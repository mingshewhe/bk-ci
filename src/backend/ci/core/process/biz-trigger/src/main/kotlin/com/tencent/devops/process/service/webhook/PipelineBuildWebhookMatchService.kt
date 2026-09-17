package com.tencent.devops.process.service.webhook

import com.tencent.devops.common.api.exception.PermissionForbiddenException
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.Watcher
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.element.trigger.WebHookTriggerElement
import com.tencent.devops.common.pipeline.utils.CascadePropertyUtils
import com.tencent.devops.common.pipeline.utils.PIPELINE_PAC_REPO_HASH_ID
import com.tencent.devops.common.service.prometheus.BkTimed
import com.tencent.devops.common.service.trace.TraceTag
import com.tencent.devops.common.service.utils.LogUtils
import com.tencent.devops.common.webhook.service.code.loader.WebhookElementParamsRegistrar
import com.tencent.devops.common.webhook.service.code.loader.WebhookStartParamsRegistrar
import com.tencent.devops.common.webhook.service.code.matcher.ScmWebhookMatcher
import com.tencent.devops.common.webhook.service.code.pojo.EventRepositoryCache
import com.tencent.devops.common.webhook.util.EventCacheUtil
import com.tencent.devops.process.api.service.ServiceBuildResource
import com.tencent.devops.process.api.service.ServiceScmWebhookResource
import com.tencent.devops.process.constant.MeasureConstant
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.engine.service.PipelineWebhookService
import com.tencent.devops.process.engine.service.code.GitWebhookUnlockDispatcher
import com.tencent.devops.process.pojo.code.WebhookCommit
import com.tencent.devops.process.pojo.trigger.PipelineTriggerDetailBuilder
import com.tencent.devops.process.pojo.trigger.PipelineTriggerEvent
import com.tencent.devops.process.pojo.trigger.PipelineTriggerFailedErrorCode
import com.tencent.devops.process.pojo.trigger.PipelineTriggerFailedMatch
import com.tencent.devops.process.pojo.trigger.PipelineTriggerFailedMatchElement
import com.tencent.devops.process.pojo.trigger.PipelineTriggerFailedMsg
import com.tencent.devops.process.pojo.trigger.PipelineTriggerReason
import com.tencent.devops.process.pojo.trigger.PipelineTriggerStatus
import com.tencent.devops.process.pojo.webhook.WebhookTriggerPipeline
import com.tencent.devops.process.service.CreateStreamTriggerSupportService
import com.tencent.devops.process.service.builds.PipelineBuildCommitService
import com.tencent.devops.process.trigger.PipelineTriggerEventService
import com.tencent.devops.process.trigger.PipelineTriggerMeasureService
import com.tencent.devops.process.utils.PipelineVarUtil
import com.tencent.devops.process.yaml.PipelineYamlService
import com.tencent.devops.repository.api.ServiceRepositoryResource
import io.micrometer.core.instrument.Tags
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Suppress("ALL")
@Service
class PipelineBuildWebhookMatchService @Autowired constructor(
    private val client: Client,
    private val pipelineWebhookService: PipelineWebhookService,
    private val pipelineRepositoryService: PipelineRepositoryService,
    private val gitWebhookUnlockDispatcher: GitWebhookUnlockDispatcher,
    private val pipelineBuildCommitService: PipelineBuildCommitService,
    private val pipelineTriggerEventService: PipelineTriggerEventService,
    private val pipelineYamlService: PipelineYamlService,
    private val pipelineTriggerMeasureService: PipelineTriggerMeasureService,
    private val creativeStreamTriggerSupportService: CreateStreamTriggerSupportService,
    private val webhookTriggerExecutor: PipelineBuildWebhookExecutor
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PipelineBuildWebhookMatchService::class.java)

        // 单条流水线触发超时上限，防止 MQ 消费线程被卡死的 worker 永久阻塞
        private const val TRIGGER_TIMEOUT_MINUTES = 3L
    }

    /**
     * 单次 Webhook 事件内允许并行触发的流水线数量上限
     *
     * 落地为 dispatchTriggerPipelines 内部的本地信号量，避免一次 Webhook
     * 因触发的流水线过多而占满整个 [PipelineBuildWebhookExecutor] 线程池，
     * 影响其他 Webhook 事件的处理
     */
    @Value("\${scm.webhook.trigger.max-concurrent-per-request:2}")
    private var maxConcurrentPerRequest: Int = 2

    fun dispatchTriggerPipelines(
        matcher: ScmWebhookMatcher,
        triggerEvent: PipelineTriggerEvent,
        triggerPipelines: List<WebhookTriggerPipeline>
    ): Boolean {
        val watcher = Watcher(
            "old WebhookDispatch|${matcher.getRepoName()}|${triggerPipelines.size}"
        )
        // 主线程只持有共享 Map，不绑定 ThreadLocal，避免 CallerRunsPolicy 场景污染主线程状态
        val sharedEventCache = ConcurrentHashMap<String, EventRepositoryCache>()
        val traceId = MDC.get(TraceTag.BIZID)
        try {
            logger.info("dispatch pipeline webhook subscriber|repo(${matcher.getRepoName()})")
            if (triggerPipelines.isEmpty()) {
                gitWebhookUnlockDispatcher.dispatchUnlockHookLockEvent(matcher)
                return false
            }

            // 代码库触发的事件ID,一个代码库会触发多条流水线,但应该只有一条触发事件
            val repoEventIdMap = ConcurrentHashMap<String, Long>()
            if (triggerPipelines.size >= 50) {
                logger.warn(
                    "Repository webhook triggered too many pipelines|" +
                        "${matcher.getRepoName()}|${triggerPipelines.size} pipelines triggered"
                )
            }
            // 本次 Webhook 事件内的并发闸门：单次 dispatch 最多 maxConcurrentPerRequest 条流水线并行触发
            // 使用 fair 模式保证 FIFO，避免某些流水线长时间饥饿
            val perRequestLimiter = Semaphore(maxConcurrentPerRequest, true)
            watcher.start("dispatch trigger pipelines")
            // 主线程在 acquire 阻塞：限制提交到线程池的并发数 = maxConcurrentPerRequest，避免单个事件吃满线程池
            // 等待者只可能是本次 dispatch 内已提交的同伴，无超时是为了保证所有流水线都能被触发
            val futures = triggerPipelines.map { subscriber ->
                perRequestLimiter.acquire()
                webhookTriggerExecutor.submit {
                    MDC.put(TraceTag.BIZID, traceId)
                    EventCacheUtil.bindSharedEventCache(sharedEventCache)
                    try {
                        triggerSinglePipeline(
                            subscriber = subscriber,
                            matcher = matcher,
                            triggerEvent = triggerEvent,
                            repoEventIdMap = repoEventIdMap
                        )
                    } finally {
                        EventCacheUtil.remove()
                        perRequestLimiter.release()
                    }
                }
            }
            // 单条触发加超时兜底，防止 worker 被下游卡死时 MQ 消费线程永久阻塞
            futures.forEach { future ->
                try {
                    future.get(TRIGGER_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                } catch (e: TimeoutException) {
                    logger.warn(
                        "webhook trigger timeout|repo(${matcher.getRepoName()})|" +
                            "timeout=${TRIGGER_TIMEOUT_MINUTES}min",
                        e
                    )
                    future.cancel(true)
                }
            }
            watcher.stop()
            /* #3131,当对mr的commit check有强依赖，但是蓝盾与git的commit check交互存在一定的时延，可以增加双重锁。
                git发起mr时锁住mr,称为webhook锁，由蓝盾主动发起解锁，解锁有三种情况：
                1. 仓库没有配置蓝盾的流水线，需要解锁
                2. 仓库配置了蓝盾流水线，但是流水线都不需要锁住mr，需要解锁
                3. 仓库配置了蓝盾流水线并且需要锁住mr，需要等commit check发送完成，再解锁
                 @see com.tencent.devops.plugin.service.git.CodeWebhookService.addGitCommitCheck
             */
            gitWebhookUnlockDispatcher.dispatchUnlockHookLockEvent(matcher)
            return true
        } finally {
            LogUtils.printCostTimeWE(watcher)
            if (logger.isDebugEnabled) {
                logger.debug(
                    "webhook event repository cache: ${JsonUtil.toJson(sharedEventCache, false)}"
                )
            }
        }
    }

    private fun triggerSinglePipeline(
        subscriber: WebhookTriggerPipeline,
        matcher: ScmWebhookMatcher,
        triggerEvent: PipelineTriggerEvent,
        repoEventIdMap: ConcurrentHashMap<String, Long>
    ) {
        val pipelineWatcher = Watcher(
            "old WebhookTrigger|${subscriber.projectId}|${subscriber.pipelineId}"
        )
        var status = PipelineTriggerReason.TRIGGER_SUCCESS
        val projectId = subscriber.projectId
        val pipelineId = subscriber.pipelineId
        try {
            try {
                logger.info("pipelineId is $pipelineId")
                val builder = PipelineTriggerDetailBuilder()
                    .projectId(projectId)
                    .pipelineId(pipelineId)

                pipelineWatcher.start("trigger pipeline build")
                val triggerResult = webhookTriggerPipelineBuild(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    matcher = matcher,
                    builder = builder
                )
                pipelineWatcher.stop()
                if (!triggerResult) {
                    status = PipelineTriggerReason.TRIGGER_NOT_MATCH
                }
                pipelineWatcher.start("save trigger event")
                saveTriggerEvent(
                    projectId = projectId,
                    builder = builder,
                    triggerEvent = triggerEvent,
                    repoEventIdMap = repoEventIdMap
                )
                pipelineWatcher.stop()
            } catch (e: Throwable) {
                status = PipelineTriggerReason.TRIGGER_FAILED
                logger.warn("[$pipelineId]|webhookTriggerPipelineBuild fail: $e", e)
            }
            val totalCostMills = System.currentTimeMillis() - triggerEvent.createTime.timestampmilli()
            if (totalCostMills >= 60 * 1000) {
                logger.warn(
                    "old Webhook trigger execution time exceeds threshold|" +
                        "${matcher.getRepoName()}|$projectId|$pipelineId|$totalCostMills"
                )
            }
            pipelineTriggerMeasureService.recordTaskExecutionTime(
                name = MeasureConstant.PIPELINE_SCM_WEBHOOK_EXECUTE_TIME,
                tags = Tags.of(MeasureConstant.TAG_SCM_WEBHOOK_TRIGGER_STATUS, status.name)
                    .and(MeasureConstant.TAG_SCM_WEBHOOK_TRIGGER_YAML, "false")
                    .and(MeasureConstant.TAG_SCM_WEBHOOK_TRIGGER_OLD, "true")
                    .and(MeasureConstant.TAG_SCM_WEBHOOK_SCM_CODE, triggerEvent.triggerType)
                    .toList(),
                timeConsumingMills = totalCostMills
            )
        } finally {
            LogUtils.printCostTimeWE(pipelineWatcher)
        }
    }

    private fun saveTriggerEvent(
        projectId: String,
        builder: PipelineTriggerDetailBuilder,
        triggerEvent: PipelineTriggerEvent,
        repoEventIdMap: ConcurrentHashMap<String, Long>
    ) {
        if (!builder.getEventSource().isNullOrBlank()) {
            val eventSource = builder.getEventSource()!!
            val eventType = triggerEvent.eventType
            val eventId = repoEventIdMap.computeIfAbsent(eventSource) {
                pipelineTriggerEventService.getEventId(
                    projectId = projectId,
                    requestId = triggerEvent.requestId,
                    eventSource = eventSource
                )
            }
            builder.eventId(eventId)
            builder.detailId(pipelineTriggerEventService.getDetailId())
            val triggerDetail = builder.build()
            val eventToSave = triggerEvent.copy(
                projectId = projectId,
                eventSource = eventSource,
                eventId = eventId
            )
            pipelineTriggerEventService.saveEvent(
                triggerEvent = eventToSave,
                triggerDetail = triggerDetail
            )
            // 判断刷新的eventType和repository_hash_id字段的准确性,为后期优化做准备
            pipelineWebhookService.get(
                projectId = projectId,
                pipelineId = triggerDetail.pipelineId!!,
                repositoryHashId = eventSource,
                eventType = eventType
            ) ?: run {
                logger.warn(
                    "Failed to match pipeline webhook|$projectId|${triggerDetail.pipelineId}|$eventSource|$eventType"
                )
            }
        }
    }

    @BkTimed
    private fun webhookTriggerPipelineBuild(
        projectId: String,
        pipelineId: String,
        matcher: ScmWebhookMatcher,
        builder: PipelineTriggerDetailBuilder
    ): Boolean {
        val pipelineInfo = pipelineRepositoryService.getPipelineInfo(projectId, pipelineId)
            ?: return false

        val model = pipelineRepositoryService.getPipelineResourceVersion(projectId, pipelineId)?.model
        if (model == null) {
            logger.warn("[$pipelineId]| Fail to get the model")
            return false
        }
        // 触发事件保存流水线名称
        builder.pipelineName(pipelineInfo.pipelineName)
        // 获取授权人
        val userId = pipelineRepositoryService.getPipelineOauthUser(projectId, pipelineId)
            ?: pipelineInfo.lastModifyUser
        val container = model.getTriggerContainer()
        // 解析变量
        val variables = getDefaultParam(container.params)
        // 补充yaml流水线代码库信息
        pipelineYamlService.getPipelineYamlInfo(projectId = projectId, pipelineId = pipelineId)?.let {
            variables[PIPELINE_PAC_REPO_HASH_ID] = it.repoHashId
        }

        val failedMatchElements = mutableListOf<PipelineTriggerFailedMatchElement>()
        // 寻找代码触发原子
        container.elements.forEach elements@{ element ->
            if (!element.elementEnabled() || element !is WebHookTriggerElement) {
                logger.info("Trigger element is disable, can not start pipeline")
                return@elements
            }
            val webHookParams = WebhookElementParamsRegistrar.getService(element)
                .getWebhookElementParams(element, PipelineVarUtil.fillVariableMap(variables)) ?: return@elements
            val repositoryConfig = webHookParams.repositoryConfig
            if (repositoryConfig.getRepositoryId().isBlank()) {
                logger.info("repositoryHashId is blank for code trigger pipeline $pipelineId ")
                return@elements
            }

            logger.info("$pipelineId|${element.name}|Get the code trigger pipeline")
            // #2958 如果仓库找不到,会抛出404异常,就不会继续往下遍历
            val repo = try {
                client.get(ServiceRepositoryResource::class).get(
                    projectId,
                    repositoryConfig.getURLEncodeRepositoryId(),
                    repositoryConfig.repositoryType
                ).data
            } catch (e: Exception) {
                null
            }
            if (repo == null) {
                logger.warn("$pipelineId|repo[$repositoryConfig] does not exist")
                return@elements
            }

            val matchResult = matcher.isMatch(projectId, pipelineId, repo, webHookParams)
            if (matchResult.isMatch) {
                try {
                    // 创作流相关参数，触发成功时才去校验创作流运行环境，避免重复查询创作流环境信息
                    val externalStartParams = creativeStreamTriggerSupportService.externalWebhookStartParams(
                        pipelineInfo = pipelineInfo,
                        userId = userId
                    )
                    val webhookCommit = WebhookCommit(
                        userId = userId,
                        pipelineId = pipelineId,
                        version = null,
                        params = WebhookStartParamsRegistrar.getService(element).getStartParams(
                            projectId = projectId,
                            element = element,
                            repo = repo,
                            matcher = matcher,
                            variables = variables,
                            params = webHookParams,
                            matchResult = matchResult
                        ).plus(externalStartParams),
                        repositoryConfig = repositoryConfig,
                        repoName = matcher.getRepoName(),
                        commitId = matcher.getRevision(),
                        block = webHookParams.block,
                        eventType = matcher.getEventType(),
                        codeType = matcher.getCodeType()
                    )
                    val buildId =
                        client.getGateway(ServiceScmWebhookResource::class).webhookCommit(projectId, webhookCommit).data
                    logger.info("$pipelineId|$buildId|webhook trigger|(${element.name}|repo(${matcher.getRepoName()})")
                    if (!buildId.isNullOrEmpty()) {
                        pipelineBuildCommitService.create(
                            projectId = projectId,
                            pipelineId = pipelineId,
                            buildId = buildId,
                            matcher = matcher,
                            repo = repo,
                            channelCode = pipelineInfo.channelCode
                        )
                        val buildDetail = client.getGateway(ServiceBuildResource::class).getBuildDetail(
                            userId = userId,
                            buildId = buildId,
                            pipelineId = pipelineId,
                            projectId = projectId,
                            channelCode = pipelineInfo.channelCode
                        ).data
                        builder.buildId(buildId)
                            .status(PipelineTriggerStatus.SUCCEED.name)
                            .eventSource(eventSource = repo.repoHashId!!)
                            .reason(PipelineTriggerReason.TRIGGER_SUCCESS.name)
                            .buildNum(buildDetail?.buildNum.toString())
                    }
                } catch (permissionException: PermissionForbiddenException) {
                    logger.warn("check permission failed", permissionException)
                    builder.eventSource(repo.repoHashId!!)
                        .status(PipelineTriggerStatus.FAILED.name)
                        .reason(PipelineTriggerReason.TRIGGER_FAILED.name)
                        .reasonDetail(
                            PipelineTriggerFailedErrorCode(
                                errorCode = ProcessMessageCode.BK_AUTHOR_NOT_PIPELINE_EXECUTE_PERMISSION,
                                params = listOf(userId)
                            )
                        )
                    // 当前流水线没有权限触发
                    return false
                } catch (ignore: Exception) {
                    logger.warn("$pipelineId|webhook trigger|(${element.name})|repo(${matcher.getRepoName()})", ignore)
                    builder.eventSource(eventSource = repo.repoHashId!!)
                    builder.status(PipelineTriggerStatus.FAILED.name)
                        .reason(PipelineTriggerReason.TRIGGER_FAILED.name)
                        .reasonDetail(PipelineTriggerFailedMsg(ignore.message ?: ""))
                }
                return true
            } else {
                logger.info(
                    "$pipelineId|webhook trigger match unsuccess|(${element.name})|repo(${matcher.getRepoName()})"
                )
                if (!matchResult.reason.isNullOrBlank()) {
                    builder.eventSource(eventSource = repo.repoHashId!!)
                    failedMatchElements.add(
                        PipelineTriggerFailedMatchElement(
                            elementId = element.id,
                            elementName = element.name,
                            elementAtomCode = element.getAtomCode(),
                            reasonMsg = matchResult.reason!!
                        )
                    )
                }
            }
        }

        // 历史原因,webhook表没有记录eventType,所以查找出来的订阅者可能因为事件类型不匹配,事件不需要记录
        if (!builder.getEventSource().isNullOrBlank()) {
            builder.status(PipelineTriggerStatus.FAILED.name)
                .reason(PipelineTriggerReason.TRIGGER_NOT_MATCH.name)
                .reasonDetail(PipelineTriggerFailedMatch(failedMatchElements))
        }
        return false
    }

    private fun getDefaultParam(params: List<BuildFormProperty>): MutableMap<String, String> {
        val variables = mutableMapOf<String, String>()
        params.forEach { param ->
            variables[param.id] = if (CascadePropertyUtils.supportCascadeParam(param.type) &&
                param.defaultValue is Map<*, *>
            ) {
                JsonUtil.toJson(param.defaultValue, false)
            } else {
                param.defaultValue.toString()
            }
        }
        return variables
    }
}
