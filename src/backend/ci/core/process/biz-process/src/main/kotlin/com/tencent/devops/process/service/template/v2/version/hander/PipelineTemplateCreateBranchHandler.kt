package com.tencent.devops.process.service.template.v2.version.hander

import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.template.v2.PipelineTemplatePermission
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplatePersistenceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateSettingService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionContext
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionHandler
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线创建分支版本
 */
@Service
class PipelineTemplateCreateBranchHandler @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val pipelineTemplatePermissionService: PipelineTemplatePermissionService,
    private val pipelineTemplatePersistenceService: PipelineTemplatePersistenceService
) : PipelineTemplateVersionHandler {
    override fun support(versionAction: PipelineVersionAction): Boolean {
        return versionAction == PipelineVersionAction.CREATE_BRANCH_VERSION
    }

    override fun handle(context: PipelineTemplateVersionContext): DeployTemplateResult {
        with(context) {
            val templateInfo = pipelineTemplateInfoService.getOrNull(
                projectId = projectId,
                templateId = templateId
            )
            if (templateInfo == null) {
                pipelineTemplatePersistenceService.createTemplate(
                    pipelineTemplateInfo = pipelineTemplateInfo,
                    pipelineTemplateResource = pipelineTemplateResource,
                    pipelineTemplateSetting = pipelineTemplateSetting,
                    pipelineTemplatePermission = PipelineTemplatePermission(
                        projectId = projectId,
                        id = templateId,
                        name = pipelineTemplateInfo.name,
                        creator = userId
                    )
                )
            } else {
                createBranchVersion()
            }
            return DeployTemplateResult(
                templateId = templateId,
                templateName = pipelineTemplateInfo.name,
                version = pipelineTemplateResource.version,
                versionNum = pipelineTemplateResource.versionNum,
                versionName = pipelineTemplateResource.versionName
            )
        }
    }

    private fun PipelineTemplateVersionContext.createBranchVersion() {
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            pipelineTemplateResourceService.create(
                transactionContext = transactionContext,
                pipelineTemplateResource = pipelineTemplateResource
            )
            pipelineTemplateSettingService.create(
                transactionContext = transactionContext,
                pipelineTemplateSetting = pipelineTemplateSetting
            )
        }
    }
}
