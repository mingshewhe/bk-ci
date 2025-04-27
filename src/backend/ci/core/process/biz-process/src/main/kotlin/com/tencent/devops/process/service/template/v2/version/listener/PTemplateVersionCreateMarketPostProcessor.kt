package com.tencent.devops.process.service.template.v2.version.listener

import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.template.UpgradeStrategyEnum
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.service.template.v2.PipelineMarketTemplateFacadeService
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionCreateContext
import com.tencent.devops.store.api.template.ServiceTemplateResource
import org.springframework.stereotype.Service

/**
 * 流水线模板版本创建研发商店后置处理器
 */
@Service
class PTemplateVersionCreateMarketPostProcessor(
    private val pipelineMarketTemplateFacadeService: PipelineMarketTemplateFacadeService,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val client: Client
) : PTemplateVersionCreatePostProcessor {
    override fun postProcessAfterCreation(
        context: PipelineTemplateVersionCreateContext,
        deployTemplateResult: DeployTemplateResult
    ) {
        with(deployTemplateResult) {
            if (!versionAction.isCreateReleaseVersion()) {
                return
            }
            val srcTemplateInfo = pipelineTemplateInfoService.get(
                projectId = projectId,
                templateId = templateId
            )
            // 检查模板是否已上架到研发商店并设置发布策略为自动。
            val publishedCheckResult = client.get(ServiceTemplateResource::class).judgeMarketTemplatePublished(
                templateCode = templateId
            ).data
            val isTemplatePublishedToMarket = publishedCheckResult?.published ?: false
            if (!isTemplatePublishedToMarket || srcTemplateInfo.publishStrategy != UpgradeStrategyEnum.AUTO)
                return
            pipelineMarketTemplateFacadeService.upgradeTemplateAuto(
                userId = userId,
                projectId = projectId,
                marketTemplateId = publishedCheckResult?.templateId ?: "",
                templateId = templateId,
                version = version
            )
        }
    }
}
