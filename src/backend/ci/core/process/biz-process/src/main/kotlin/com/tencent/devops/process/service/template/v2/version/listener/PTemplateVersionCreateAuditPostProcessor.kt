package com.tencent.devops.process.service.template.v2.version.listener

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.enums.OperationLogType
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.service.PipelineOperationLogService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import org.springframework.stereotype.Service

/**
 * 流水线模板版本创建审计后置处理器
 */
@Service
class PTemplateVersionCreateAuditPostProcessor(
    private val operationLogService: PipelineOperationLogService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService
) : PTemplateVersionCreatePostProcessor {
    override fun postProcessAfterCreation(postCreationContext: DeployTemplateResult) {
        with(postCreationContext) {
            val versionName = if (operationLogType == OperationLogType.CREATE_DRAFT_VERSION) {
                pipelineTemplateResourceService.getLatestReleasedResource(
                    projectId = projectId,
                    templateId = templateId
                )?.versionName ?: throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
                )
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
