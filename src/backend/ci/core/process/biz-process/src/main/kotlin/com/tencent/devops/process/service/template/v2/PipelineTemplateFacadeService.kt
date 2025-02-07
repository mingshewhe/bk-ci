package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateBasicCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCustomCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateMarketCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplatePermission
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRepositoryCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingVersion
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
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val dslContext: DSLContext,
    private val client: Client
) {
    fun createTemplate(request: PipelineTemplateBasicCreateReq) {
        logger.info("create template in project ${request.projectId} by ${request.source} ,body is {}", request)
        when (request) {
            is PipelineTemplateCustomCreateReq -> createByCustom(request)
            is PipelineTemplateMarketCreateReq -> createByMarket(request)
            is PipelineTemplateYamlCreateReq -> createByYaml(request)
            is PipelineTemplateRepositoryCreateReq -> createByRepository(request)
            else -> {}
        }
    }

    fun createByCustom(request: PipelineTemplateCustomCreateReq) {
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = request.projectId,
            name = request.name
        )
        val templateId = UUIDUtil.generate()
        val templateModel = pipelineTemplateModelParser.getDefaultTemplateModel(
            projectId = request.projectId,
            name = request.name,
            desc = request.desc,
            type = request.type,
            creator = request.creator
        )
        val templateModelYaml = pipelineTemplatePACService.getDefaultTemplateModelYaml(request)
        val version = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!
        val templateSettingVersion = PipelineTemplateSettingVersion.defaultSetting(
            projectId = request.projectId,
            templateId = templateId,
            templateName = request.name,
            creator = request.creator
        )
        val pipelineTemplateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = request.projectId,
            name = request.name,
            desc = request.desc,
            mode = TemplateType.CUSTOMIZE.name,
            type = request.type,
            enablePac = false,
            lastedVersion = version,
            lastedVersionName = PipelineTemplateCommonService.INIT_VERSION_NAME,
            lastedSettingVersion = 1,
            source = PipelineTemplateSource.CUSTOM,
            storeFlag = false,
            creator = request.creator
        )
        val pipelineTemplateResource = PipelineTemplateResource.defaultTemplateResource(
            projectId = request.projectId,
            templateId = templateId,
            type = request.type,
            version = version,
            templateModel = templateModel,
            yaml = templateModelYaml,
            creator = request.creator
        )
        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = request.projectId,
            id = templateId,
            name = request.name,
            creator = request.creator
        )
        saveTemplate(
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateResource = pipelineTemplateResource,
            pipelineTemplateSettingVersion = templateSettingVersion,
            pipelineTemplatePermission = pipelineTemplatePermission
        )
    }

    fun createByMarket(request: PipelineTemplateMarketCreateReq) {
        val marketTemplateInfo = client.get(ServiceTemplateResource::class).getTemplateDetailByCode(
            userId = request.creator,
            templateCode = request.marketTemplateId
        ).data ?: throw ErrorCodeException(errorCode = "")
        val templateInfo = pipelineTemplateInfoService.get(
            templateId = request.marketTemplateId
        )
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = request.projectId,
            name = templateInfo.name
        )
        val templateId = UUIDUtil.generate()
        val pipelineTemplateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = request.projectId,
            name = templateInfo.name,
            desc = templateInfo.desc,
            mode = TemplateType.CONSTRAINT.name,
            type = templateInfo.type,
            enablePac = false,
            lastedVersion = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!,
            lastedVersionName = templateInfo.lastedVersionName,
            lastedSettingVersion = 1,
            source = PipelineTemplateSource.MARKET,
            storeFlag = true,
            creator = request.creator,
            srcTemplateProjectId = templateInfo.projectId,
            srcTemplateId = templateInfo.id,
            category = marketTemplateInfo.classifyCode,
            logoUrl = marketTemplateInfo.logoUrl
        )
        val templateSettingVersion = PipelineTemplateSettingVersion.defaultSetting(
            projectId = request.projectId,
            templateId = templateId,
            templateName = templateInfo.name,
            creator = request.creator
        )
        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = request.projectId,
            id = templateId,
            name = templateInfo.name,
            creator = request.creator
        )
        saveTemplate(
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateSettingVersion = templateSettingVersion,
            pipelineTemplatePermission = pipelineTemplatePermission
        )
    }

    fun createByRepository(request: PipelineTemplateRepositoryCreateReq) {

    }

    fun createByYaml(request: PipelineTemplateYamlCreateReq) {
        val version = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!
        // TODO 校验
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = request.projectId,
            name = request.name
        )
        val templateId = UUIDUtil.generate()
        val pipelineTemplateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = request.projectId,
            name = request.name,
            desc = request.desc,
            mode = TemplateType.CUSTOMIZE.name,
            type = request.type,
            enablePac = false,
            lastedVersion = version,
            lastedVersionName = PipelineTemplateCommonService.INIT_VERSION_NAME,
            lastedSettingVersion = 1,
            source = PipelineTemplateSource.YAML,
            storeFlag = true,
            creator = request.creator
        )
        val pipelineTemplateSettingVersion = request.setting.toSettingVersion(
            templateId = templateId,
            versionName = PipelineTemplateCommonService.INIT_VERSION_NAME,
            settingVersion = 1
        )
        val model = pipelineTemplateModelParser.parseTemplateModel(request.originalModel)
        val pipelineTemplateResource = PipelineTemplateResource(
            projectId = request.projectId,
            templateId = templateId,
            type = request.type,
            version = version,
            number = 1,
            versionName = PipelineTemplateCommonService.INIT_VERSION_NAME,
            params = request.params,
            originalModel = request.originalModel,
            model = model,
            yaml = request.yaml,
            status = VersionStatus.COMMITTING,
            creator = request.creator
        )
        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = request.projectId,
            id = templateId,
            name = request.name,
            creator = request.creator
        )
        saveTemplate(
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateResource = pipelineTemplateResource,
            pipelineTemplateSettingVersion = pipelineTemplateSettingVersion,
            pipelineTemplatePermission = pipelineTemplatePermission
        )
    }

    private fun saveTemplate(
        pipelineTemplateInfo: PipelineTemplateInfo? = null,
        pipelineTemplateResource: PipelineTemplateResource? = null,
        pipelineTemplateSettingVersion: PipelineTemplateSettingVersion? = null,
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
            pipelineTemplateSettingVersion?.let {
                pipelineTemplateSettingService.create(
                    transactionContext = context,
                    pipelineTemplateSettingVersion = pipelineTemplateSettingVersion
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

    fun deleteTemplate(projectId: String, templateId: String) {
        logger.info("Start to delete the template $projectId|$templateId")
        val templateInfo = pipelineTemplateInfoService.get(
            PipelineTemplateCommonCondition(
                projectId = projectId,
                templateId = templateId
            )
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
    }

    // 复制模板
    // 导出模板
    // 编辑模板
    // 保存草稿
    // 发布模板
    // 复制模板
    // 获取用户最近打开的模板类型
    // 获取模板列表
    // 查看模板详情
    // 流水线模板检查
    // 模板版本对比
    // 查看全部模板版本历史
    companion object {
        private const val TEMPLATE_BIZ_TAG_NAME = "TEMPLATE"
        private val logger = LoggerFactory.getLogger(PipelineTemplateFacadeService::class.java)
    }
}
