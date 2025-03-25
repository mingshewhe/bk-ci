package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.enums.CodeTargetAction
import com.tencent.devops.common.pipeline.enums.CodeTargetAction.CHECKOUT_BRANCH_AND_REQUEST_MERGE
import com.tencent.devops.common.pipeline.enums.CodeTargetAction.COMMIT_TO_BRANCH
import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.PipelineModelAndSetting
import com.tencent.devops.common.pipeline.pojo.transfer.TransferActionType
import com.tencent.devops.common.pipeline.pojo.transfer.TransferBody
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.dao.PipelineSettingDao
import com.tencent.devops.process.dao.PipelineSettingVersionDao
import com.tencent.devops.process.engine.cfg.PipelineIdGenerator
import com.tencent.devops.process.engine.dao.template.TemplateInstanceBaseDao
import com.tencent.devops.process.engine.dao.template.TemplateInstanceItemDao
import com.tencent.devops.process.engine.dao.template.TemplatePipelineDao
import com.tencent.devops.process.engine.pojo.event.PipelineTemplateInstanceEvent
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.engine.utils.PipelineUtils
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.pojo.PipelineVersionReleaseRequest
import com.tencent.devops.process.pojo.pipeline.PipelineYamlVo
import com.tencent.devops.process.pojo.template.TemplateInstanceStatus
import com.tencent.devops.process.pojo.template.TemplateInstanceUpdate
import com.tencent.devops.process.pojo.template.TemplateOperationMessage
import com.tencent.devops.process.pojo.template.TemplateOperationRet
import com.tencent.devops.process.pojo.template.TemplatePipelineStatus
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstanceBase
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstanceItem
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstanceReleaseInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstancesRequest
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedResp
import com.tencent.devops.process.pojo.template.v2.TemplateInstanceType
import com.tencent.devops.process.service.PipelineInfoFacadeService
import com.tencent.devops.process.service.PipelineRemoteAuthService
import com.tencent.devops.process.service.PipelineVersionFacadeService
import com.tencent.devops.process.service.StageTagService
import com.tencent.devops.process.service.label.PipelineGroupService
import com.tencent.devops.process.service.pipeline.PipelineSettingFacadeService
import com.tencent.devops.process.service.pipeline.PipelineTransferYamlService
import com.tencent.devops.process.yaml.PipelineYamlFacadeService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

