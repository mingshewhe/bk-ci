package com.tencent.devops.process.engine.utils

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.ResolvedTemplate
import com.tencent.devops.common.pipeline.TemplateDescriptor
import com.tencent.devops.common.pipeline.container.Container
import com.tencent.devops.common.pipeline.container.JobTemplateContainer
import com.tencent.devops.common.pipeline.container.Stage
import com.tencent.devops.common.pipeline.container.TriggerContainer
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.TemplateRefType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.TemplateInstanceField
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.StepTemplateElement
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.common.pipeline.pojo.time.BuildRecordTimeCost
import com.tencent.devops.common.pipeline.template.JobTemplateModel
import com.tencent.devops.common.pipeline.template.PipelineTemplateType
import com.tencent.devops.common.pipeline.template.StageTemplateModel
import com.tencent.devops.common.pipeline.template.StepTemplateModel
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.dao.template.PipelineTemplateInfoDao
import com.tencent.devops.process.dao.template.PipelineTemplateResourceDao
import com.tencent.devops.process.dao.template.PipelineTemplateSettingDao
import com.tencent.devops.process.dao.yaml.PipelineYamlInfoDao
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.service.pipeline.PipelineYamlVersionResolver
import org.apache.commons.codec.digest.DigestUtils
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 流水线模型解析工具
 *
 * 1. 引用 model 展开成嵌套 model,引用节点写上 resolvedTemplate
 * 2. 嵌套 model 平铺成引擎可用的 model
 * 3. 根据解析结果生成动态版本号
 */
@Suppress("TooManyFunctions")
object PipelineModelParseUtil {

    private val logger = LoggerFactory.getLogger(PipelineModelParseUtil::class.java)

    private const val MAX_TEMPLATE_DEPTH = 10
    private const val CONTAINER_HASH_ID_PREFIX = "c-"
    private const val ELEMENT_ID_PREFIX = "e-"
    private const val TRIGGER_CONTAINER_ID = "0"

