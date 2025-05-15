package com.tencent.devops.process.service.template.v2.version.listener

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionCreateContext
import com.tencent.devops.store.api.template.ServiceTemplateResource
import com.tencent.devops.store.pojo.template.TemplateVersionInstallHistoryInfo
import org.springframework.stereotype.Service

/**
 * 流水线模板版本创建研发商店安装后置处理器
 */
@Service
class PTemplateVersionCreateMarketInstallPostProcessor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val client: Client
) : PTemplateVersionCreatePostProcessor {
    override fun postProcessAfterCreation(
        context: PipelineTemplateVersionCreateContext,
        deployTemplateResult: DeployTemplateResult
    ) {
        with(deployTemplateResult) {
            if (versionAction != PipelineVersionAction.CREATE_RELEASE) {
                return
            }
            val templateInfo = pipelineTemplateInfoService.get(
                projectId = projectId,
                templateId = templateId
            )
            if (templateInfo.mode != TemplateType.CONSTRAINT)
                return

            val latestReleasedResource = pipelineTemplateResourceService.getLatestReleasedResource(
                projectId = projectId,
                templateId = templateId
            ) ?: throw ErrorCodeException(errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS)
            val srcTemplateProjectId = latestReleasedResource.srcTemplateProjectId!!
            val srcTemplateId = latestReleasedResource.srcTemplateId!!
            val srcTemplateVersion = latestReleasedResource.srcTemplateVersion!!
            val srcTemplateResource = pipelineTemplateResourceService.get(
                projectId = srcTemplateProjectId,
                templateId = srcTemplateId,
                version = srcTemplateVersion
            )
            client.get(ServiceTemplateResource::class).createTemplateVersionInstallHistory(
                installHistoryInfo = TemplateVersionInstallHistoryInfo(
                    srcMarketTemplateProjectCode = srcTemplateProjectId,
                    srcMarketTemplateCode = srcTemplateId,
                    version = srcTemplateVersion,
                    versionName = srcTemplateResource.versionName!!,
                    number = srcTemplateResource.number,
                    projectCode = projectId,
                    templateCode = templateId,
                    creator = userId
                )
            )
        }
    }
}
