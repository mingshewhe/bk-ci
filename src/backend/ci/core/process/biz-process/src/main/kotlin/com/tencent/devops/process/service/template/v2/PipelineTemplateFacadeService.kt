package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.model.SQLLimit
import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.CodeTargetAction
import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.engine.dao.PipelineOperationLogDao
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.PipelineOperationDetail
import com.tencent.devops.common.pipeline.template.PipelineTemplateType
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.pipeline.PipelineYamlFileInfo
import com.tencent.devops.process.pojo.setting.PipelineVersionSimple
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
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoV2
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateMarketCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.TemplatePrefetchReleaseResult
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionManager
import com.tencent.devops.process.util.FileExportUtil
import com.tencent.devops.process.yaml.PipelineYamlFacadeService
import com.tencent.devops.process.yaml.transfer.PipelineTransferException
import jakarta.ws.rs.core.Response
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

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
    private val pipelineTemplateVersionManager: PipelineTemplateVersionManager,
    private val pipelineTemplateGenerator: PipelineTemplateGenerator,
    private val pipelineOperationLogDao: PipelineOperationLogDao,
    private val dslContext: DSLContext,
    private val pipelineYamlFacadeService: PipelineYamlFacadeService,
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

    /**
     * 保存草稿
     */
    fun saveDraft(
        userId: String,
        projectId: String,
        templateId: String?,
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
        repoHashId: String?,
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
            repoHashId = repoHashId,
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
     * 删除模版所有版本
     */
    fun deleteTemplate(
        userId: String,
        projectId: String,
        templateId: String
    ): Boolean {
        pipelineTemplateVersionManager.deleteAllVersions(
            userId = userId,
            projectId = projectId,
            templateId = templateId
        )
        return true
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
    ): SQLPage<PipelineTemplateInfoV2> {
        logger.info("list template infos {}|{}", userId, commonCondition)
        val projectId = commonCondition.projectId!!
        val enableTemplatePermissionManage = pipelineTemplatePermissionService.enableTemplatePermissionManage(projectId)

        val (count, templateInfos) = if (enableTemplatePermissionManage) {
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
            records = templateInfos
        )
    }

    // 查看模板详情
    fun getTemplateDetails(
        projectId: String,
        templateId: String,
        version: Long?
    ): PipelineTemplateDetailsResponse {
        val templateResource = if (version == null) {
            pipelineTemplateResourceService.getLatestReleasedResource(
                projectId = projectId,
                templateId = templateId
            )
        } else {
            pipelineTemplateResourceService.get(
                projectId = projectId,
                templateId = templateId,
                version = version
            )
        } ?: throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
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
                params = templateResource.params,
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
        val buildNo = if (templateResource.type == PipelineTemplateType.PIPELINE) {
            (templateResource.model as Model).getTriggerContainer().buildNo
        } else {
            null
        }
        return PipelineTemplateDetailsResponse(
            resource = templateResource,
            setting = setting,
            buildNo = buildNo,
            params = templateResource.params,
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
        val draftResource = pipelineTemplateResourceService.getDraftVersionResource(
            projectId = projectId,
            templateId = templateId
        )
        val releaseResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = templateId,
            version = basicInfo.releasedVersion!!
        )
        val baseResource = draftResource?.baseVersion?.let {
            pipelineTemplateResourceService.get(
                projectId = projectId,
                templateId = templateId,
                version = it
            )
        }

        /**
         * 获取最新版本和版本名称
         *
         * 如果最新版本是分支版本,则需要获取分支最新的激活版本,否则最新版本可能是正式或者草稿版本
         */
        val (releaseVersion, releaseVersionName) = when (basicInfo.latestVersionStatus) {
            // 分支版本,需要获取当前分支最新的激活版本
            VersionStatus.BRANCH -> {
                val branchVersion = basicInfo.releasedVersionName?.let {
                    pipelineTemplateResourceService.getLatestBranchResource(
                        projectId = projectId,
                        templateId = templateId,
                        branchName = it
                    )
                }
                Pair(branchVersion?.version ?: releaseResource.version, branchVersion?.versionName)
            }

            else -> {
                Pair(releaseResource.version, releaseResource.versionName)
            }
        }
        // 草稿版本和版本名,如果有草稿版本,则使用草稿版本,否则使用最新版本
        val (version, versionName) = if (draftResource == null) {
            Pair(releaseVersion, releaseVersionName)
        } else {
            Pair(draftResource.version, null)
        }
        val permission2TemplatesMap = pipelineTemplatePermissionService.getResourcesByPermission(
            userId = userId,
            projectId = projectId,
            permissions = setOf(
                AuthPermission.VIEW,
                AuthPermission.DELETE,
                AuthPermission.EDIT
            )
        )
        val yamlInfo = pipelineYamlFacadeService.getPipelineYamlInfo(
            projectId = projectId,
            pipelineId = templateId,
            version = releaseVersion.toInt()
        )
        val yamlExist = pipelineYamlFacadeService.yamlExistInDefaultBranch(
            projectId = projectId,
            pipelineId = templateId
        )
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
            canDebug = draftResource != null,
            debugPipelineCount = basicInfo.debugPipelineCount,
            instancePipelineCount = basicInfo.instancePipelineCount,
            creator = basicInfo.creator,
            updater = basicInfo.updater,
            createdTime = basicInfo.createdTime,
            updateTime = basicInfo.updateTime,
            canView = permission2TemplatesMap[AuthPermission.VIEW]?.contains(basicInfo.id) ?: false,
            canEdit = permission2TemplatesMap[AuthPermission.EDIT]?.contains(basicInfo.id) ?: false,
            canDelete = permission2TemplatesMap[AuthPermission.DELETE]?.contains(basicInfo.id) ?: false,
            canRelease = draftResource?.model != null,
            version = version,
            versionName = versionName,
            baseVersion = baseResource?.version,
            baseVersionName = baseResource?.versionName,
            baseVersionStatus = baseResource?.status,
            releaseVersion = releaseVersion,
            releaseVersionName = releaseVersionName,
            latestVersionStatus = basicInfo.latestVersionStatus,
            pipelineAsCodeSettings = PipelineAsCodeSettings(
                enable = yamlInfo != null
            ),
            yamlInfo = yamlInfo,
            yamlExist = yamlExist
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
        storageType: PipelineStorageType,
        body: PTemplateTransferBody
    ): PTemplateModelTransferResult {
        return pipelineTemplateGenerator.transfer(
            userId = userId,
            projectId = projectId,
            storageType = storageType,
            templateType = body.templateType,
            templateModel = body.templateModel,
            templateSetting = body.templateSetting,
            params = body.params,
            yaml = body.yaml
        )
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
            params = templateResource.params,
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

    fun getOperationLogsInPage(
        userId: String,
        projectId: String,
        templateId: String,
        creator: String?,
        page: Int?,
        pageSize: Int?
    ): Page<PipelineOperationDetail> {
        val pageNotNull = page ?: 0
        val pageSizeNotNull = pageSize ?: -1
        var slqLimit: SQLLimit? = null
        if (pageSizeNotNull != -1) slqLimit = PageUtil.convertPageSizeToSQLLimit(pageNotNull, pageSizeNotNull)
        val offset = slqLimit?.offset ?: 0
        val limit = slqLimit?.limit ?: -1
        val opCount = pipelineOperationLogDao.getCountByPipeline(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = templateId,
            creator = if (creator.isNullOrBlank()) null else creator
        )
        val opList = pipelineOperationLogDao.getListByPipeline(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = templateId,
            creator = if (creator.isNullOrBlank()) null else creator,
            offset = offset,
            limit = limit
        )
        val versions = mutableSetOf<Int>()
        opList.forEach { versions.add(it.version) }
        val versionMap = pipelineTemplateResourceService.getTemplateVersions(
            PipelineTemplateResourceCommonCondition(
                projectId = projectId,
                templateId = templateId,
            )
        ).associateBy { it.version }
        val detailList = opList.map {
            with(it) {
                val operationLogStr = "${operationLogType.getI18n(I18nUtil.getRequestUserLanguage())} $params"
                PipelineOperationDetail(
                    id = id,
                    projectId = projectId,
                    pipelineId = templateId,
                    version = version,
                    operator = operator,
                    operationLogType = operationLogType,
                    operationLogStr = operationLogStr,
                    params = params,
                    description = description,
                    operateTime = operateTime,
                    versionName = versionMap[it.version]?.versionName,
                    versionCreateTime = versionMap[it.version]?.createTime,
                    status = versionMap[it.version]?.status
                )
            }
        }
        return Page(
            page = pageNotNull,
            pageSize = pageSizeNotNull,
            count = opCount.toLong(),
            records = detailList
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateFacadeService::class.java)
    }
}
