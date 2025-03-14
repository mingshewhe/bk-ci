package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_SOURCE_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.setting.PipelineVersionSimple
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCompareResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCustomCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDetailsResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftSaveReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateMarketCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplatePermission
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionManager
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
    private val pipelineTemplateGenerator: PipelineTemplateGenerator,
    private val pipelineTemplatePermissionService: PipelineTemplatePermissionService,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val dslContext: DSLContext,
    private val client: Client,
    private val pipelineTemplateRelatedService: PipelineTemplateRelatedService,
    private val pipelineTemplateTransactionService: PipelineTemplateTransactionService,
    private val pipelineTemplateVersionManager: PipelineTemplateVersionManager
) {
    fun create(userId: String, projectId: String, request: PipelineTemplateCustomCreateReq): DeployTemplateResult {
        logger.info("$userId create template in project $projectId by ${request} ,body is $request")
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            request = request
        )
    }

    private fun createByMarket(userId: String, projectId: String, request: PipelineTemplateMarketCreateReq) {
        val marketTemplateDetails = client.get(ServiceTemplateResource::class).getTemplateDetailByCode(
            userId = userId,
            templateCode = request.marketTemplateId
        ).data ?: throw ErrorCodeException(errorCode = ERROR_SOURCE_TEMPLATE_NOT_EXISTS)
        val marketTemplateInfo = pipelineTemplateInfoService.get(
            templateId = request.marketTemplateId
        )
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = projectId,
            name = marketTemplateInfo.name
        )
        val marketTemplateResource = pipelineTemplateResourceService.get(
            projectId = request.marketTemplateProjectId,
            templateId = request.marketTemplateId,
            version = request.marketTemplateVersion
        )
        val templateId = pipelineTemplateGenerator.generateTemplateId()
        val version = pipelineTemplateGenerator.generateTemplateVersion()

        val setting = pipelineTemplateGenerator.getDefaultSetting(
            type = marketTemplateResource.type,
            projectId = projectId,
            templateId = templateId,
            creator = userId,
            templateName = marketTemplateInfo.name,
            desc = marketTemplateInfo.desc
        )

        val pipelineTemplateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = projectId,
            name = marketTemplateInfo.name,
            desc = marketTemplateInfo.desc,
            mode = TemplateType.CONSTRAINT.name,
            type = marketTemplateInfo.type,
            enablePac = false,
            releasedVersion = version,
            releasedVersionName = marketTemplateInfo.releasedVersionName,
            releasedSettingVersion = PipelineTemplateConstant.INIT_VERSION,
            source = PipelineTemplateSource.MARKET,
            storeFlag = false,
            creator = userId,
            srcTemplateProjectId = marketTemplateInfo.projectId,
            srcTemplateId = marketTemplateInfo.id,
            category = marketTemplateDetails.classifyCode,
            logoUrl = marketTemplateDetails.logoUrl,
            latestVersionStatus = VersionStatus.RELEASED
        )
        val templateResource = PipelineTemplateResource(
            projectId = projectId,
            templateId = templateId,
            type = marketTemplateInfo.type,
            settingVersion = PipelineTemplateConstant.INIT_VERSION,
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
            projectId = projectId,
            id = templateId,
            name = marketTemplateInfo.name,
            creator = userId
        )
        pipelineTemplateTransactionService.createTemplateAndPermission(
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateSetting = setting,
            pipelineTemplatePermission = pipelineTemplatePermission,
            pipelineTemplateResource = templateResource
        )
    }

    fun deleteTemplate(projectId: String, templateId: String): Boolean {
        logger.info("Start to delete the template $projectId|$templateId")
        val templateInfo = pipelineTemplateInfoService.get(
            projectId = projectId,
            templateId = templateId
        )
        val isTemplateExistInstances = pipelineTemplateRelatedService.isTemplateExistInstances(
            projectId = projectId,
            templateId = templateId
        )

        if (isTemplateExistInstances) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.TEMPLATE_CAN_NOT_DELETE_WHEN_HAVE_INSTANCE
            )
        }

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
        pipelineTemplateTransactionService.deleteTemplate(
            projectId = projectId,
            templateId = templateId
        )
        return true
    }

    fun saveDraft(userId: String, projectId: String, request: PipelineTemplateDraftSaveReq): DeployTemplateResult {
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            request = request
        )
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
        val setting = pipelineTemplateSettingService.get(
            projectId = projectId,
            templateId = templateId,
            settingVersion = templateResource.settingVersion
        )

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
        val draftVersionResource = pipelineTemplateResourceService.getDraftVersionResource(
            projectId = projectId,
            templateId = templateId
        )
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
        pipelineTemplateInfoService.get(
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
        if (srcTemplateInfo.latestVersionStatus != VersionStatus.RELEASED) {
            // todo 错误码
            throw ErrorCodeException(errorCode = "")
        }

        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = projectId,
            name = name
        )
        val srcTemplateResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = srcTemplateId,
            version = srcTemplateInfo.releasedVersion!!
        )
        val templateId = UUIDUtil.generate()
        val version = pipelineTemplateGenerator.generateTemplateVersion()

        val setting = if (copySetting) {
            val srcTemplateSetting = pipelineTemplateSettingService.get(
                projectId = projectId,
                templateId = srcTemplateId,
                settingVersion = srcTemplateInfo.releasedSettingVersion!!
            )
            srcTemplateSetting.copy(
                pipelineId = templateId,
                projectId = projectId,
                pipelineName = name,
                version = PipelineTemplateConstant.INIT_VERSION,
                creator = userId
            )
        } else {
            pipelineTemplateGenerator.getDefaultSetting(
                type = srcTemplateResource.type,
                projectId = projectId,
                templateId = templateId,
                templateName = name,
                desc = srcTemplateInfo.desc,
                creator = userId
            )
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
            releasedSettingVersion = PipelineTemplateConstant.INIT_VERSION,
            source = srcTemplateInfo.source,
            storeFlag = srcTemplateInfo.storeFlag,
            creator = userId,
            latestVersionStatus = VersionStatus.RELEASED
        )

        val templateResource = srcTemplateResource.copy(
            projectId = projectId,
            templateId = templateId,
            settingVersion = PipelineTemplateConstant.INIT_VERSION,
            version = version,
            number = PipelineTemplateConstant.INIT_NUMBER,
            versionName = "V1(P1.T1.1)",
            versionNum = 1,
            pipelineVersion = 1,
            triggerVersion = 1,
            creator = userId,
            releaseTime = LocalDateTime.now().timestampmilli()
        )

        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = projectId,
            id = templateId,
            name = name,
            creator = userId
        )
        pipelineTemplateTransactionService.createTemplateAndPermission(
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
        private val logger = LoggerFactory.getLogger(PipelineTemplateFacadeService::class.java)
    }
}
