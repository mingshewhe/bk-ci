package com.tencent.devops.process.service.webhook

import com.tencent.devops.common.api.context.ChannelContext
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.event.dispatcher.SampleEventDispatcher
import com.tencent.devops.common.event.pojo.measure.ProjectUserDailyEvent
import com.tencent.devops.common.event.pojo.measure.ProjectUserOperateMetricsData
import com.tencent.devops.common.event.pojo.measure.ProjectUserOperateMetricsEvent
import com.tencent.devops.common.event.pojo.measure.UserOperateCounterData
import com.tencent.devops.common.log.pojo.message.LogMessage
import com.tencent.devops.common.log.utils.BuildLogPrinter
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.BuildParameters
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.common.webhook.pojo.code.PIPELINE_START_WEBHOOK_USER_ID
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.engine.compatibility.BuildParametersCompatibilityTransformer
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.engine.service.PipelineWebHookQueueService
import com.tencent.devops.process.engine.service.WebhookBuildParameterService
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.pojo.BuildId
import com.tencent.devops.process.pojo.code.WebhookCommit
import com.tencent.devops.process.service.pipeline.PipelineBuildService
import com.tencent.devops.process.utils.PIPELINE_START_TASK_ID
import com.tencent.devops.process.utils.PipelineVarUtil
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PipelineBuildWebhookService @Autowired constructor(
    private val buildParamCompatibilityTransformer: BuildParametersCompatibilityTransformer,
    private val pipelineRepositoryService: PipelineRepositoryService,
    private val pipelineBuildService: PipelineBuildService,
    private val pipelineWebHookQueueService: PipelineWebHookQueueService,
    private val buildLogPrinter: BuildLogPrinter,
    private val webhookBuildParameterService: WebhookBuildParameterService,
    private val measureEventDispatcher: SampleEventDispatcher,
    private val pipelinePermissionService: PipelinePermissionService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PipelineBuildWebhookService::class.java)
        private const val WEBHOOK_COMMIT_TRIGGER = "webhook_commit_trigger"
    }

    /**
     * webhookCommitTriggerPipelineBuild 方法是webhook事件触发最后执行方法
     * @link webhookTriggerPipelineBuild 方法接收webhook事件后通过调用网关接口进行分发，从而区分正式和灰度服务
     */
    fun webhookCommitTriggerPipelineBuild(projectId: String, webhookCommit: WebhookCommit): BuildId? {
        val userId = webhookCommit.userId
        val pipelineId = webhookCommit.pipelineId
        val startParams = webhookCommit.params
        val repoName = webhookCommit.repoName
        val pipelineInfo = pipelineRepositoryService.getPipelineInfo(projectId, pipelineId)
            ?: throw ErrorCodeException(
                statusCode = Response.Status.NOT_FOUND.statusCode,
                errorCode = ProcessMessageCode.ERROR_PIPELINE_NOT_EXISTS,
                params = arrayOf(pipelineId)
            )
        if (pipelineInfo.locked == true) {
            throw ErrorCodeException(errorCode = ProcessMessageCode.ERROR_PIPELINE_LOCK)
        }
        if (pipelineInfo.latestVersionStatus == VersionStatus.COMMITTING) throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_NO_RELEASE_PIPELINE_VERSION
        )
        val version = webhookCommit.version ?: pipelineInfo.version
        ChannelContext.withChannel(pipelineInfo.channelCode.name) {
            checkPermission(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId
            )
        }

        val resource = pipelineRepositoryService.getPipelineResourceVersion(
            projectId = projectId,
            pipelineId = pipelineId,
            version = version
        )
        if (resource == null) {
            logger.warn("[$pipelineId]| Fail to get the model")
            return null
        }

        val paramMap = buildParamCompatibilityTransformer.parseTriggerParam(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            paramProperties = resource.model.getTriggerContainer().params,
            paramValues = startParams.mapValues { it.value.toString() }
        )
        val pipelineParamMap = mutableMapOf<String, BuildParameters>()
        pipelineParamMap.putAll(paramMap)
        startParams.forEach {
            if (paramMap.containsKey(it.key)) {
                return@forEach
            }
            val newVarName = PipelineVarUtil.oldVarToNewVar(it.key)
            if (newVarName == null) {
                pipelineParamMap[it.key] = BuildParameters(key = it.key, value = it.value)
            } else if (!pipelineParamMap.contains(newVarName)) {
                pipelineParamMap[newVarName] = BuildParameters(key = newVarName, value = it.value)
            }
        }

        val startEpoch = System.currentTimeMillis()
        val channelCode = pipelineInfo.channelCode
        try {
            val buildId = pipelineBuildService.startPipeline(
                userId = userId,
                pipeline = pipelineInfo,
                startType = StartType.WEB_HOOK,
                pipelineParamMap = HashMap(pipelineParamMap),
                channelCode = channelCode,
                isMobile = false,
                resource = resource,
                signPipelineVersion = version,
                frequencyLimit = false
            )
            pipelineWebHookQueueService.onWebHookTrigger(
                projectId = projectId,
                pipelineId = pipelineId,
                buildId = buildId.id,
                variables = webhookCommit.params,
                channelCode = channelCode
            )
            buildLogPrinter.addLines(
                buildId = buildId.id,
                logMessages = pipelineParamMap.map {
                    LogMessage(
                        message = "${it.key}=${it.value.value}",
                        timestamp = System.currentTimeMillis(),
                        tag = startParams[PIPELINE_START_TASK_ID]?.toString() ?: ""
                    )
                }
            )
            if (buildId.id.isNotBlank()) {
                webhookBuildParameterService.save(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    buildId = buildId.id,
                    buildParameters = pipelineParamMap.values.toList()
                )
                if (startParams[PIPELINE_START_WEBHOOK_USER_ID] != null) {
                    uploadProjectUserMetrics(
                        userId = startParams[PIPELINE_START_WEBHOOK_USER_ID]!!.toString(),
                        projectId = projectId,
                        theDate = LocalDate.now()
                    )
                }
            }
            return buildId
        } catch (ignore: Exception) {
            logger.warn("[$pipelineId]| webhook trigger fail to start repo($repoName): ${ignore.message}", ignore)
            throw ignore
        } finally {
            logger.info("$pipelineId|WEBHOOK_TRIGGER|repo=$repoName|time=${System.currentTimeMillis() - startEpoch}")
        }
    }

    private fun checkPermission(userId: String, projectId: String, pipelineId: String) {
        pipelinePermissionService.validPipelinePermission(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            permission = AuthPermission.EXECUTE,
            message = I18nUtil.getCodeLanMessage(
                messageCode = ProcessMessageCode.USER_NO_PIPELINE_PERMISSION_UNDER_PROJECT,
                params = arrayOf(
                    userId,
                    projectId,
                    AuthPermission.EXECUTE.getI18n(I18nUtil.getLanguage(userId))
                )
            )
        )
    }

    private fun uploadProjectUserMetrics(
        userId: String,
        projectId: String,
        theDate: LocalDate
    ) {
        try {
            val projectUserOperateMetricsKey = ProjectUserOperateMetricsData(
                projectId = projectId,
                userId = userId,
                operate = WEBHOOK_COMMIT_TRIGGER,
                theDate = theDate
            ).getProjectUserOperateMetricsKey()
            measureEventDispatcher.dispatch(
                ProjectUserDailyEvent(
                    projectId = projectId,
                    userId = userId,
                    theDate = theDate
                ),
                ProjectUserOperateMetricsEvent(
                    userOperateCounterData = UserOperateCounterData().apply {
                        this.increment(projectUserOperateMetricsKey)
                    }
                )
            )
        } catch (ignored: Exception) {
            logger.error("save auth user metrics", ignored)
        }
    }
}
