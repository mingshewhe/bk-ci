package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_SOURCE_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.process.pojo.setting.PipelineVersionSimple
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateBasicCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCompareResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCustomCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDetailsResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftSaveReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateMarketCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplatePermission
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRepositoryCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceUpdateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSetting
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateYamlCreateReq
import com.tencent.devops.project.api.service.ServiceAllocIdResource
import com.tencent.devops.store.api.common.ServiceStoreResource
import com.tencent.devops.store.api.template.ServiceTemplateResource
import com.tencent.devops.store.pojo.common.enums.StoreTypeEnum
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 流水线模版门面类
 */
@Service
class PipelineTemplateFacadeService @Autowired constructor(
    private val pipelineTemplateCommonService: PipelineTemplateCommonService,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateModelParser: PipelineTemplateModelParser,
    private val pipelineTemplatePACService: PipelineTemplatePACService,
    private val pipelineTemplatePermissionService: PipelineTemplatePermissionService,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val dslContext: DSLContext,
    private val client: Client
) {
    fun createTemplate(userId: String, request: PipelineTemplateBasicCreateReq): String {
        logger.info("$userId create template in project ${request.projectId} by ${request.source} ,body is {}", request)
        val templateId = request.generateId()
        when (request) {
            is PipelineTemplateCustomCreateReq -> createByCustom(userId, request)
            is PipelineTemplateMarketCreateReq -> createByMarket(userId, request)
            is PipelineTemplateYamlCreateReq -> createByYaml(userId, request)
            is PipelineTemplateRepositoryCreateReq -> createByRepository(userId, request)
            else -> {}
        }
        return templateId
    }

    private fun createByCustom(userId: String, request: PipelineTemplateCustomCreateReq) {
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = request.projectId,
            name = request.name
        )
        val templateId = request.id!!
        val version = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!
        val (setting, settingVersion) = getDefaultSettingAndVersion(
            type = request.type,
            projectId = request.projectId,
            templateId = templateId,
            creator = userId
        )
        val defaultTemplateModel = pipelineTemplateModelParser.getDefaultTemplateModel(
            name = request.name,
            type = request.type,
            userId = userId
        )
        val pipelineTemplateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = request.projectId,
            name = request.name,
            desc = request.desc,
            mode = TemplateType.CUSTOMIZE.name,
            type = request.type,
            enablePac = false,
            source = PipelineTemplateSource.CUSTOM,
            storeFlag = false,
            creator = userId,
            latestVersionStatus = VersionStatus.COMMITTING
        )

        val pipelineTemplateResource = PipelineTemplateResource(
            projectId = request.projectId,
            templateId = templateId,
            name = request.name,
            desc = request.desc,
            type = request.type,
            settingVersion = settingVersion,
            version = version,
            number = 1,
            model = defaultTemplateModel,
            yaml = null,
            creator = userId,
            status = VersionStatus.COMMITTING
        )

        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = request.projectId,
            id = templateId,
            name = request.name,
            creator = userId
        )
        saveTemplate(
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateResource = pipelineTemplateResource,
            pipelineTemplateSetting = setting,
            pipelineTemplatePermission = pipelineTemplatePermission
        )
    }

    private fun createByMarket(userId: String, request: PipelineTemplateMarketCreateReq) {
        val marketTemplateDetails = client.get(ServiceTemplateResource::class).getTemplateDetailByCode(
            userId = userId,
            templateCode = request.marketTemplateId
        ).data ?: throw ErrorCodeException(errorCode = ERROR_SOURCE_TEMPLATE_NOT_EXISTS)
        val marketTemplateInfo = pipelineTemplateInfoService.get(
            templateId = request.marketTemplateId
        )
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = request.projectId,
            name = marketTemplateInfo.name
        )
        val marketTemplateResource = pipelineTemplateResourceService.get(
            PipelineTemplateResourceCommonCondition(
                projectId = request.marketTemplateProjectId,
                templateId = request.marketTemplateId,
                version = request.marketTemplateVersion
            )
        )
        // todo 从研发商店中获取
        val type = PipelineTemplateType.PIPELINE

        val templateId = request.id!!
        val version = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!

        val (setting, settingVersion) = getDefaultSettingAndVersion(
            type = type,
            projectId = request.projectId,
            templateId = templateId,
            creator = userId
        )

        val pipelineTemplateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = request.projectId,
            name = marketTemplateInfo.name,
            desc = marketTemplateInfo.desc,
            mode = TemplateType.CONSTRAINT.name,
            type = marketTemplateInfo.type,
            enablePac = false,
            releasedVersion = version,
            releasedVersionName = marketTemplateInfo.releasedVersionName,
            releasedSettingVersion = settingVersion,
            source = PipelineTemplateSource.MARKET,
            storeFlag = true,
            creator = userId,
            srcTemplateProjectId = marketTemplateInfo.projectId,
            srcTemplateId = marketTemplateInfo.id,
            category = marketTemplateDetails.classifyCode,
            logoUrl = marketTemplateDetails.logoUrl,
            latestVersionStatus = VersionStatus.RELEASED
        )
        val templateResource = PipelineTemplateResource(
            projectId = request.projectId,
            templateId = templateId,
            name = marketTemplateInfo.name,
            desc = marketTemplateInfo.desc,
            type = marketTemplateInfo.type,
            settingVersion = settingVersion,
            version = version,
            number = PipelineTemplateConstant.INIT_NUMBER,
            srcTemplateProjectId = marketTemplateInfo.projectId,
            srcTemplateId = marketTemplateInfo.id,
            srcTemplateVersion = marketTemplateResource.version,
            model = marketTemplateResource.model,
            yaml = marketTemplateResource.yaml,
            creator = userId,
            status = VersionStatus.RELEASED
        )
        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = request.projectId,
            id = templateId,
            name = marketTemplateInfo.name,
            creator = userId
        )
        saveTemplate(
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateSetting = setting,
            pipelineTemplatePermission = pipelineTemplatePermission,
            pipelineTemplateResource = templateResource
        )
    }

    private fun createByRepository(userId: String, request: PipelineTemplateRepositoryCreateReq) {
    }

    private fun createByYaml(userId: String, request: PipelineTemplateYamlCreateReq) {
        val version = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!
        // TODO 校验
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = request.projectId,
            name = request.name
        )
        val templateId = request.id!!

        // todo 需要进一步判断是否是流水线模板类型
        val (settingVersion, pipelineTemplateSettingVersion) = if (request.setting != null) {
            val setting = request.setting?.copy(
                templateId = templateId,
                settingVersion = PipelineTemplateConstant.INIT_SETTING_VERSION
            )
            Pair(PipelineTemplateConstant.INIT_SETTING_VERSION, setting)
        } else {
            Pair(null, null)
        }
        val type = PipelineTemplateType.PIPELINE

        val pipelineTemplateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = request.projectId,
            name = request.name,
            desc = request.desc,
            mode = TemplateType.CUSTOMIZE.name,
            type = type,
            enablePac = false,
            source = PipelineTemplateSource.YAML,
            storeFlag = false,
            creator = userId,
            latestVersionStatus = VersionStatus.COMMITTING
        )

        val pipelineTemplateResource = PipelineTemplateResource(
            projectId = request.projectId,
            templateId = templateId,
            name = request.name,
            desc = request.desc,
            settingVersion = settingVersion,
            type = type,
            version = version,
            number = PipelineTemplateConstant.INIT_NUMBER,
            params = request.params,
            model = request.model,
            yaml = request.yaml,
            status = VersionStatus.COMMITTING,
            creator = userId
        )
        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = request.projectId,
            id = templateId,
            name = request.name,
            creator = userId
        )
        saveTemplate(
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateResource = pipelineTemplateResource,
            pipelineTemplateSetting = pipelineTemplateSettingVersion,
            pipelineTemplatePermission = pipelineTemplatePermission
        )
    }

    private fun getDefaultSettingAndVersion(
        type: PipelineTemplateType,
        projectId: String,
        templateId: String,
        creator: String
    ): Pair<PipelineTemplateSetting?, Int?> {
        return if (type == PipelineTemplateType.PIPELINE) {
            val setting = PipelineTemplateSetting.defaultSetting(
                projectId = projectId,
                templateId = templateId,
                creator = creator
            )
            Pair(setting, PipelineTemplateConstant.INIT_SETTING_VERSION)
        } else {
            Pair(null, null)
        }
    }

    private fun saveTemplate(
        pipelineTemplateInfo: PipelineTemplateInfo? = null,
        pipelineTemplateResource: PipelineTemplateResource? = null,
        pipelineTemplateSetting: PipelineTemplateSetting? = null,
        pipelineTemplatePermission: PipelineTemplatePermission? = null
    ) {
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            pipelineTemplateInfo?.let {
                pipelineTemplateInfoService.create(
                    transactionContext = context,
                    pipelineTemplateInfo = pipelineTemplateInfo
                )
            }
            pipelineTemplateResource?.let {
                pipelineTemplateResourceService.create(
                    transactionContext = context,
                    pipelineTemplateResource = pipelineTemplateResource
                )
            }
            pipelineTemplateSetting?.let {
                pipelineTemplateSettingService.create(
                    transactionContext = context,
                    pipelineTemplateSetting = pipelineTemplateSetting
                )
            }
            pipelineTemplatePermission?.let {
                pipelineTemplatePermissionService.createResource(
                    userId = pipelineTemplatePermission.creator,
                    projectId = pipelineTemplatePermission.projectId,
                    templateId = pipelineTemplatePermission.id,
                    templateName = pipelineTemplatePermission.name
                )
            }
        }
    }

    fun deleteTemplate(projectId: String, templateId: String): Boolean {
        logger.info("Start to delete the template $projectId|$templateId")
        val templateInfo = pipelineTemplateInfoService.get(
            projectId = projectId,
            templateId = templateId
        )
        // todo 校验该模板是否已经有实例化的流水线，若有的话，不允许直接删除
        if (templateInfo.mode == TemplateType.CUSTOMIZE.name && templateInfo.storeFlag) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.TEMPLATE_CAN_NOT_DELETE_WHEN_PUBLISH
            )
        }
        val isExistInstalledTemplate = pipelineTemplateInfoService.count(
            PipelineTemplateCommonCondition(
                mode = TemplateType.CONSTRAINT.name,
                srcTemplateProjectId = projectId,
                srcTemplateId = templateId
            )
        ) > 0
        if (templateInfo.mode == TemplateType.CUSTOMIZE.name && isExistInstalledTemplate) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.TEMPLATE_CAN_NOT_DELETE_WHEN_INSTALL
            )
        }
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            // todo 删除模板与流水线实例关联表数据
            pipelineTemplateInfoService.delete(
                transactionContext = context,
                commonCondition = PipelineTemplateCommonCondition(
                    projectId = projectId,
                    templateId = templateId
                )
            )
            pipelineTemplateResourceService.delete(
                transactionContext = context,
                commonCondition = PipelineTemplateResourceCommonCondition(
                    projectId = projectId,
                    templateId = templateId
                )
            )
            pipelineTemplateSettingService.delete(
                transactionContext = context,
                commonCondition = PipelineTemplateSettingCommonCondition(
                    projectId = projectId,
                    templateId = templateId
                )
            )
            if (templateInfo.mode == TemplateType.CONSTRAINT.name) {
                client.get(ServiceStoreResource::class).uninstall(
                    storeCode = templateInfo.srcTemplateId!!,
                    storeType = StoreTypeEnum.TEMPLATE,
                    projectCode = templateInfo.projectId
                )
            }
            pipelineTemplatePermissionService.deleteResource(
                projectId = projectId,
                templateId = templateId
            )
        }
        return true
    }

    fun saveDraft(userId: String, request: PipelineTemplateDraftSaveReq): Long {
        logger.info("save template draft {}|{}|{}", request.projectId, userId, request)
        val templateInfo = pipelineTemplateInfoService.get(
            projectId = request.projectId,
            templateId = request.templateId
        )
        // todo yaml方式 或者 model方式
        var newYaml = ""

        val isExistDraft = pipelineTemplateResourceService.getDraftVersionResource(
            projectId = request.projectId,
            templateId = request.templateId
        ) != null

        // todo 检查模型，模板参数，配置检查

        val version = if (isExistDraft) {
            // 若存在草稿，则在原草稿版本上更新
            val draftVersionResource = pipelineTemplateResourceService.get(
                PipelineTemplateResourceCommonCondition(
                    projectId = request.projectId,
                    templateId = request.templateId,
                    status = VersionStatus.COMMITTING
                )
            )
            val templateResourceUpdateInfo = PipelineTemplateResourceUpdateInfo(
                name = request.name,
                desc = request.desc,
                params = request.params,
                model = request.model!!,
                yaml = request.yaml,
                updater = userId,
                sortWeight = PipelineTemplateConstant.COMMITTING_STATUS_VERSION_SORT_WIGHT
            )
            dslContext.transaction { configuration ->
                val context = DSL.using(configuration)
                pipelineTemplateResourceService.update(
                    transactionContext = context,
                    record = templateResourceUpdateInfo,
                    commonCondition = PipelineTemplateResourceCommonCondition(
                        projectId = draftVersionResource.projectId,
                        templateId = draftVersionResource.templateId,
                        version = draftVersionResource.version
                    )
                )
                if (templateInfo.type == PipelineTemplateType.PIPELINE && request.templateSetting != null) {
                    pipelineTemplateSettingService.create(
                        transactionContext = dslContext,
                        pipelineTemplateSetting = request.templateSetting!!.copy(
                            templateId = request.templateId,
                            settingVersion = draftVersionResource.settingVersion!!,
                            creator = draftVersionResource.creator
                        )
                    )
                }
            }
            draftVersionResource.version
        } else {
            // 若不存在草稿版本，则基于版本进行创建新版本草稿
            val latestTemplateResource = pipelineTemplateResourceService.getLatestReleasedResource(
                projectId = request.projectId,
                templateId = request.templateId
            )
            val baseVersionResource = pipelineTemplateResourceService.get(
                projectId = request.projectId,
                templateId = request.templateId,
                version = request.baseVersion
            )

            val pipelineTemplateResource = PipelineTemplateResource(
                projectId = request.projectId,
                templateId = request.templateId,
                name = request.name,
                desc = request.desc,
                type = templateInfo.type,
                settingVersion = latestTemplateResource.settingVersion?.let { it + 1 },
                version = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!,
                number = latestTemplateResource.number + 1,
                baseVersion = baseVersionResource.version,
                params = request.params,
                model = request.model,
                yaml = request.yaml,
                status = VersionStatus.COMMITTING,
                creator = userId
            )
            val pipelineTemplateSetting = if (templateInfo.type == PipelineTemplateType.PIPELINE) {
                request.templateSetting?.let { setting ->
                    latestTemplateResource.settingVersion?.let { currentVersion ->
                        setting.copy(
                            templateId = templateInfo.id,
                            settingVersion = currentVersion + 1
                        )
                    }
                }
            } else {
                null
            }
            saveTemplate(
                pipelineTemplateSetting = pipelineTemplateSetting,
                pipelineTemplateResource = pipelineTemplateResource
            )
            pipelineTemplateResource.version
        }
        return version
    }

    // 获取模板列表
    fun listTemplateInfos(
        userId: String,
        commonCondition: PipelineTemplateCommonCondition
    ): SQLPage<PipelineTemplateInfo> {
        val projectId = commonCondition.projectId!!
        val enableTemplatePermissionManage = pipelineTemplatePermissionService.enableTemplatePermissionManage(projectId)

        val (count, templateInfoWithPermission) = if (enableTemplatePermissionManage) {
            val permission2TemplatesMap = pipelineTemplatePermissionService.getResourcesByPermission(
                userId = userId,
                projectId = projectId,
                permissions = setOf(
                    AuthPermission.VIEW,
                    AuthPermission.LIST,
                    AuthPermission.DELETE,
                    AuthPermission.EDIT
                )
            )
            val templatesWithListPermIds = permission2TemplatesMap[AuthPermission.LIST] ?: return SQLPage(
                count = 0L,
                records = emptyList()
            )

            val queryCondition = commonCondition.copy(filterTemplateIds = templatesWithListPermIds)
            val templateInfoList = pipelineTemplateInfoService.list(queryCondition)
            val count = pipelineTemplateInfoService.count(queryCondition)

            val templateInfoWithPermission = templateInfoList.map { templateInfo ->
                templateInfo.copy(
                    canView = permission2TemplatesMap[AuthPermission.VIEW]?.contains(templateInfo.id) ?: false,
                    canEdit = permission2TemplatesMap[AuthPermission.EDIT]?.contains(templateInfo.id) ?: false,
                    canDelete = permission2TemplatesMap[AuthPermission.DELETE]?.contains(templateInfo.id) ?: false
                )
            }
            Pair(count.toLong(), templateInfoWithPermission)
        } else {
            val templateInfoList = pipelineTemplateInfoService.list(commonCondition)
            val count = pipelineTemplateInfoService.count(commonCondition)
            val isProjectManager = pipelinePermissionService.checkProjectManager(userId, projectId)

            val templateInfoWithPermission = templateInfoList.map { templateInfo ->
                templateInfo.copy(
                    canView = isProjectManager,
                    canEdit = isProjectManager,
                    canDelete = isProjectManager
                )
            }
            Pair(count.toLong(), templateInfoWithPermission)
        }

        return SQLPage(
            count = count,
            records = templateInfoWithPermission
        )
    }

    // 查看模板详情
    fun getTemplateDetails(
        projectId: String,
        templateId: String,
        version: Long
    ): PipelineTemplateDetailsResponse {
        val templateResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = templateId,
            version = version
        )
        val setting = templateResource.settingVersion?.let {
            pipelineTemplateSettingService.get(
                projectId = projectId,
                templateId = templateId,
                settingVersion = it
            )
        }
        return PipelineTemplateDetailsResponse(
            resource = templateResource,
            setting = setting
        )
    }

    fun getTemplateInfo(
        userId: String,
        projectId: String,
        templateId: String
    ): PipelineTemplateInfoResponse {
        val basicInfo = pipelineTemplateInfoService.get(projectId, templateId)
        val draftVersionResource = pipelineTemplateResourceService.getDraftVersionResource(projectId, templateId)
        val draftBaseVersionResource = pipelineTemplateResourceService.getDraftBaseVersionResource(
            projectId = projectId, templateId = templateId
        )
        // 配合前端的展示需要，version有以下几种情况的返回值：
        // 1 发布过且有草稿：version取草稿的版本号
        // 2 发布过且有分支版本：version取最新正式的版本号
        // 3 未发布过仅有草稿版本：version取草稿的版本号
        // 4 未发布过仅有分支版本：version取最新的分支版本号
        var versionName = basicInfo.releasedVersionName
        val version = when (basicInfo.latestVersionStatus) {
            VersionStatus.COMMITTING -> {
                draftVersionResource?.version
            }

            VersionStatus.BRANCH -> {
                val latestBranchResource = pipelineTemplateResourceService.getLatestBranchResource(
                    projectId = projectId, templateId = templateId
                )
                versionName = latestBranchResource?.versionName
                latestBranchResource?.version
            }

            else -> {
                draftVersionResource?.version
            }
        } ?: basicInfo.releasedVersion

        return PipelineTemplateInfoResponse(
            id = basicInfo.id,
            projectId = basicInfo.projectId,
            name = basicInfo.name,
            desc = basicInfo.desc,
            mode = basicInfo.mode,
            category = basicInfo.category,
            type = basicInfo.type,
            logoUrl = basicInfo.logoUrl,
            enablePac = basicInfo.enablePac,
            source = basicInfo.source,
            sourceName = basicInfo.sourceName,
            storeFlag = basicInfo.storeFlag,
            srcTemplateId = basicInfo.srcTemplateId,
            srcTemplateProjectId = basicInfo.srcTemplateProjectId,
            debugPipelineCount = basicInfo.debugPipelineCount,
            instancePipelineCount = basicInfo.instancePipelineCount,
            creator = basicInfo.creator,
            updater = basicInfo.updater,
            createdTime = basicInfo.createdTime,
            updateTime = basicInfo.updateTime,
            canRelease = draftVersionResource?.model != null,
            version = version,
            versionName = versionName,
            baseVersion = draftBaseVersionResource?.version,
            baseVersionName = draftBaseVersionResource?.versionName,
            baseVersionStatus = draftBaseVersionResource?.status,
            releaseVersion = basicInfo.releasedVersion,
            releaseVersionName = basicInfo.releasedVersionName,
            latestVersionStatus = basicInfo.latestVersionStatus,
            pipelineAsCodeSettings = PipelineAsCodeSettings(
                enable = basicInfo.enablePac
            ),
            // todo 补充
            yamlInfo = null,
            yamlExist = false
        )
    }

    // 查看全部模板版本历史
    fun getTemplateVersions(
        commonCondition: PipelineTemplateResourceCommonCondition
    ): Page<PipelineVersionSimple> {
        val records = pipelineTemplateResourceService.getTemplateVersions(commonCondition)
        val count = pipelineTemplateResourceService.count(commonCondition)
        return Page(
            page = commonCondition.page ?: -1,
            pageSize = commonCondition.pageSize ?: -1,
            records = records,
            count = count.toLong()
        )
    }

    // 模板版本对比
    fun compare(
        projectId: String,
        templateId: String,
        baseVersion: Long,
        comparedVersion: Long
    ): PipelineTemplateCompareResponse {
        val templateInfo = pipelineTemplateInfoService.get(
            projectId = projectId,
            templateId = templateId
        )
        val baseVersionResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = templateId,
            version = baseVersion
        )
        val comparedVersionResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = templateId,
            version = comparedVersion
        )
        return PipelineTemplateCompareResponse(
            baseVersionResource = baseVersionResource,
            comparedVersionResource = comparedVersionResource
        )
    }

    // 复制模板
    fun copy(
        userId: String,
        projectId: String,
        srcTemplateId: String,
        copySetting: Boolean,
        name: String
    ): String {
        val srcTemplateInfo = pipelineTemplateInfoService.get(
            projectId = projectId,
            templateId = srcTemplateId
        )
        // todo 校验是否有正式版本
        if (srcTemplateInfo.releasedVersion == null) {
            throw ErrorCodeException(errorCode = "")
        }
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = projectId,
            name = name
        )
        // todo 校验是否有正式版本
        val srcTemplateResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = srcTemplateId,
            version = srcTemplateInfo.releasedVersion!!
        )
        val templateId = UUIDUtil.generate()
        val version = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!

        val (settingVersion, setting) = if (copySetting && srcTemplateInfo.type == PipelineTemplateType.PIPELINE) {
            val srcTemplateSetting = pipelineTemplateSettingService.get(
                projectId = projectId,
                templateId = srcTemplateId,
                settingVersion = srcTemplateInfo.releasedSettingVersion!!
            ) ?: throw ErrorCodeException(errorCode = ERROR_SOURCE_TEMPLATE_NOT_EXISTS)
            val setting = srcTemplateSetting.copy(
                templateId = templateId,
                projectId = projectId,
                settingVersion = 1,
                creator = userId
            )
            Pair(1, setting)
        } else {
            Pair(null, null)
        }

        val templateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = projectId,
            name = name,
            desc = srcTemplateInfo.desc,
            mode = srcTemplateInfo.mode,
            category = srcTemplateInfo.category,
            type = srcTemplateInfo.type,
            logoUrl = srcTemplateInfo.logoUrl,
            enablePac = srcTemplateInfo.enablePac,
            releasedVersion = version,
            releasedVersionName = "V1(P1.T1.1)",
            releasedSettingVersion = settingVersion,
            source = srcTemplateInfo.source,
            storeFlag = srcTemplateInfo.storeFlag,
            creator = userId,
            latestVersionStatus = VersionStatus.RELEASED
        )

        val templateResource = srcTemplateResource.copy(
            projectId = projectId,
            templateId = templateId,
            name = name,
            settingVersion = settingVersion,
            version = version,
            number = 1,
            versionName = "V1(P1.T1.1)",
            versionNum = 1,
            modelVersion = 1,
            triggerVersion = 1,
            creator = userId,
            releaseTime = LocalDateTime.now()
        )

        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = projectId,
            id = templateId,
            name = name,
            creator = userId
        )
        saveTemplate(
            pipelineTemplateInfo = templateInfo,
            pipelineTemplateResource = templateResource,
            pipelineTemplateSetting = setting,
            pipelineTemplatePermission = pipelineTemplatePermission
        )
        return templateId
    }

    // 发布模板
    // 流水线模板检查
    // 回滚版本

    companion object {
        private const val TEMPLATE_BIZ_TAG_NAME = "TEMPLATE"
        private val logger = LoggerFactory.getLogger(PipelineTemplateFacadeService::class.java)
    }
}
