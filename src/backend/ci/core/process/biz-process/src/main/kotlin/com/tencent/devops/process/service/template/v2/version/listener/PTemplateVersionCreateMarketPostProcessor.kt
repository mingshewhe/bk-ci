package com.tencent.devops.process.service.template.v2.version.listener

import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.service.template.v2.PipelineMarketTemplateFacadeService
import org.springframework.stereotype.Service

/**
 * 流水线模板版本创建研发商店后置处理器
 */
@Service
class PTemplateVersionCreateMarketPostProcessor(
    private val pipelineMarketTemplateFacadeService: PipelineMarketTemplateFacadeService
) : PTemplateVersionCreatePostProcessor {
    override fun postProcessAfterCreation(postCreationContext: DeployTemplateResult) {
        with(postCreationContext) {
            if (!versionAction.isCreateReleaseVersion()) {
                return
            }
            pipelineMarketTemplateFacadeService.upgradeTemplateAuto(
                userId = userId,
                projectId = projectId,
                templateId = templateId,
                version = version
            )
        }
    }
}