    /**
     * 模板版本内容不会变化,可以跨构建缓存;取出后必须深拷贝再使用
     */
    private val templateResourceCache: Cache<String, PipelineTemplateResource> = Caffeine.newBuilder()
        .maximumSize(2000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build()

    private val dslContext: DSLContext by lazy { SpringContextUtil.getBean(DSLContext::class.java) }
    private val templateInfoDao by lazy { SpringContextUtil.getBean(PipelineTemplateInfoDao::class.java) }
    private val templateResourceDao by lazy { SpringContextUtil.getBean(PipelineTemplateResourceDao::class.java) }
    private val templateSettingDao by lazy { SpringContextUtil.getBean(PipelineTemplateSettingDao::class.java) }
    private val yamlInfoDao by lazy { SpringContextUtil.getBean(PipelineYamlInfoDao::class.java) }
    private val yamlVersionResolver by lazy { SpringContextUtil.getBean(PipelineYamlVersionResolver::class.java) }

    /**
     * 解析 model:引用 model 展开成嵌套 model,引用节点写上 resolvedTemplate
     *
     * 没有任何模板引用时直接返回传入的 model;否则返回新对象,不修改传入的 model
     */
    fun parseModel(
        projectId: String,
        pipelineId: String,
        model: Model,
        branchName: String? = null
    ): Model {
        val needExpandPipeline = needExpandPipelineTemplate(model)
        if (!needExpandPipeline && model.resolvedTemplate == null && !hasTemplateRef(model)) {
            return model
        }
        val context = ParseContext(projectId = projectId, pipelineId = pipelineId, branchName = branchName)
        val parsedModel = if (needExpandPipeline) {
            doExpandPipelineTemplate(context = context, model = model)
        } else {
            deepCopy(model)
        }
        val stages = parsedModel.stages
        stages.forEachIndexed { index, stage ->
            if (index == 0) {
                parseTriggerStage(context = context, stage = stage)
            } else {
                parseStage(context = context, stage = stage, seed = null, chain = emptyList())
            }
        }
        checkRepeatedJobId(flatten(parsedModel))
        return parsedModel
    }

    /**
     * 只展开流水线模板引用,局部模板节点保持为引用,用于读取流水线版本时还原完整编排
     */
    fun expandPipelineTemplate(
        projectId: String,
        pipelineId: String,
        model: Model,
        branchName: String? = null
    ): Model {
        if (!needExpandPipelineTemplate(model)) {
            return model
        }
        return doExpandPipelineTemplate(
            context = ParseContext(projectId = projectId, pipelineId = pipelineId, branchName = branchName),
            model = model
        )
    }

    /**
     * 解析 setting:按 setting 自己的 TEMPLATE 字段解析模板版本并合并设置,不依赖 model
     */
    fun parseSetting(
        projectId: String,
        setting: PipelineSetting,
        branchName: String? = null
    ): PipelineSetting {
        val templateRef = setting.template ?: return setting
        val context = ParseContext(projectId = projectId, pipelineId = setting.pipelineId, branchName = branchName)
        val (templateResource, _) = resolveDescriptor(
            context = context,
            descriptor = templateRef.toDescriptor(),
            expectType = PipelineTemplateType.PIPELINE
        )
        val templateSetting = templateSettingDao.get(
            dslContext = dslContext,
            projectId = projectId,
            templateId = templateResource.templateId,
            settingVersion = templateResource.settingVersion
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS,
            params = arrayOf(templateResource.templateId)
        )
        return TemplateInstanceUtil.instanceSetting(
            setting = setting,
            templateSetting = templateSetting,
            overrideTemplateField = TemplateInstanceField(settingGroups = templateRef.settingGroups ?: emptyList())
        )
    }

    /**
     * 嵌套 model 平铺为引擎可用的 model,引用节点替换成它展开后的子节点
     *
     * 没有引用节点的 stage/container 保持原对象,便于和嵌套 model 共享状态
     */
    fun flatten(model: Model): Model {
        if (!hasTemplateRef(model)) {
            return model
        }
        return model.copy(stages = model.stages.flatMap { flattenStage(it) })
    }

    /**
     * 把合并了构建记录的平铺 model 按嵌套 model 的结构放回去,引用节点的状态和耗时由子节点汇总
     *
     * @param nestedModel 动态版本中保存的嵌套 model,引用节点会被修改
     * @param flatModel 由 [flatten] 平铺后再合并构建记录得到的 model
     */
    fun restoreNested(nestedModel: Model, flatModel: Model): Model {
        if (!hasTemplateRef(nestedModel)) {
            return flatModel
        }
        val flatStages = flatModel.stages.associateBy { it.id }
        val flatContainers = flatModel.stages.flatMap { it.containers }.associateBy { it.id }
        return flatModel.copy(
            stages = nestedModel.stages.map { restoreStage(it, flatStages, flatContainers) }
        )
    }

    /**
     * 生成动态版本号:深度优先收集解析后 model 上的所有 resolvedTemplate,拼上 pipelineId + version 算 md5
     *
     * model 里没有任何 resolvedTemplate 时返回 null
     */
    fun generateDynamicVersion(pipelineId: String, version: Int, model: Model): String? {
        val resolvedTemplates = mutableListOf<ResolvedTemplate>()
        model.resolvedTemplate?.let { resolvedTemplates.add(it) }
        model.stages.forEach { collectResolvedTemplates(it, resolvedTemplates) }
        if (resolvedTemplates.isEmpty()) {
            return null
        }
        return DigestUtils.md5Hex("$pipelineId:$version:${JsonUtil.toJson(resolvedTemplates, false)}")
    }

    /**
     * 清理解析产物,流水线版本中只保存引用节点本身
     */
    fun clearResolved(model: Model) {
        model.resolvedTemplate = null
        model.stages.forEach { stage ->
            stage.resolvedTemplate = null
            stage.templateStages = null
            stage.containers.forEach { container ->
                if (container is JobTemplateContainer) {
                    container.resolvedTemplate = null
                    container.templateContainers = null
                }
                container.elements.forEach { element ->
                    if (element is StepTemplateElement) {
                        element.resolvedTemplate = null
                        element.templateElements = null
                    }
                }
            }
        }
    }

    /**
     * 保存流水线时解析模板引用,PATH 方式引用会实时查询代码库确定模板版本
     *
     * @param repoHashId 仓库hashId,当通过模版路径引用且流水线还未创建时,必须传入
     * @param branchName 触发分支名称,当webhook触发时才有值
     */
    @Suppress("CyclomaticComplexMethod")
    fun parseTemplateDescriptor(
        projectId: String,
        descriptor: TemplateDescriptor,
        pipelineId: String? = null,
        repoHashId: String? = null,
        branchName: String? = null
    ): PipelineTemplateResource {
        with(descriptor) {
            return when (templateRefType) {
                TemplateRefType.ID -> {
                    if (templateVersionName.isNullOrEmpty()) {
                        throw ErrorCodeException(
                            errorCode = ProcessMessageCode.ERROR_TEMPLATE_VERSION_NAME_NOT_EMPTY
                        )
                    }
                    logger.info(
                        "parse template descriptor by version name|$projectId|$templateId|$templateVersionName"
                    )
                    templateInfoDao.get(
                        dslContext = dslContext,
                        projectId = projectId,
                        templateId = templateId!!
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS,
                        params = arrayOf(templateId!!)
                    )
                    templateResourceDao.getLatestRecord(
                        dslContext = dslContext,
                        projectId = projectId,
                        templateId = templateId!!,
                        versionName = templateVersionName!!
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessMessageCode.ERROR_TEMPLATE_VERSION_BY_ID_NOT_FOUND,
                        params = arrayOf(templateId!!, templateVersionName!!)
                    )
                }

                TemplateRefType.PATH -> {
                    val finalRepoHashId = when {
                        !repoHashId.isNullOrEmpty() -> repoHashId
                        !pipelineId.isNullOrEmpty() -> {
                            yamlInfoDao.get(
                                dslContext = dslContext,
                                projectId = projectId,
                                pipelineId = pipelineId
                            )?.repoHashId
                        }

                        else -> null
                    } ?: throw ErrorCodeException(
                        errorCode = CommonMessageCode.PARAMETER_IS_NULL,
                        params = arrayOf("repoHashId")
                    )
                    logger.info(
                        "parse template descriptor by path|$projectId|$finalRepoHashId|$templatePath|$templateRef"
                    )
                    val pipelineYamlInfo = yamlInfoDao.get(
                        dslContext = dslContext,
                        projectId = projectId,
                        repoHashId = finalRepoHashId,
                        filePath = templatePath!!
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessMessageCode.ERROR_YAML_FOR_TEMPLATE_NOT_FOUND,
                        params = arrayOf(templatePath!!)
                    )
                    // 指定分支则使用指定分支,否则 webhook 触发时使用触发分支,其他情况使用默认分支
                    val ref = templateRef?.takeIf { it.isNotEmpty() } ?: branchName?.takeIf { it.isNotEmpty() }
                    val pipelineYamlVersion = yamlVersionResolver.resolveTemplateRefVersion(
                        projectId = projectId,
                        repoHashId = finalRepoHashId,
                        filePath = templatePath!!,
                        ref = ref
                    )
                    logger.info(
                        "parse template descriptor result by path|$projectId|$finalRepoHashId|" +
                            "${pipelineYamlInfo.pipelineId}|${pipelineYamlVersion.version}"
                    )
                    templateResourceDao.getLatestRecord(
                        dslContext = dslContext,
                        projectId = projectId,
                        templateId = pipelineYamlInfo.pipelineId,
                        version = pipelineYamlVersion.version.toLong()
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessMessageCode.ERROR_TEMPLATE_VERSION_BY_PATH_NOT_FOUND
                    )
                }

                else -> throw ErrorCodeException(errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_TYPE)
            }
        }
    }

    /**
     * 是否含有局部模板(stage/job/step)引用节点
     */
    fun hasTemplateRef(model: Model): Boolean {
        return model.stages.any { stage ->
            stage.template != null || stage.containers.any { container ->
                container is JobTemplateContainer || container.elements.any { it is StepTemplateElement }
            }
        }
    }

    /**
     * 存储的 model 只有流水线模板引用、没有编排时才需要展开流水线模板
     */
    fun needExpandPipelineTemplate(model: Model): Boolean {
        return model.template != null && model.stages.isEmpty()
    }

    private fun doExpandPipelineTemplate(context: ParseContext, model: Model): Model {
        val (templateResource, resolvedTemplate) = resolveDescriptor(
            context = context,
            descriptor = model.template!!,
            expectType = PipelineTemplateType.PIPELINE
        )
        val instanceModel = TemplateInstanceUtil.instanceModel(
            model = deepCopy(model),
            templateResource = templateResource.copy(model = deepCopy(templateResource.model))
        )
        instanceModel.resolvedTemplate = resolvedTemplate
        return instanceModel
    }

    private fun parseTriggerStage(context: ParseContext, stage: Stage) {
        stage.id = VMUtils.genStageId(context.stageSeq.incrementAndGet())
        stage.containers.forEach { container ->
            if (container is TriggerContainer) {
                container.id = TRIGGER_CONTAINER_ID
                container.containerId = TRIGGER_CONTAINER_ID
            }
        }
    }

    /**
     * @param seed 引用节点的 ID 种子,不在模板展开范围内时为空
     * @param chain 当前引用链上的模板,用于环检测和深度限制
     */
    private fun parseStage(
        context: ParseContext,
        stage: Stage,
        seed: String?,
        chain: List<String>
    ) {
        stage.id = VMUtils.genStageId(context.stageSeq.incrementAndGet())
        val descriptor = stage.template
        if (descriptor == null) {
            stage.containers.forEach { container ->
                parseContainer(context = context, container = container, seed = seed, chain = chain)
            }
            return
        }
        val templateKey = descriptorKey(descriptor)
        val nextChain = checkChain(chain = chain, templateKey = templateKey)
        val (templateResource, resolvedTemplate) = resolveDescriptor(
            context = context,
            descriptor = descriptor,
            expectType = PipelineTemplateType.STAGE
        )
        val templateModel = templateResource.model as? StageTemplateModel ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TEMPLATE_TYPE_MODEL_TYPE_NOT_MATCH
        )
        val templateStages = deepCopy(templateModel).stages
        if (templateStages.isEmpty()) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_EMPTY,
                params = arrayOf(templateKey)
            )
        }
        // stage 没有持久化的唯一 ID,用用户自定义 ID 或同一层级下的引用序号作为种子
        val stageKey = stage.stageIdForUser ?: "$templateKey#${context.nextOccurrence("${seed ?: ""}|$templateKey")}"
        val stageSeed = chainSeed(seed = seed, id = "stage:$stageKey")
        templateStages.forEach { subStage ->
            parseStage(context = context, stage = subStage, seed = stageSeed, chain = nextChain)
        }
        stage.resolvedTemplate = resolvedTemplate
        stage.templateStages = templateStages
    }

    private fun parseContainer(
        context: ParseContext,
        container: Container,
        seed: String?,
        chain: List<String>
    ) {
        if (container is TriggerContainer) {
            return
        }
        container.id = context.containerSeq.incrementAndGet().toString()
        container.containerId = container.id
        if (seed != null) {
            container.containerHashId = CONTAINER_HASH_ID_PREFIX +
                chainSeed(seed = seed, id = container.containerHashId ?: container.jobId ?: "")
        }
        if (container !is JobTemplateContainer) {
            parseElements(context = context, elements = container.elements, seed = seed, chain = chain)
            return
        }
        val descriptor = container.template ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_TYPE
        )
        val templateKey = descriptorKey(descriptor)
        val nextChain = checkChain(chain = chain, templateKey = templateKey)
        val (templateResource, resolvedTemplate) = resolveDescriptor(
            context = context,
            descriptor = descriptor,
            expectType = PipelineTemplateType.JOB
        )
        val templateModel = templateResource.model as? JobTemplateModel ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TEMPLATE_TYPE_MODEL_TYPE_NOT_MATCH
        )
        val templateContainers = deepCopy(templateModel).containers
        if (templateContainers.isEmpty()) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_EMPTY,
                params = arrayOf(templateKey)
            )
        }
        val containerSeed = container.containerHashId ?: chainSeed(seed = seed, id = "job:${container.id}")
        templateContainers.forEach { subContainer ->
            parseContainer(context = context, container = subContainer, seed = containerSeed, chain = nextChain)
        }
        container.resolvedTemplate = resolvedTemplate
        container.templateContainers = templateContainers
    }

    private fun parseElements(
        context: ParseContext,
        elements: List<Element>,
        seed: String?,
        chain: List<String>
    ) {
        elements.forEachIndexed { index, element ->
            if (seed != null) {
                element.id = ELEMENT_ID_PREFIX + chainSeed(seed = seed, id = element.id ?: "$index")
            }
            if (element !is StepTemplateElement) {
                return@forEachIndexed
            }
            val descriptor = element.template ?: throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_TYPE
            )
            val templateKey = descriptorKey(descriptor)
            val nextChain = checkChain(chain = chain, templateKey = templateKey)
            val (templateResource, resolvedTemplate) = resolveDescriptor(
                context = context,
                descriptor = descriptor,
                expectType = PipelineTemplateType.STEP
            )
            val templateModel = templateResource.model as? StepTemplateModel ?: throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_TYPE_MODEL_TYPE_NOT_MATCH
            )
            val templateElements = deepCopy(templateModel).container.elements
            if (templateElements.isEmpty()) {
                throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_EMPTY,
                    params = arrayOf(templateKey)
                )
            }
            val elementSeed = element.id ?: chainSeed(seed = seed, id = "step:$index")
            parseElements(context = context, elements = templateElements, seed = elementSeed, chain = nextChain)
            element.resolvedTemplate = resolvedTemplate
            element.templateElements = templateElements
        }
    }

    private fun checkChain(chain: List<String>, templateKey: String): List<String> {
        if (chain.contains(templateKey)) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_CYCLE,
                params = arrayOf(templateKey)
            )
        }
        if (chain.size >= MAX_TEMPLATE_DEPTH) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_DEPTH_EXCEED,
                params = arrayOf(MAX_TEMPLATE_DEPTH.toString())
            )
        }
        return chain + templateKey
    }

    /**
     * 身份 ID 逐层串联:同一引用节点下展开出来的 ID 在不同动态版本之间保持稳定
     */
    private fun chainSeed(seed: String?, id: String): String {
        return DigestUtils.md5Hex("${seed ?: ""}:$id")
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ThrowsCount")
    private fun resolveDescriptor(
        context: ParseContext,
        descriptor: TemplateDescriptor,
        expectType: PipelineTemplateType
    ): Pair<PipelineTemplateResource, ResolvedTemplate> {
        val projectId = context.projectId
        val (templateResource, resolvedTemplate) = when (descriptor.templateRefType) {
            TemplateRefType.ID -> {
                val templateId = descriptor.templateId ?: throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_TYPE
                )
                val versionName = descriptor.templateVersionName?.takeIf { it.isNotBlank() }
                    ?: throw ErrorCodeException(errorCode = ProcessMessageCode.ERROR_TEMPLATE_VERSION_NAME_NOT_EMPTY)
                val latest = templateResourceDao.getLatestRecord(
                    dslContext = dslContext,
                    projectId = projectId,
                    templateId = templateId,
                    versionName = versionName
                )
                // 分支版本只取活跃的分支版本
                val resource = if (latest?.status == VersionStatus.BRANCH) {
                    templateResourceDao.getLatestRecord(
                        dslContext = dslContext,
                        projectId = projectId,
                        templateId = templateId,
                        versionName = versionName,
                        status = VersionStatus.BRANCH
                    )
                } else {
                    latest
                } ?: throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_TEMPLATE_VERSION_BY_ID_NOT_FOUND,
                    params = arrayOf(templateId, versionName)
                )
                templateResourceCache.put(cacheKey(projectId, resource.templateId, resource.version), resource)
                Pair(
                    resource,
                    ResolvedTemplate(
                        templateId = resource.templateId,
                        version = resource.version,
                        versionName = resource.versionName
                    )
                )
            }

            TemplateRefType.PATH -> {
                val templatePath = descriptor.templatePath ?: throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_TYPE
                )
                val pipelineYamlInfo = yamlInfoDao.get(
                    dslContext = dslContext,
                    projectId = projectId,
                    pipelineId = context.pipelineId
                ) ?: throw ErrorCodeException(
                    errorCode = CommonMessageCode.PARAMETER_IS_NULL,
                    params = arrayOf("repoHashId")
                )
                val repoHashId = pipelineYamlInfo.repoHashId
                val templateYamlInfo = yamlInfoDao.get(
                    dslContext = dslContext,
                    projectId = projectId,
                    repoHashId = repoHashId,
                    filePath = templatePath
                ) ?: throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_YAML_FOR_TEMPLATE_NOT_FOUND,
                    params = arrayOf(templatePath)
                )
                val ref = descriptor.templateRef?.takeIf { it.isNotBlank() }
                    ?: context.branchName?.takeIf { it.isNotBlank() }
                val defaultBranch = templateYamlInfo.defaultBranch ?: pipelineYamlInfo.defaultBranch
                    ?: throw ErrorCodeException(
                        errorCode = ProcessMessageCode.ERROR_PIPELINE_REF_TEMPLATE_YAML_FILE_NOT_FOUND,
                        params = arrayOf(templatePath, ref ?: "")
                    )
                val branchVersion = yamlVersionResolver.resolveTemplateRefVersionFromDb(
                    projectId = projectId,
                    repoHashId = repoHashId,
                    filePath = templatePath,
                    defaultBranch = defaultBranch,
                    ref = ref
                )
                val templateId = templateYamlInfo.pipelineId
                val version = branchVersion.yamlVersion.version.toLong()
                val resource = getTemplateResource(
                    projectId = projectId,
                    templateId = templateId,
                    version = version
                ) ?: throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_TEMPLATE_VERSION_BY_PATH_NOT_FOUND,
                    params = arrayOf(templatePath, ref ?: defaultBranch)
                )
                Pair(
                    resource,
                    ResolvedTemplate(
                        templateId = resource.templateId,
                        version = resource.version,
                        versionName = resource.versionName,
                        commitId = branchVersion.commitId,
                        blobId = branchVersion.blobId
                    )
                )
            }

            else -> throw ErrorCodeException(errorCode = ProcessMessageCode.ERROR_TEMPLATE_REF_TYPE)
        }
        if (templateResource.type != expectType) {
            throw ErrorCodeException(errorCode = ProcessMessageCode.ERROR_TEMPLATE_TYPE_MODEL_TYPE_NOT_MATCH)
        }
        logger.info(
            "resolve template descriptor|$projectId|${context.pipelineId}|${descriptorKey(descriptor)}|" +
                "${resolvedTemplate.templateId}|${resolvedTemplate.version}"
        )
        return Pair(templateResource, resolvedTemplate)
    }

    private fun getTemplateResource(
        projectId: String,
        templateId: String,
        version: Long
    ): PipelineTemplateResource? {
        val key = cacheKey(projectId, templateId, version)
        templateResourceCache.getIfPresent(key)?.let { return it }
        return templateResourceDao.get(
            dslContext = dslContext,
            projectId = projectId,
            templateId = templateId,
            version = version
        )?.also { templateResourceCache.put(key, it) }
    }

    private fun cacheKey(projectId: String, templateId: String, version: Long) = "$projectId|$templateId|$version"

    private fun descriptorKey(descriptor: TemplateDescriptor): String {
        return descriptor.templateId ?: descriptor.templatePath ?: ""
    }

    private fun checkRepeatedJobId(flatModel: Model) {
        val jobIds = mutableSetOf<String>()
        flatModel.stages.forEach { stage ->
            stage.containers.forEach { container ->
                val jobId = container.jobId
                if (container !is TriggerContainer && !jobId.isNullOrBlank() && !jobIds.add(jobId)) {
                    throw ErrorCodeException(
                        errorCode = ProcessMessageCode.ERROR_JOB_ID_DUPLICATE,
                        params = arrayOf(jobId)
                    )
                }
            }
        }
    }

    private fun flattenStage(stage: Stage): List<Stage> {
        if (stage.template != null) {
            return stage.templateStages.orEmpty().flatMap { flattenStage(it) }
        }
        val hasRef = stage.containers.any { container ->
            container is JobTemplateContainer || container.elements.any { it is StepTemplateElement }
        }
        if (!hasRef) {
            return listOf(stage)
        }
        return listOf(stage.copy(containers = stage.containers.flatMap { flattenContainer(it) }))
    }

    private fun flattenContainer(container: Container): List<Container> {
        if (container is JobTemplateContainer) {
            return container.templateContainers.orEmpty().flatMap { flattenContainer(it) }
        }
        if (container.elements.none { it is StepTemplateElement }) {
            return listOf(container)
        }
        return listOf(container.copyElements(container.elements.flatMap { flattenElement(it) }))
    }

    private fun flattenElement(element: Element): List<Element> {
        if (element is StepTemplateElement) {
            return element.templateElements.orEmpty().flatMap { flattenElement(it) }
        }
        return listOf(element)
    }

    private fun restoreStage(
        stage: Stage,
        flatStages: Map<String?, Stage>,
        flatContainers: Map<String?, Container>
    ): Stage {
        if (stage.template != null) {
            val subStages = stage.templateStages.orEmpty().map { restoreStage(it, flatStages, flatContainers) }
            stage.templateStages = subStages
            stage.status = aggregateStatus(subStages.map { it.status })
            stage.executeCount = subStages.mapNotNull { it.executeCount }.maxOrNull()
            stage.timeCost = sumTimeCost(subStages.map { it.timeCost })
            return stage
        }
        val flatStage = flatStages[stage.id] ?: return stage
        val hasRef = stage.containers.any { container ->
            container is JobTemplateContainer || container.elements.any { it is StepTemplateElement }
        }
        if (!hasRef) {
            return flatStage
        }
        return flatStage.copy(containers = stage.containers.map { restoreContainer(it, flatContainers) })
    }

    private fun restoreContainer(
        container: Container,
        flatContainers: Map<String?, Container>
    ): Container {
        if (container is JobTemplateContainer) {
            val subContainers = container.templateContainers.orEmpty().map { restoreContainer(it, flatContainers) }
            container.templateContainers = subContainers
            container.status = aggregateStatus(subContainers.map { it.status })
            container.executeCount = subContainers.mapNotNull { it.executeCount }.maxOrNull()
            // 模板内的 job 并行执行,耗时取最长的一个
            container.timeCost = subContainers.mapNotNull { it.timeCost }.maxByOrNull { it.totalCost }
            return container
        }
        val flatContainer = flatContainers[container.id] ?: return container
        if (container.elements.none { it is StepTemplateElement }) {
            return flatContainer
        }
        val baseElementIds = mutableSetOf<String>()
        collectElementIds(container.elements, baseElementIds)
        val cursor = ElementCursor(elements = flatContainer.elements, baseElementIds = baseElementIds)
        val elements = restoreElements(container.elements, cursor) + cursor.remaining()
        return flatContainer.copyElements(elements)
    }

    private fun restoreElements(elements: List<Element>, cursor: ElementCursor): List<Element> {
        val result = mutableListOf<Element>()
        elements.forEach { element ->
            if (element is StepTemplateElement) {
                val subElements = restoreElements(element.templateElements.orEmpty(), cursor)
                element.templateElements = subElements
                element.status = aggregateStatus(subElements.map { it.status })
                element.executeCount = subElements.maxOfOrNull { it.executeCount } ?: element.executeCount
                element.timeCost = sumTimeCost(subElements.map { it.timeCost })
                result.add(element)
            } else {
                result.addAll(cursor.takeExtras())
                result.add(cursor.take(element.id) ?: element)
            }
        }
        return result
    }

    private fun collectElementIds(elements: List<Element>, result: MutableSet<String>) {
        elements.forEach { element ->
            if (element is StepTemplateElement) {
                collectElementIds(element.templateElements.orEmpty(), result)
            } else {
                element.id?.let { result.add(it) }
            }
        }
    }

    /**
     * 引用节点状态:有执行中的取执行中,其次失败、取消;全部跳过为跳过,全部成功为成功
     */
    private fun aggregateStatus(statuses: List<String?>): String? {
        val buildStatuses = statuses.filterNot { it.isNullOrBlank() }.map { BuildStatus.parse(it) }
        if (buildStatuses.isEmpty()) {
            return null
        }
        buildStatuses.firstOrNull { it.isRunning() || it.isReadyToRun() }?.let { return it.name }
        buildStatuses.firstOrNull { it.isFailure() }?.let { return it.name }
        buildStatuses.firstOrNull { it.isCancel() }?.let { return it.name }
        if (buildStatuses.all { it.isSkip() }) {
            return BuildStatus.SKIP.name
        }
        if (buildStatuses.all { it.isSuccess() }) {
            return BuildStatus.SUCCEED.name
        }
        return (buildStatuses.lastOrNull { !it.isNeverRun() } ?: buildStatuses.first()).name
    }

    private fun sumTimeCost(timeCosts: List<BuildRecordTimeCost?>): BuildRecordTimeCost? {
        val costs = timeCosts.filterNotNull()
        if (costs.isEmpty()) {
            return null
        }
        return BuildRecordTimeCost(
            systemCost = costs.sumOf { it.systemCost },
            executeCost = costs.sumOf { it.executeCost },
            waitCost = costs.sumOf { it.waitCost },
            queueCost = costs.sumOf { it.queueCost },
            totalCost = costs.sumOf { it.totalCost }
        )
    }

    /**
     * 按顺序消费平铺后合并了构建记录的插件列表
     */
    private class ElementCursor(
        private val elements: List<Element>,
        private val baseElementIds: Set<String>
    ) {
        private var index = 0

        /**
         * 取出下一个编排插件之前由构建补充的插件,比如质量红线
         */
        fun takeExtras(): List<Element> {
            val extras = mutableListOf<Element>()
            while (index < elements.size && elements[index].id !in baseElementIds) {
                extras.add(elements[index++])
            }
            return extras
        }

        fun take(id: String?): Element? {
            if (index < elements.size && elements[index].id == id) {
                return elements[index++]
            }
            return null
        }

        fun remaining(): List<Element> {
            val remaining = elements.subList(index, elements.size).toList()
            index = elements.size
            return remaining
        }
    }

    private fun collectResolvedTemplates(stage: Stage, result: MutableList<ResolvedTemplate>) {
        stage.resolvedTemplate?.let { result.add(it) }
        stage.templateStages?.forEach { collectResolvedTemplates(it, result) }
        stage.containers.forEach { collectResolvedTemplates(it, result) }
    }

    private fun collectResolvedTemplates(container: Container, result: MutableList<ResolvedTemplate>) {
        if (container is JobTemplateContainer) {
            container.resolvedTemplate?.let { result.add(it) }
            container.templateContainers?.forEach { collectResolvedTemplates(it, result) }
        }
        container.elements.forEach { collectResolvedTemplates(it, result) }
    }

    private fun collectResolvedTemplates(element: Element, result: MutableList<ResolvedTemplate>) {
        if (element is StepTemplateElement) {
            element.resolvedTemplate?.let { result.add(it) }
            element.templateElements?.forEach { collectResolvedTemplates(it, result) }
        }
    }

    private fun <T : Any> deepCopy(obj: T): T {
        return JsonUtil.to(JsonUtil.toJson(obj, false), obj.javaClass)
    }

    private class ParseContext(
        val projectId: String,
        val pipelineId: String,
        val branchName: String?
    ) {
        val stageSeq = AtomicInteger(0)
        val containerSeq = AtomicInteger(0)
        private val occurrences = mutableMapOf<String, Int>()

        fun nextOccurrence(key: String): Int {
            val next = (occurrences[key] ?: 0) + 1
            occurrences[key] = next
            return next
        }
    }
}
