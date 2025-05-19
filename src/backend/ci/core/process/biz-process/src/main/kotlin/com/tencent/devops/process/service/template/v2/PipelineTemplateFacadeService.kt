package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.model.SQLLimit
import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.CodeTargetAction
import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.template.PipelineTemplateType
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.engine.dao.PipelineOperationLogDao
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.PipelineOperationDetail
import com.tencent.devops.process.pojo.PipelinePermissions
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.pipeline.PipelineYamlFileInfo
import com.tencent.devops.process.pojo.setting.PipelineVersionSimple
import com.tencent.devops.process.pojo.template.PipelineTemplateListResponse
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
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoUpdateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoV2
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateMarketCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateMarketRelatedInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateStrategyUpdateInfo
import com.tencent.devops.process.pojo.template.v2.PreFetchTemplateReleaseResult
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionManager
import com.tencent.devops.process.util.FileExportUtil
import com.tencent.devops.process.yaml.PipelineYamlFacadeService
import com.tencent.devops.process.yaml.transfer.PipelineTransferException
import com.tencent.devops.store.api.template.ServiceTemplateResource
import com.tencent.devops.store.pojo.template.enums.TemplateStatusEnum
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
    private val pipelineTemplatePersistenceService: PipelineTemplatePersistenceService,
    private val client: Client
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
        templateId: String?,
        request: PipelineTemplateMarketCreateReq
    ): DeployTemplateResult {
        logger.info("$userId create template in project $projectId by market ,body is $request")
        return pipelineTemplateVersionManager.deployTemplate(
            userId = userId,
            projectId = projectId,
            templateId = templateId,
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
    ): PreFetchTemplateReleaseResult {
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
        return PreFetchTemplateReleaseResult(
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
    ): SQLPage<PipelineTemplateListResponse> {
        logger.info("list template infos {}|{}", userId, commonCondition)
        val projectId = commonCondition.projectId!!
        val enableTemplatePermissionManage = pipelineTemplatePermissionService.enableTemplatePermissionManage(projectId)
        val (count, templateInfos) = if (enableTemplatePermissionManage) {
            processWithPermissions(userId, projectId, commonCondition)
        } else {
            processWithoutPermissions(userId, projectId, commonCondition)
        }

        return SQLPage(count, templateInfos)
    }

    private fun processWithPermissions(
        userId: String,
        projectId: String,
        condition: PipelineTemplateCommonCondition
    ): Pair<Long, List<PipelineTemplateListResponse>> {
        val permissionMap = pipelineTemplatePermissionService.getResourcesByPermission(
            userId = userId,
            projectId = projectId,
            permissions = setOf(AuthPermission.VIEW, AuthPermission.LIST, AuthPermission.DELETE, AuthPermission.EDIT)
        )
        val accessibleTemplateIds = permissionMap[AuthPermission.LIST] ?: return Pair(0L, emptyList())

        val queryCondition = condition.copy(filterTemplateIds = accessibleTemplateIds)
        val allTemplates = pipelineTemplateInfoService.list(queryCondition)

        return processTemplateList(
            allTemplates = allTemplates,
            totalCount = pipelineTemplateInfoService.count(queryCondition),
            getPermission = { templateId ->
                PipelinePermissions(
                    canView = permissionMap[AuthPermission.VIEW]?.contains(templateId) ?: false,
                    canEdit = permissionMap[AuthPermission.EDIT]?.contains(templateId) ?: false,
                    canDelete = permissionMap[AuthPermission.DELETE]?.contains(templateId) ?: false,
                    canManage = false
                )
            }
        )
    }

    private fun processWithoutPermissions(
        userId: String,
        projectId: String,
        condition: PipelineTemplateCommonCondition
    ): Pair<Long, List<PipelineTemplateListResponse>> {
        val allTemplates = pipelineTemplateInfoService.list(condition)
        val isProjectManager = pipelinePermissionService.checkProjectManager(userId, projectId)

        return processTemplateList(
            allTemplates = allTemplates,
            totalCount = pipelineTemplateInfoService.count(condition),
            getPermission = { _ ->
                PipelinePermissions(
                    canView = isProjectManager,
                    canEdit = isProjectManager,
                    canDelete = isProjectManager,
                    canManage = isProjectManager
                )
            }
        )
    }

    private fun processTemplateList(
        allTemplates: List<PipelineTemplateInfoV2>,
        totalCount: Int,
        getPermission: (String) -> PipelinePermissions
    ): Pair<Long, List<PipelineTemplateListResponse>> {
        val publishedTemplates = allTemplates.filter { it.storeStatus == TemplateStatusEnum.RELEASED }
        val marketTemplates = allTemplates.filter { it.mode == TemplateType.CONSTRAINT }
        // 已上架模板的最新发布版本
        val latestReleasedVersions = publishedTemplates.fetchVersions { ids ->
            pipelineTemplateResourceService.listLatestReleasedVersions(ids)
        }
        logger.debug("latestReleasedVersions :$latestReleasedVersions")
        // 已上架模板的最新上架商店版本
        val latestMarketVersions = publishedTemplates.fetchVersions { ids ->
            client.get(ServiceTemplateResource::class).listLatestPublishedVersions(ids).data ?: emptyList()
        }
        logger.debug("latestMarketVersions :$latestMarketVersions")

        // 模板最新安装的研发商店版本
        val latestInstalledVersions = marketTemplates.fetchVersions { ids ->
            client.get(ServiceTemplateResource::class).listLatestInstalledVersions(ids).data ?: emptyList()
        }
        logger.debug("latestInstalledVersions :$latestInstalledVersions")

        // 父模板最新发布版本
        val latestParentVersions = marketTemplates.takeIf { it.isNotEmpty() }?.let {
            client.get(ServiceTemplateResource::class).listLatestPublishedVersions(
                it.mapNotNull { t -> t.srcTemplateId }
            ).data
        } ?: emptyList()
        logger.debug("latestParentVersions :$latestParentVersions")

        // 处理每个模板
        val processedTemplates = allTemplates.map { template ->
            val upgradeFlag = if (template.mode == TemplateType.CONSTRAINT) {
                val installedVersion = latestInstalledVersions.firstOrNull { it.templateCode == template.id }
                val parentVersion = latestParentVersions.firstOrNull { it.templateCode == template.srcTemplateId }
                logger.debug("${template.id} installedVersion($installedVersion)|parentVersion($parentVersion)")
                installedVersion != null && parentVersion != null && installedVersion.version != parentVersion.version
            } else {
                false
            }

            // 发布检查逻辑
            val publishFlag = if (template.storeStatus == TemplateStatusEnum.RELEASED) {
                val releasedVersion = latestReleasedVersions.firstOrNull { it.pipelineId == template.id }
                val marketVersion = latestMarketVersions.firstOrNull { it.templateCode == template.id }
                logger.debug("${template.id} releasedVersion($releasedVersion)|marketVersion($marketVersion)")
                releasedVersion != null && marketVersion != null &&
                    releasedVersion.version.toLong() != marketVersion.version
            } else {
                false
            }

            PipelineTemplateListResponse(
                pipelineTemplateInfo = template,
                permission = getPermission(template.id),
                upgradeFlag = upgradeFlag,
                publishFlag = publishFlag,
                storeFlag = template.storeStatus == TemplateStatusEnum.RELEASED
            )
        }

        return Pair(totalCount.toLong(), processedTemplates)
    }

    // 获取各类最新版本
    fun <T> List<PipelineTemplateInfoV2>.fetchVersions(fetch: (List<String>) -> List<T>) =
        takeIf { it.isNotEmpty() }?.let { fetch(it.map { t -> t.id }) } ?: emptyList()

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
            version = basicInfo.releasedVersion
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

        val pipelineTemplateMarketRelatedInfo = basicInfo.takeIf { it.mode == TemplateType.CONSTRAINT }?.let {
            if (it.srcTemplateProjectId == null || it.srcTemplateId == null) {
                throw IllegalArgumentException("srcTemplateProjectId or srcTemplateId is null")
            }
            val recentlyInstalledVersion = client.get(ServiceTemplateResource::class).getRecentlyInstalledVersion(
                projectCode = projectId,
                templateCode = templateId
            ).data ?: throw ErrorCodeException(errorCode = "")

            val srcTemplateLatestReleasedVersion =
                client.get(ServiceTemplateResource::class).getLatestMarketPublishedVersion(
                    templateCode = it.srcTemplateId!!
                ).data ?: throw ErrorCodeException(errorCode = "")

            val srcMarketTemplateInfo = pipelineTemplateInfoService.get(
                projectId = it.srcTemplateProjectId!!,
                templateId = it.srcTemplateId!!
            )

            PipelineTemplateMarketRelatedInfo(
                srcMarketProjectId = srcMarketTemplateInfo.projectId,
                srcMarketTemplateId = srcMarketTemplateInfo.id,
                srcMarketTemplateName = srcMarketTemplateInfo.name,
                srcMarketTemplateLatestVersion = srcTemplateLatestReleasedVersion.version,
                srcMarketTemplateLatestVersionName = srcTemplateLatestReleasedVersion.versionName,
                latestInstalledVersion = recentlyInstalledVersion.version,
                latestInstalledVersionName = recentlyInstalledVersion.versionName,
                upgradeStrategy = it.upgradeStrategy!!,
                settingSyncStrategy = it.settingSyncStrategy!!,
                latestInstaller = recentlyInstalledVersion.creator,
                latestInstalledTime = recentlyInstalledVersion.createTime!!
            )
        }
        return PipelineTemplateInfoResponse(
            id = basicInfo.id,
            projectId = basicInfo.projectId,
            name = basicInfo.name,
            desc = basicInfo.desc,
            mode = basicInfo.mode,
            publishStrategy = basicInfo.publishStrategy,
            category = basicInfo.category,
            type = basicInfo.type,
            logoUrl = basicInfo.logoUrl,
            enablePac = basicInfo.enablePac,
            storeFlag = basicInfo.storeStatus == TemplateStatusEnum.RELEASED,
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
            yamlExist = yamlExist,
            pipelineTemplateMarketRelatedInfo = pipelineTemplateMarketRelatedInfo
        )
    }

    fun getTemplateVersions(
        commonCondition: PipelineTemplateResourceCommonCondition
    ): Page<PipelineVersionSimple> {
        with(commonCondition) {
            val finCondition = upgradableVersionsQuery?.takeIf { it }?.let {
                if (templateId == null) {
                    throw IllegalArgumentException("templateId is null")
                }
                client.get(ServiceTemplateResource::class).getLatestInstalledVersion(
                    projectCode = projectId,
                    templateCode = templateId!!
                ).data?.let { latestInstalled ->
                    PipelineTemplateResourceCommonCondition(
                        projectId = latestInstalled.srcMarketTemplateProjectCode,
                        templateId = latestInstalled.srcMarketTemplateCode,
                        gtNumber = latestInstalled.number,
                        status = VersionStatus.RELEASED
                    )
                } ?: return Page(page = -1, pageSize = -1, records = emptyList(), count = 0)
            } ?: commonCondition  // 默认使用原始条件
            val records = pipelineTemplateResourceService.getTemplateVersions(finCondition)
            val count = pipelineTemplateResourceService.count(finCondition)
            return Page(
                page = commonCondition.page ?: -1,
                pageSize = commonCondition.pageSize ?: -1,
                records = records,
                count = count.toLong()
            )
        }
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

    fun transformTemplateToCustom(
        userId: String,
        projectId: String,
        templateId: String
    ): Boolean {
        val templateInfo = pipelineTemplateInfoService.get(
            projectId = projectId,
            templateId = templateId
        )
        if (templateInfo.mode != TemplateType.CONSTRAINT) {
            throw ErrorCodeException(errorCode = "")
        }
        pipelineTemplatePersistenceService.transformTemplateToCustom(
            userId = userId,
            projectId = projectId,
            templateId = templateId
        )
        return true
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

    fun checkTemplate(
        projectId: String,
        userId: String,
        templateId: String,
        version: Long
    ): Boolean {
        val templateResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = templateId,
            version = version
        )
        // todo 检查是否已经上架过
        if (templateResource.storeStatus == TemplateStatusEnum.RELEASED)
            throw ErrorCodeException(errorCode = "该版本已经发布")
        // todo 检查模型
        return true
    }

    fun updateUpgradeStrategy(
        userId: String,
        projectId: String,
        templateId: String,
        request: PipelineTemplateStrategyUpdateInfo
    ): Boolean {
        // todo 当策略是自动升级时，将安装最新版本
        pipelineTemplateInfoService.update(
            record = PipelineTemplateInfoUpdateInfo(
                upgradeStrategy = request.upgradeStrategy,
                settingSyncStrategy = request.settingSyncStrategy,
                updater = userId
            ),
            commonCondition = PipelineTemplateCommonCondition(
                projectId = projectId,
                templateId = templateId
            )
        )
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateFacadeService::class.java)
    }
}