@Service
class PipelineTemplateInstanceFacadeService @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateRelatedService: PipelineTemplateRelatedService,
    private val stageTagService: StageTagService,
    private val dslContext: DSLContext,
    private val templateInstanceItemDao: TemplateInstanceItemDao,
    private val templateInstanceBaseDao: TemplateInstanceBaseDao,
    private val pipelineInfoFacadeService: PipelineInfoFacadeService,
    private val pipelineSettingFacadeService: PipelineSettingFacadeService,
    private val pipelineSettingVersionDao: PipelineSettingVersionDao,
    private val pipelineTemplateInstanceSettingService: PipelineTemplateInstanceSettingService,
    private val pipelineRemoteAuthService: PipelineRemoteAuthService,
    private val pipelineGroupService: PipelineGroupService,
    private val pipelineSettingDao: PipelineSettingDao,
    private val templatePipelineDao: TemplatePipelineDao,
    private val pipelineIdGenerator: PipelineIdGenerator,
    private val pipelineYamlFacadeService: PipelineYamlFacadeService,
    private val pipelineEventDispatcher: PipelineEventDispatcher,
    private val redisOperation: RedisOperation,
    private val transferService: PipelineTransferYamlService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val pipelineRepositoryService: PipelineRepositoryService
) {
    /*同步创建模板实例*/
    fun createTemplateInstances(
        projectId: String,
        userId: String,
        templateId: String,
        version: Long,
        useTemplateSettings: Boolean,
        request: PipelineTemplateInstancesRequest
    ): TemplateOperationRet {
        logger.info("template instance creation start $projectId|$userId|$templateId")
        val templateResource = pipelineTemplateResourceService.get(projectId, templateId, version)

        val templateModel = templateResource.model as Model
        val instances = request.instanceReleaseInfos

        val successPipelines = mutableListOf<String>()
        val failurePipelines = mutableListOf<String>()
        val successPipelineIds = mutableListOf<String>()
        val failureMessages = mutableMapOf<String, String>()

        instances.forEach { instance ->
            try {
                val pipelineId = createTemplateInstance(
                    projectId = projectId,
                    userId = userId,
                    pipelineId = pipelineIdGenerator.getNextId(),
                    templateId = templateId,
                    instance = instance,
                    templateModel = templateModel,
                    templateVersion = version,
                    useTemplateSettings = useTemplateSettings,
                    templateSettingVersion = templateResource.settingVersion,
                    enabledPac = request.enablePac,
                    targetAction = request.targetAction,
                    repoHashId = request.repoHashId,
                    targetBranch = request.targetBranch,
                    scmType = request.scmType,
                    description = request.description
                )
                successPipelines.add(instance.pipelineName)
                successPipelineIds.add(pipelineId)
            } catch (ignored: Throwable) {
                handleSyncCreateInstanceErrorMessage(
                    projectId = projectId,
                    userId = userId,
                    instance = instance,
                    error = ignored,
                    failurePipelines = failurePipelines,
                    failureMessages = failureMessages
                )
            }
        }
        return TemplateOperationRet(
            0,
            TemplateOperationMessage(
                successPipelines = successPipelines,
                failurePipelines = failurePipelines,
                failureMessages = failureMessages,
                successPipelinesId = successPipelineIds
            ),
            ""
        )
    }

    private fun createTemplateInstance(
        projectId: String,
        pipelineId: String,
        userId: String,
        templateId: String,
        instance: PipelineTemplateInstanceReleaseInfo,
        enabledPac: Boolean,
        targetAction: CodeTargetAction?,
        description: String?,
        templateModel: Model,
        templateVersion: Long,
        templateSettingVersion: Int,
        useTemplateSettings: Boolean,
        repoHashId: String?,
        scmType: ScmType?,
        targetBranch: String?
    ): String {
        // 获取默认阶段标签
        val defaultStageTag = stageTagService.getDefaultStageTag().data
        val defaultStageTagId = defaultStageTag?.id

        // 构建实例模型
        val instanceModel = PipelineUtils.instanceModel(
            templateModel = templateModel,
            pipelineName = instance.pipelineName,
            buildNo = instance.buildNo,
            param = instance.param,
            instanceFromTemplate = true,
            defaultStageTagId = defaultStageTagId,
            templateId = templateId
        )

        val setting = if (useTemplateSettings) {
            pipelineTemplateInstanceSettingService.getTemplateInstanceSetting(
                projectId = projectId,
                templateId = templateId,
                settingVersion = templateSettingVersion,
                pipelineId = pipelineId,
                pipelineName = instance.pipelineName,
                pipelineLabels = null,
                enabledPac = enabledPac,
                version = 1
            )
        } else {
            pipelineTemplateInstanceSettingService.getTemplateInstanceDefaultSetting(
                projectId = projectId,
                pipelineId = pipelineId,
                pipelineName = instance.pipelineName
            )
        }
        val transferResult = transferService.transfer(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            actionType = TransferActionType.FULL_MODEL2YAML,
            data = TransferBody(
                modelAndSetting = PipelineModelAndSetting(
                    model = instanceModel,
                    setting = setting
                )
            )
        )

        val (branchName, versionStatus) = when {
            (enabledPac && targetAction == CHECKOUT_BRANCH_AND_REQUEST_MERGE) -> {
                Pair(
                    first = PipelineVersionFacadeService.getReleaseBranchName(
                        pipelineId = pipelineId,
                        version = PipelineTemplateConstant.INIT_VERSION
                    ),
                    second = VersionStatus.BRANCH
                )
            }

            (enabledPac && targetAction == COMMIT_TO_BRANCH) -> {
                // todo 如果指定分支是默认分支，为正式版本
                Pair(
                    first = targetBranch ?: throw ErrorCodeException(errorCode = ""),
                    second = VersionStatus.BRANCH
                )
            }

            else -> {
                Pair(
                    first = null,
                    second = VersionStatus.RELEASED
                )
            }
        }
        val yamlInfo = targetAction?.let {
            PipelineYamlVo(
                repoHashId = repoHashId!!,
                scmType = scmType!!,
                filePath = instance.filePath!!
            )
        }

        // 创建流水线
        pipelineInfoFacadeService.createPipeline(
            userId = userId,
            projectId = projectId,
            model = instanceModel,
            channelCode = ChannelCode.BS,
            fixPipelineId = pipelineId,
            checkPermission = true,
            yaml = transferResult.yamlWithVersion,
            instanceType = PipelineInstanceTypeEnum.CONSTRAINT.type,
            buildNo = instance.buildNo,
            param = instance.param,
            fixTemplateVersion = templateVersion,
            versionStatus = versionStatus,
            branchName = branchName,
            yamlInfo = yamlInfo,
            description = description
        )

        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            pipelineSettingFacadeService.saveSetting(
                context = context,
                userId = userId,
                projectId = setting.projectId,
                pipelineId = setting.pipelineId,
                setting = setting
            )
            pipelineRemoteAuthService.addRemoteAuth(
                model = instanceModel,
                projectId = projectId,
                pipelineId = pipelineId,
                userId = userId
            )
        }
        if (enabledPac) {
            val fixTargetAction = targetAction ?: throw ErrorCodeException(errorCode = "")
            if (yamlInfo == null) throw ErrorCodeException(
                errorCode = CommonMessageCode.ERROR_NEED_PARAM_,
                params = arrayOf(PipelineVersionReleaseRequest::yamlInfo.name)
            )
            // 对前端的YAML信息进行校验
            if (!yamlInfo.filePath.endsWith(".yaml") && !yamlInfo.filePath.endsWith(".yml")) {
                throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_PIPELINE_YAML_FILENAME,
                    params = arrayOf(yamlInfo.filePath)
                )
            }

            pipelineYamlFacadeService.pushYamlFile(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                version = PipelineTemplateConstant.INIT_VERSION,
                versionName = branchName,
                pipelineName = instance.pipelineName,
                content = transferResult.yamlWithVersion?.yamlStr ?: "",
                commitMessage = description ?: "update",
                repoHashId = yamlInfo.repoHashId,
                scmType = yamlInfo.scmType!!,
                filePath = yamlInfo.filePath,
                targetAction = fixTargetAction
            )
        }
        return pipelineId
    }

    private fun handleSyncCreateInstanceErrorMessage(
        projectId: String,
        instance: PipelineTemplateInstanceReleaseInfo,
        userId: String,
        error: Throwable,
        failurePipelines: MutableList<String>,
        failureMessages: MutableMap<String, String>
    ) {
        when (error) {
            is DuplicateKeyException -> {
                logger.warn("TemplateCreateInstanceDuplicate|$projectId|$instance|$userId|${error.message}")
                failurePipelines.add(instance.pipelineName)
                failureMessages[instance.pipelineName] = "duplicate!"
            }

            is ErrorCodeException -> {
                logger.warn("TemplateCreateInstanceErrorCode|$projectId|$instance|$userId|${error.message}")
                failureMessages[instance.pipelineName] = I18nUtil.generateResponseDataObject(
                    messageCode = error.errorCode,
                    params = error.params,
                    data = null,
                    defaultMessage = error.defaultMessage
                ).message ?: error.defaultMessage ?: "unknown!"
                failurePipelines.add(instance.pipelineName)
            }

            else -> {
                logger.warn("TemplateCreateInstanceThrowable|$projectId|$instance|$userId|${error.message}")
                failurePipelines.add(instance.pipelineName)
                failureMessages[instance.pipelineName] = error.message ?: "create instance fail"
            }
        }
    }

    /*异步创建流水线模板实例*/
    fun asyncCreateTemplateInstances(
        projectId: String,
        userId: String,
        templateId: String,
        version: Long,
        useTemplateSettings: Boolean,
        request: PipelineTemplateInstancesRequest
    ): String {
        logger.info(
            "async template instance creation start $projectId|$userId|$templateId|" +
                "$version|$useTemplateSettings|$request"
        )
        pipelineTemplateResourceService.get(projectId, templateId, version)
        val instances = request.instanceReleaseInfos.map {
            it.copy(pipelineId = pipelineIdGenerator.getNextId())
        }
        val baseId = UUIDUtil.generate()

        val pipelineIds = instances.map { it.pipelineId }.toSet()
        val templateInstanceItems = templateInstanceItemDao.getTemplateInstanceItemListByPipelineIds(
            dslContext = dslContext,
            projectId = projectId,
            pipelineIds = pipelineIds
        )
        if (!templateInstanceItems.isNullOrEmpty()) {
            val pipelineNames = templateInstanceItems.map { it.pipelineName }
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_PIPELINE_IS_INSTANCING,
                params = arrayOf(JsonUtil.toJson(pipelineNames))
            )
        }
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            templateInstanceBaseDao.createTemplateInstanceBase(
                dslContext = context,
                baseId = baseId,
                templateId = templateId,
                templateVersion = version.toString(),
                useTemplateSettingsFlag = useTemplateSettings,
                projectId = projectId,
                totalItemNum = instances.size,
                status = TemplateInstanceStatus.INIT.name,
                userId = userId,
                pac = request.enablePac,
                targetAction = request.targetAction?.name,
                type = TemplateInstanceType.CREATE.name,
                repoHashId = request.repoHashId,
                scmType = request.scmType,
                targetBranch = request.targetBranch,
                description = request.description
            )
            templateInstanceItemDao.createTemplateInstanceItemsV2(
                dslContext = context,
                projectId = projectId,
                baseId = baseId,
                instances = instances,
                status = TemplateInstanceStatus.INIT.name,
                userId = userId
            )
            pipelineEventDispatcher.dispatch(
                PipelineTemplateInstanceEvent(
                    projectId = projectId,
                    source = "PIPELINE_TEMPLATE_INSTANCE_CREATE",
                    pipelineId = "",
                    userId = userId,
                    templateId = templateId,
                    baseId = baseId,
                    templateInstanceType = TemplateInstanceType.CREATE
                )
            )
        }
        return baseId
    }

    /*异步更新模板实例*/
    fun asyncUpdateTemplateInstances(
        projectId: String,
        userId: String,
        templateId: String,
        version: Long,
        useTemplateSettings: Boolean,
        request: PipelineTemplateInstancesRequest
    ): String {
        logger.info("asyncUpdateTemplateInstances [$projectId|$userId|$templateId|$version|$useTemplateSettings]")
        val templateResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = templateId,
            version = version
        )
        val instances = request.instanceReleaseInfos
        val baseId = UUIDUtil.generate()
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            templateInstanceBaseDao.createTemplateInstanceBase(
                dslContext = context,
                baseId = baseId,
                templateId = templateId,
                templateVersion = templateResource.version.toString(),
                useTemplateSettingsFlag = useTemplateSettings,
                projectId = projectId,
                totalItemNum = instances.size,
                status = TemplateInstanceStatus.INIT.name,
                userId = userId,
                pac = request.enablePac,
                targetAction = request.targetAction?.name,
                type = TemplateInstanceType.UPDATE.name,
                description = request.description,
                repoHashId = request.repoHashId,
                scmType = request.scmType,
                targetBranch = request.targetBranch
            )
            templateInstanceItemDao.createTemplateInstanceItemsV2(
                dslContext = context,
                projectId = projectId,
                baseId = baseId,
                instances = instances,
                status = TemplateInstanceStatus.INIT.name,
                userId = userId
            )
            pipelineEventDispatcher.dispatch(
                PipelineTemplateInstanceEvent(
                    projectId = projectId,
                    source = "PIPELINE_TEMPLATE_INSTANCE_UPDATE",
                    pipelineId = "",
                    userId = userId,
                    templateId = templateId,
                    baseId = baseId,
                    templateInstanceType = TemplateInstanceType.UPDATE
                )
            )
        }
        return baseId
    }

    fun updateTemplateInstance(
        projectId: String,
        pipelineId: String,
        userId: String,
        templateId: String,
        instance: PipelineTemplateInstanceReleaseInfo,
        enabledPac: Boolean,
        targetAction: CodeTargetAction?,
        description: String?,
        templateModel: Model,
        templateVersion: Long,
        templateSettingVersion: Int,
        useTemplateSettings: Boolean,
        repoHashId: String?,
        scmType: ScmType?,
        targetBranch: String?
    ) {
        val labels = if (useTemplateSettings) {
            pipelineTemplateSettingService.getPipelineTemplateSetting(
                projectId = projectId,
                templateId = templateId,
                settingVersion = templateSettingVersion
            ).labels
        } else {
            pipelineGroupService.getGroups(
                userId = userId,
                projectId = projectId,
                pipelineId = instance.pipelineId
            ).flatMap { it.labels }
        }

        val instanceModel = PipelineUtils.instanceModel(
            templateModel = templateModel,
            pipelineName = instance.pipelineName,
            buildNo = instance.buildNo,
            param = instance.param,
            instanceFromTemplate = true,
            labels = labels,
            defaultStageTagId = stageTagService.getDefaultStageTag().data?.id,
            templateId = templateId
        )
        val pipelineSetting = pipelineSettingDao.getSetting(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = instance.pipelineId
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.PIPELINE_SETTING_NOT_EXISTS
        )

        val lastedPipelineSetting = if (useTemplateSettings) {
            pipelineTemplateInstanceSettingService.getTemplateInstanceSetting(
                projectId = projectId,
                templateId = templateId,
                settingVersion = templateSettingVersion,
                pipelineId = pipelineId,
                pipelineName = instance.pipelineName,
                pipelineLabels = labels,
                enabledPac = enabledPac,
                version = pipelineSetting.version + 1
            )
        } else {
            pipelineSetting
        }

        val transferResult = transferService.transfer(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            actionType = TransferActionType.FULL_MODEL2YAML,
            data = TransferBody(
                modelAndSetting = PipelineModelAndSetting(
                    model = instanceModel,
                    setting = lastedPipelineSetting
                )
            )
        )

        val (branchName, versionStatus) = when {
            (enabledPac && targetAction == CHECKOUT_BRANCH_AND_REQUEST_MERGE) -> {
                val latestResource = pipelineRepositoryService.getLatestResource(
                    projectId = projectId,
                    pipelineId = pipelineId
                )
                Pair(
                    first = PipelineVersionFacadeService.getReleaseBranchName(
                        pipelineId = pipelineId,
                        version = latestResource.version + 1
                    ),
                    second = VersionStatus.BRANCH
                )
            }

            (enabledPac && targetAction == COMMIT_TO_BRANCH) -> {
                Pair(
                    first = targetBranch ?: throw ErrorCodeException(errorCode = ""),
                    second = VersionStatus.BRANCH
                )
            }

            else -> {
                Pair(
                    first = null,
                    second = VersionStatus.RELEASED
                )
            }
        }

        val yamlInfo = targetAction?.let {
            PipelineYamlVo(
                repoHashId = repoHashId!!,
                scmType = scmType!!,
                filePath = instance.filePath!!
            )
        }

        pipelineInfoFacadeService.editPipeline(
            userId = userId,
            projectId = projectId,
            pipelineId = instance.pipelineId,
            model = instanceModel,
            yaml = transferResult.yamlWithVersion,
            channelCode = ChannelCode.BS,
            checkPermission = true,
            checkTemplate = false,
            versionStatus = versionStatus,
            branchName = branchName,
            description = description,
            yamlInfo = yamlInfo
        )
        if (useTemplateSettings) {
            pipelineSettingFacadeService.saveSetting(
                userId = userId,
                projectId = projectId,
                pipelineId = instance.pipelineId,
                setting = pipelineSetting,
                checkPermission = true,
                dispatchPipelineUpdateEvent = false
            )
        } else {
            // 不应用模板设置但是修改了流水线名称,需要重命名流水线
            if (pipelineSetting.pipelineName != instance.pipelineName) {
                pipelineSettingFacadeService.saveSetting(
                    userId = userId,
                    projectId = projectId,
                    pipelineId = instance.pipelineId,
                    setting = pipelineSetting.apply {
                        this.pipelineName = instance.pipelineName
                    },
                    checkPermission = true,
                    dispatchPipelineUpdateEvent = false
                )
            }
        }

        if (enabledPac) {
            val fixTargetAction = targetAction ?: throw ErrorCodeException(errorCode = "")
            if (yamlInfo == null) throw ErrorCodeException(
                errorCode = CommonMessageCode.ERROR_NEED_PARAM_,
                params = arrayOf(PipelineVersionReleaseRequest::yamlInfo.name)
            )
            // 对前端的YAML信息进行校验
            if (!yamlInfo.filePath.endsWith(".yaml") && !yamlInfo.filePath.endsWith(".yml")) {
                throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_PIPELINE_YAML_FILENAME,
                    params = arrayOf(yamlInfo.filePath)
                )
            }

            // todo
        }
    }

    private fun handleSyncUpdateInstancesErrorMessage(
        projectId: String,
        userId: String,
        instance: TemplateInstanceUpdate,
        error: Throwable,
        failurePipelines: MutableList<String>,
        failureMessages: MutableMap<String, String>
    ) {
        when (error) {
            is DuplicateKeyException -> {
                logger.warn("updateTemplateInstancesDuplicate|$projectId|$instance|$userId|${error.message}")
                failurePipelines.add(instance.pipelineName)
                failureMessages[instance.pipelineName] = " exist!"
            }

            is ErrorCodeException -> {
                logger.warn("updateTemplateInstancesErrorCode|$projectId|$instance|$userId|${error.message}")
                failureMessages[instance.pipelineName] = I18nUtil.generateResponseDataObject(
                    messageCode = error.errorCode,
                    params = error.params,
                    data = null,
                    defaultMessage = error.defaultMessage
                ).message ?: error.defaultMessage ?: "unknown!"
                failurePipelines.add(instance.pipelineName)
            }

            else -> {
                logger.warn("updateTemplateInstancesThrowable|$projectId|$instance|$userId|${error.message}")
                failurePipelines.add(instance.pipelineName)
                failureMessages[instance.pipelineName] = error.message ?: "update instance fail"
            }
        }
    }

    fun updateInstanceErrorInfo(
        projectId: String,
        pipelineId: String,
        errorInfo: String?
    ) {
        if (errorInfo == null) return
        try {
            templatePipelineDao.updateInstanceErrorInfo(
                dslContext = dslContext,
                projectId = projectId,
                pipelineId = pipelineId,
                errorInfo = errorInfo
            )
        } catch (ignored: Exception) {
            logger.warn("Failed to update instance error info|$projectId|$pipelineId$errorInfo")
        }
    }

    fun generateTemplatePipelineStatus(
        templateInstanceItem: PipelineTemplateInstanceItem?,
        templatePipelineId: String,
        templatePipelineVersion: Long,
        // 模板最新发布版本
        templateReleasedVersion: Long
    ): TemplatePipelineStatus {

        return when {
            templateInstanceItem?.status == TemplateInstanceStatus.INSTANCING ||
                templateInstanceItem?.status == TemplateInstanceStatus.INIT -> TemplatePipelineStatus.UPDATING

            templateInstanceItem?.status == TemplateInstanceStatus.FAILED ->
                TemplatePipelineStatus.FAILED

            templatePipelineVersion != templateReleasedVersion ->
                TemplatePipelineStatus.PENDING_UPDATE

            else ->
                TemplatePipelineStatus.UPDATED
        }
    }

    fun handleTemplateInstanceEvent(event: PipelineTemplateInstanceEvent) {
        val baseId = event.baseId
        val projectId = event.projectId
        val type = event.templateInstanceType
        PipelineTemplateInstanceLock(redisOperation, baseId).use { lock ->
            logger.info("start to handle template event {}|,{}", type, event)
            if (!lock.tryLock()) {
                logger.warn("handle template instance event running ${event.projectId}|${event.baseId}")
                return@use
            }
            val instanceBase = templateInstanceBaseDao.getTemplateInstanceBase(
                dslContext = dslContext,
                projectId = event.projectId,
                baseId = event.baseId
            ) ?: throw ErrorCodeException(errorCode = "")

            val templateInstanceItemCount = templateInstanceItemDao.getTemplateInstanceItemCountByBaseId(
                dslContext = dslContext,
                projectId = projectId,
                baseId = baseId,
                excludeStatusList = listOf(TemplateInstanceStatus.SUCCESS.name)
            )

            checkTemplateInstanceEvent(
                instanceBase = instanceBase,
                projectId = projectId,
                baseId = baseId,
                templateInstanceItemCount = templateInstanceItemCount
            )

            // 开始执行任务
            templateInstanceBaseDao.updateTemplateInstanceBase(
                dslContext = dslContext,
                projectId = event.projectId,
                baseId = event.baseId,
                status = TemplateInstanceStatus.INSTANCING.name,
                userId = "system"
            )
            val templateResource = pipelineTemplateResourceService.get(
                projectId = projectId,
                templateId = instanceBase.templateId,
                version = instanceBase.templateVersion
            )

            val templateSettingVersion = templateResource.settingVersion
            val templateModel = templateResource.model as Model
            val successPipelines = mutableListOf<String>()
            val failurePipelines = mutableListOf<String>()
            val totalPages = PageUtil.calTotalPage(PageUtil.MAX_PAGE_SIZE, templateInstanceItemCount)

            for (page in 1..totalPages) {
                val templateInstanceItems = templateInstanceItemDao.listTemplateInstanceItemByBaseIds(
                    dslContext = dslContext,
                    projectId = projectId,
                    baseIds = listOf(baseId),
                    excludeStatusList = listOf(TemplateInstanceStatus.SUCCESS.name),
                    page = page,
                    pageSize = PageUtil.MAX_PAGE_SIZE
                )
                templateInstanceItems.forEach { item ->
                    with(item) {
                        logger.info("${type.toString().lowercase()} template instance {}", item)
                        if (item.status == TemplateInstanceStatus.SUCCESS)
                            return@forEach
                        templateInstanceItemDao.updateStatus(
                            dslContext = dslContext,
                            projectId = item.projectId,
                            baseId = item.baseId,
                            pipelineIds = listOf(item.pipelineId),
                            status = TemplateInstanceStatus.INSTANCING
                        )
                        val instance = PipelineTemplateInstanceReleaseInfo(
                            pipelineId = pipelineId,
                            pipelineName = pipelineName,
                            buildNo = buildNo,
                            param = params,
                            filePath = filePath
                        )

                        try {
                            if (type == TemplateInstanceType.CREATE) {
                                createTemplateInstance(
                                    projectId = projectId,
                                    pipelineId = item.pipelineId,
                                    userId = creator,
                                    templateId = instanceBase.templateId,
                                    instance = instance,
                                    enabledPac = instanceBase.pac,
                                    targetAction = instanceBase.targetAction,
                                    description = instanceBase.description,
                                    templateModel = templateModel,
                                    templateVersion = instanceBase.templateVersion,
                                    templateSettingVersion = templateSettingVersion,
                                    useTemplateSettings = instanceBase.useTemplateSetting,
                                    repoHashId = instanceBase.repoHashId,
                                    scmType = instanceBase.scmType,
                                    targetBranch = instanceBase.targetBranch
                                )
                            } else {
                                updateTemplateInstance(
                                    projectId = projectId,
                                    pipelineId = item.pipelineId,
                                    userId = creator,
                                    templateId = instanceBase.templateId,
                                    instance = instance,
                                    enabledPac = instanceBase.pac,
                                    targetAction = instanceBase.targetAction,
                                    description = instanceBase.description,
                                    templateModel = templateModel,
                                    templateVersion = instanceBase.templateVersion,
                                    templateSettingVersion = templateSettingVersion,
                                    useTemplateSettings = instanceBase.useTemplateSetting,
                                    repoHashId = instanceBase.repoHashId,
                                    scmType = instanceBase.scmType,
                                    targetBranch = instanceBase.targetBranch
                                )
                            }
                            templateInstanceItemDao.updateStatus(
                                dslContext = dslContext,
                                projectId = item.projectId,
                                baseId = item.baseId,
                                pipelineIds = listOf(item.pipelineId),
                                status = TemplateInstanceStatus.SUCCESS
                            )
                            successPipelines.add(item.pipelineId)
                        } catch (ignored: Throwable) {
                            handleTemplateInstanceEventError(
                                projectId = projectId,
                                userId = item.creator,
                                instance = item,
                                error = ignored,
                                failurePipelines = failurePipelines,
                                type = type
                            )
                        }
                    }
                }
            }
            val finalStatus = if (failurePipelines.isNotEmpty()) {
                TemplateInstanceStatus.FAILED
            } else {
                TemplateInstanceStatus.SUCCESS
            }
            templateInstanceBaseDao.updateStatus(
                dslContext = dslContext,
                projectId = event.projectId,
                baseId = event.baseId,
                status = finalStatus
            )
        }
    }

    fun checkTemplateInstanceEvent(
        projectId: String,
        baseId: String,
        templateInstanceItemCount: Long,
        instanceBase: PipelineTemplateInstanceBase
    ) {
        if (instanceBase.status == TemplateInstanceStatus.SUCCESS) {
            logger.warn(
                "The template instance task has been completed." +
                    "${instanceBase.projectId}|${instanceBase.baseId}|${instanceBase.type}"
            )
        }
        if (templateInstanceItemCount < 1) {
            templateInstanceBaseDao.updateTemplateInstanceBase(
                dslContext = dslContext,
                projectId = projectId,
                baseId = baseId,
                status = TemplateInstanceStatus.SUCCESS.name,
                userId = "system"
            )
            logger.warn("The template instance task has been completed.${projectId}|${baseId}")
        }
    }

    private fun handleTemplateInstanceEventError(
        projectId: String,
        userId: String,
        instance: PipelineTemplateInstanceItem,
        type: TemplateInstanceType,
        error: Throwable,
        failurePipelines: MutableList<String>
    ) {
        if (type == TemplateInstanceType.CREATE) {
            var errorMessage = ""
            when (error) {
                is DuplicateKeyException -> {
                    logger.warn("TemplateCreateInstanceDuplicate|$projectId|$instance|$userId|${error.message}")
                    errorMessage = "【${instance.pipelineName}】reason：duplicate！"
                }

                is ErrorCodeException -> {
                    logger.warn("TemplateCreateInstanceErrorCode|$projectId|$instance|$userId|${error.message}")
                    val reason = I18nUtil.generateResponseDataObject(
                        messageCode = error.errorCode,
                        params = error.params,
                        data = null,
                        defaultMessage = error.defaultMessage
                    ).message ?: error.defaultMessage ?: "unknown!"
                    errorMessage = "【${instance.pipelineName}】reason：$reason！"
                }

                else -> {
                    logger.warn("TemplateCreateInstanceThrowable|$projectId|$instance|$userId|${error.message}")
                    errorMessage = "【${instance.pipelineName}】reason：${error.message ?: "create instance fail"}！"
                }
            }
            templateInstanceItemDao.updateErrorMessage(
                dslContext = dslContext,
                projectId = projectId,
                baseId = instance.baseId,
                pipelineId = instance.pipelineId,
                errorMessage = errorMessage
            )
            failurePipelines.add(errorMessage)
        } else {

        }
    }

    fun list(
        userId: String,
        projectId: String,
        templateId: String,
        pipelineName: String?,
        updater: String?,
        page: Int,
        pageSize: Int
    ): SQLPage<PipelineTemplateRelatedResp> {
        val (offset, limit) = PageUtil.convertPageSizeToSQLLimit(page, pageSize)
        val templateInfo = pipelineTemplateInfoService.get(projectId, templateId)
        // todo 错误码
        val templateReleasedVersion = templateInfo.releasedVersion ?: throw ErrorCodeException(
            errorCode = ""
        )

        val hasPermissionList = pipelinePermissionService.getResourceByPermission(
            userId = userId,
            projectId = projectId,
            permission = AuthPermission.EDIT
        )
        val templatePipelineRecords = pipelineTemplateRelatedService.listSimple(
            projectId = projectId,
            templateId = templateId,
            pipelineName = pipelineName,
            updater = updater,
            instanceTypeEnum = PipelineInstanceTypeEnum.CONSTRAINT,
            limit = limit,
            offset = offset
        )
        val count = pipelineTemplateRelatedService.countSimple(
            projectId = projectId,
            templateId = templateId,
            pipelineName = pipelineName,
            updater = updater,
            instanceTypeEnum = PipelineInstanceTypeEnum.CONSTRAINT
        )

        val instanceBaseIds = templateInstanceBaseDao.list(
            dslContext = dslContext,
            projectId = projectId,
            excludeStatusList = listOf(TemplateInstanceStatus.SUCCESS.name),
            type = TemplateInstanceType.UPDATE
        ).map { it.baseId }

        val templateInstanceItems = templateInstanceItemDao.listTemplateInstanceItemByBaseIds(
            dslContext = dslContext,
            projectId = projectId,
            baseIds = instanceBaseIds,
            excludeStatusList = listOf(TemplateInstanceStatus.SUCCESS.name)
        )
        val pipeline2PacSettings = pipelineSettingVersionDao.listPacSettings(
            dslContext = dslContext,
            projectId = projectId,
            pipelineIds = templatePipelineRecords.map { it.pipelineId }
        )
        val results = templatePipelineRecords.map {
            val templateInstanceItem = templateInstanceItems.firstOrNull { item -> item.pipelineId == it.pipelineId }
            val status = generateTemplatePipelineStatus(
                templateInstanceItem = templateInstanceItem,
                templatePipelineId = it.pipelineId,
                templatePipelineVersion = it.version,
                templateReleasedVersion = templateReleasedVersion,
            )
            val enabledPac = pipeline2PacSettings[it.pipelineId]?.enable ?: false
            PipelineTemplateRelatedResp(
                templateId = it.templateId,
                versionName = it.versionName,
                version = it.version,
                pipelineId = it.pipelineId,
                pipelineName = it.pipelineName,
                updateTime = it.updatedTime,
                hasPermission = hasPermissionList.contains(it.pipelineId),
                status = status,
                enabledPac = enabledPac,
                instanceErrorInfo = it.instanceErrorInfo,
                updater = it.updater
            )
        }
        return SQLPage(
            count = count.toLong(),
            records = results
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateInstanceFacadeService::class.java)
    }
}
