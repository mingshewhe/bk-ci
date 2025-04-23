package com.tencent.devops.process.service.template.v2.version.listener

import com.tencent.devops.process.enums.OperationLogType
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.service.PipelineOperationLogService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionCreateContext
import org.springframework.stereotype.Service

/**
 * 流水线模板版本创建审计后置处理器
 */
@Service
class PTemplateVersionCreateAuditPostProcessor(
    private val operationLogService: PipelineOperationLogService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService
) : PTemplateVersionCreatePostProcessor {
    override fun postProcessAfterCreation(
        context: PipelineTemplateVersionCreateContext,
        deployTemplateResult: DeployTemplateResult
    ) {
        with(deployTemplateResult) {
            val versionName = if (operationLogType == OperationLogType.CREATE_DRAFT_VERSION) {
                val baseVersion = pipelineTemplateResourceService.get(
                    projectId = projectId,
                    templateId = templateId,
                    version = version
                ).baseVersion
                baseVersion?.let {
                    pipelineTemplateResourceService.get(
                        projectId = projectId,
                        templateId = templateId,
                        version = it
                    ).versionName
                }
            } else {
                versionName
            } ?: ""
            operationLogService.addOperationLog(
                userId = userId,
                projectId = projectId,
                pipelineId = templateId,
                version = version.toInt(),
                operationLogType = operationLogType,
                params = versionName,
                description = null
            )
        }
    }
}
