package com.tencent.devops.process.service.template.v2.version.listener

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.process.dao.PipelineSettingDao
import com.tencent.devops.process.engine.dao.template.TemplateDao
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateSettingService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service

/**
 * 流水线模板版本创建兼容后置处理器
 */
@Service
class PTemplateVersionCreateCompatibilityPostProcessor(
    val v2TemplateInfoService: PipelineTemplateInfoService,
    val v2TemplateResourceService: PipelineTemplateResourceService,
    val v2TemplateSettingService: PipelineTemplateSettingService,
    val v1TemplateDao: TemplateDao,
    val v1TemplateSettingService: PipelineSettingDao,
    val dslContext: DSLContext
) : PTemplateVersionCreatePostProcessor {
    override fun postProcessAfterCreation(postCreationContext: DeployTemplateResult) {
        with(postCreationContext) {
            if (versionAction != PipelineVersionAction.RELEASE_DRAFT &&
                versionAction != PipelineVersionAction.CREATE_RELEASE) {
                return
            }
            val templateInfo = v2TemplateInfoService.get(
                projectId = projectId,
                templateId = templateId
            )
            val templateResource = v2TemplateResourceService.get(
                projectId = projectId,
                templateId = templateId,
                version = version
            )
            val templateSetting = v2TemplateSettingService.get(
                projectId = projectId,
                templateId = templateId,
                settingVersion = templateResource.settingVersion
            )
            dslContext.transaction { configuration ->
                val transactionContext = DSL.using(configuration)
                v1TemplateDao.createTemplate(
                    dslContext = transactionContext,
                    projectId = projectId,
                    templateId = templateId,
                    templateName = templateInfo.name,
                    versionName = templateResource.versionName!!,
                    userId = userId,
                    template = JsonUtil.toJson(templateResource.model),
                    type = templateInfo.mode.name,
                    category = templateInfo.category,
                    logoUrl = templateInfo.logoUrl,
                    srcTemplateId = templateResource.srcTemplateId,
                    storeFlag = templateInfo.storeFlag,
                    weight = 0,
                    version = version,
                    desc = templateInfo.desc
                )
                v1TemplateSettingService.delete(
                    dslContext = transactionContext,
                    projectId = projectId,
                    pipelineId = templateId
                )
                v1TemplateSettingService.saveSetting(
                    dslContext = transactionContext,
                    setting = templateSetting,
                    isTemplate = true
                )
            }
        }
    }
}
