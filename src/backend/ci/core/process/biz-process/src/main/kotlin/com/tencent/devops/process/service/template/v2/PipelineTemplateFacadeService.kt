package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.check.Preconditions
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.pipeline.enums.CodeTargetAction
import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.pipeline.PipelineYamlFileInfo
import com.tencent.devops.process.pojo.setting.PipelineVersionSimple
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PTemplateModelTransferResult
import com.tencent.devops.process.pojo.template.v2.PTemplateTransferBody
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateBranchPushReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCompareResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCopyCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCustomCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDetailsResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftReleaseReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftRollbackReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftSaveReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateMarketCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.TemplatePrefetchReleaseResult
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionManager
import com.tencent.devops.process.util.FileExportUtil
import com.tencent.devops.process.yaml.transfer.PipelineTransferException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.ws.rs.core.Response

/**
 * 流水线模版门面类
 */
@Service
class PipelineTemplateFacadeService @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplatePermissionService: PipelineTemplatePermissionService,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val pipelineTemplateRelatedService: PipelineTemplateRelatedService,
    private val pipelineTemplateTransactionService: PipelineTemplateTransactionService,
    private val pipelineTemplateVersionManager: PipelineTemplateVersionManager,
    private val pipelineTemplateGenerator: PipelineTemplateGenerator
) {
    fun create(
        userId: String,
        projectId: String,
        request: PipelineTemplateCustomCreateReq
    ): DeployTemplateResult {
        logger.info("$userId create template in project $projectId by $request ,body is $request")
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            request = request
        )
    }

    fun createByMarket(
        userId: String,
        projectId: String,
        request: PipelineTemplateMarketCreateReq
    ): DeployTemplateResult {
        logger.info("$userId create template in project $projectId by market ,body is $request")
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            request = request
        )
    }

    fun copy(
        userId: String,
        projectId: String,
        request: PipelineTemplateCopyCreateReq
    ): DeployTemplateResult {
        logger.info("$userId create template in project $projectId by copy ,body is $request")
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            request = request
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

        if (templateInfo.mode == TemplateType.CUSTOMIZE && templateInfo.storeFlag) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.TEMPLATE_CAN_NOT_DELETE_WHEN_PUBLISH
            )
        }
        val isExistInstalledTemplate = pipelineTemplateInfoService.count(
            PipelineTemplateCommonCondition(
                mode = TemplateType.CONSTRAINT,
                srcTemplateProjectId = projectId,
                srcTemplateId = templateId
            )
        ) > 0
        if (templateInfo.mode == TemplateType.CUSTOMIZE && isExistInstalledTemplate) {
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

    /**
     * 保存草稿
     */
    fun saveDraft(
        userId: String,
        projectId: String,
        templateId: String,
        request: PipelineTemplateDraftSaveReq
    ): DeployTemplateResult {
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            templateId = templateId,
            request = request
        )
    }

    fun createYamlTemplate(
        userId: String,
        projectId: String,
        yaml: String,
        yamlFileName: String,
        branchName: String,
        isDefaultBranch: Boolean,
        description: String? = null,
        yamlFileInfo: PipelineYamlFileInfo? = null
    ): DeployTemplateResult {
        val request = PipelineTemplateBranchPushReq(
            yaml = yaml,
            yamlFileName = yamlFileName,
            branchName = branchName,
            isDefaultBranch = isDefaultBranch,
            description = description,
            yamlFileInfo = yamlFileInfo
        )
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            request = request
        )
    }

    fun updateYamlTemplate(
        userId: String,
        projectId: String,
        templateId: String,
        yaml: String,
        yamlFileName: String,
        branchName: String,
        isDefaultBranch: Boolean,
        description: String? = null,
        yamlFileInfo: PipelineYamlFileInfo? = null
    ): DeployTemplateResult {
        val request = PipelineTemplateBranchPushReq(
            yaml = yaml,
            yamlFileName = yamlFileName,
            branchName = branchName,
            isDefaultBranch = isDefaultBranch,
            description = description,
            yamlFileInfo = yamlFileInfo
        )
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            templateId = templateId,
            request = request
        )
    }

    fun preFetchDraftVersion(
        projectId: String,
        templateId: String,
        version: Long,
        enablePac: Boolean,
        targetAction: CodeTargetAction?,
        targetBranch: String?
    ): TemplatePrefetchReleaseResult {
        val draftResource = pipelineTemplateResourceService.get(
            projectId = projectId, templateId = templateId, version = version
        )
        if (draftResource.status != VersionStatus.COMMITTING) {
            throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
        }
        val templateSetting = pipelineTemplateSettingService.get(
            projectId = projectId, templateId = templateId, settingVersion = draftResource.settingVersion
        )
        val resourceOnlyVersion = pipelineTemplateGenerator.generateReleaseDraftVersion(
            projectId = projectId,
            templateId = templateId,
            draftResource = draftResource,
            draftSetting = templateSetting,
            enablePac = enablePac,
            targetAction = targetAction,
            targetBranch = targetBranch
        ).second
        return TemplatePrefetchReleaseResult(
            templateId = templateId,
            templateName = templateSetting.pipelineName,
            version = resourceOnlyVersion.version,
            number = resourceOnlyVersion.number,
            newVersionNum = resourceOnlyVersion.versionNum,
            newVersionName = resourceOnlyVersion.versionName!!,
        )
    }

    /**
     * 发布草稿
     */
    fun releaseDraft(
        userId: String,
        projectId: String,
        templateId: String,
        version: Long,
        request: PipelineTemplateDraftReleaseReq
    ): DeployTemplateResult {
        logger.info("release draft version|projectId:$projectId|templateId:$templateId|version:$version")
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            templateId = templateId,
            version = version,
            request = request
        )
    }

    /**
     * 回滚草稿到指定版本
     */
    fun rollbackDraft(
        userId: String,
        projectId: String,
        templateId: String,
        version: Long
    ): DeployTemplateResult {
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            templateId = templateId,
            version = version,
            request = PipelineTemplateDraftRollbackReq()
        )
    }

    /**
     * 删除模版版本
     */
    fun deleteVersion(
        userId: String,
        projectId: String,
        templateId: String,
        version: Long
    ) {
        pipelineTemplateVersionManager.deleteVersion(
            userId = userId,
            projectId = projectId,
            templateId = templateId,
            version = version
        )
    }

    /**
     * 将分支版本置为不活跃
     */
    fun inactiveBranch(
        userId: String,
        projectId: String,
        templateId: String,
        branch: String
    ) {
        pipelineTemplateVersionManager.inactiveBranch(
            userId = userId,
            projectId = projectId,
            templateId = templateId,
            branch = branch
        )
    }

    // 获取模板列表
    fun listTemplateInfos(
        userId: String,
        commonCondition: PipelineTemplateCommonCondition
    ): SQLPage<PipelineTemplateInfo> {
        logger.info("list template infos {}|{}", userId, commonCondition)
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
        val (yamlSupported, yamlPreview, msg) = try {
            val yaml = templateResource.yaml ?: pipelineTemplateGenerator.transfer(
                userId = templateResource.creator,
                projectId = templateResource.projectId,
                storageType = PipelineStorageType.MODEL,
                templateType = templateResource.type,
                templateModel = templateResource.model,
                templateSetting = setting,
                yaml = null
            ).yamlWithVersion?.yamlStr ?: ""
            val response = pipelineTemplateGenerator.buildPreView(yaml)
            Triple(true, response, null)
        } catch (e: PipelineTransferException) {
            Triple(
                first = false,
                second = null,
                third = I18nUtil.getCodeLanMessage(
                    messageCode = e.errorCode,
                    params = e.params,
                    language = I18nUtil.getLanguage(I18nUtil.getRequestUserId()),
                    defaultMessage = e.defaultMessage
                )
            )
        }
        return PipelineTemplateDetailsResponse(
            resource = templateResource,
            setting = setting,
            yamlSupported = yamlSupported,
            yamlPreview = yamlPreview,
            yamlInvalidMsg = msg
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
            projectId = projectId,
            templateId = templateId
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

    fun transfer(
        userId: String,
        projectId: String,
        templateId: String?,
        storageType: PipelineStorageType,
        body: PTemplateTransferBody
    ): PTemplateModelTransferResult {
        return if (storageType == PipelineStorageType.YAML) {
            Preconditions.checkNotNull(templateId, "The template id must not be null")
            val templateInfo = pipelineTemplateInfoService.get(
                projectId = projectId,
                templateId = templateId!!
            )
            pipelineTemplateGenerator.transfer(
                userId = userId,
                projectId = projectId,
                storageType = storageType,
                templateType = templateInfo.type,
                templateModel = body.templateModel,
                templateSetting = body.templateSetting,
                yaml = body.yaml
            )
        } else {
            pipelineTemplateGenerator.transfer(
                userId = userId,
                projectId = projectId,
                storageType = storageType,
                templateType = null,
                templateModel = null,
                templateSetting = null,
                yaml = body.yaml
            )
        }
    }

    fun exportTemplate(
        userId: String,
        projectId: String,
        templateId: String,
        version: Long?
    ): Response {
        val templateInfo = pipelineTemplateInfoService.get(
            projectId = projectId,
            templateId = templateId
        )
        val templateResource = version?.let {
            pipelineTemplateResourceService.get(
                projectId = projectId,
                templateId = templateId,
                version = version
            )
        } ?: pipelineTemplateResourceService.getLatestVersionResource(
            projectId = projectId,
            templateId = templateId
        ) ?: throw ErrorCodeException(errorCode = "")
        val setting = pipelineTemplateSettingService.get(
            projectId = projectId,
            templateId = templateId,
            settingVersion = templateResource.settingVersion
        )

        val yamlStr = pipelineTemplateGenerator.transfer(
            userId = userId,
            projectId = projectId,
            storageType = PipelineStorageType.MODEL,
            templateType = templateResource.type,
            templateModel = templateResource.model,
            templateSetting = setting,
            yaml = templateResource.yaml
        ).yamlWithVersion?.yamlStr
        if (yamlStr == null) {
            throw ErrorCodeException(errorCode = "")
        }
        return FileExportUtil.exportStringToFile(
            content = yamlStr,
            fileName = "${templateInfo.name}.yaml"
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateFacadeService::class.java)
    }
}
