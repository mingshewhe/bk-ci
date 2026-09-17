package com.tencent.devops.process.trigger

import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.container.TriggerContainer
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.common.pipeline.pojo.BuildParameters
import com.tencent.devops.process.api.service.ServiceWebhookBuildResource
import com.tencent.devops.process.engine.compatibility.BuildParametersCompatibilityTransformer
import com.tencent.devops.process.engine.pojo.PipelineInfo
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.pojo.BuildId
import com.tencent.devops.process.pojo.pipeline.PipelineResourceVersion
import com.tencent.devops.process.pojo.webhook.WebhookStartPipelineRequest
import com.tencent.devops.process.trigger.scm.listener.WebhookTriggerContext
import com.tencent.devops.process.trigger.scm.listener.WebhookTriggerManager
import com.tencent.devops.process.utils.PipelineVarUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WebhookTriggerBuildService(
    private val client: Client,
    private val buildParamCompatibilityTransformer: BuildParametersCompatibilityTransformer,
    private val webhookTriggerManager: WebhookTriggerManager,
    private val pipelineRepositoryService: PipelineRepositoryService
) {
    fun startPipeline(
        context: WebhookTriggerContext,
        pipelineInfo: PipelineInfo,
        resource: PipelineResourceVersion,
        startParams: Map<String, Any>
    ) {
        val buildId = startPipeline(
            pipelineInfo = pipelineInfo,
            resource = resource,
            startParams = startParams,
            startType = context.startType
        )
        context.buildId = buildId
        context.startParams = startParams
        webhookTriggerManager.fireBuildSuccess(context = context)
    }

    fun startPipeline(
        pipelineInfo: PipelineInfo,
        resource: PipelineResourceVersion,
        startParams: Map<String, Any>,
        startType: StartType = StartType.SERVICE
    ): BuildId {
        val startEpoch = System.currentTimeMillis()
        val (projectId, pipelineId) = pipelineInfo.projectId to pipelineInfo.pipelineId
        val userId = pipelineRepositoryService.getPipelineOauthUser(
            projectId = projectId,
            pipelineId = pipelineId
        ) ?: pipelineInfo.lastModifyUser
        val buildId = client.getGateway(ServiceWebhookBuildResource::class).webhookStartPipeline(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = WebhookStartPipelineRequest(
                pipelineInfo = pipelineInfo,
                startType = startType,
                pipelineParamMap = convertBuildParameters(
                    userId = userId,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    triggerContainer = resource.model.getTriggerContainer(),
                    startParams = startParams
                ),
                channelCode = pipelineInfo.channelCode,
                resource = resource,
                signPipelineVersion = resource.version,
                frequencyLimit = false
            )
        ).data ?: throw IllegalStateException("webhook start pipeline returned empty buildId")
        logger.info(
            "success to start pipeline|" +
                "projectId: $projectId|pipelineId: $pipelineId|" +
                "version: ${resource.version}|" +
                "time=${System.currentTimeMillis() - startEpoch}"
        )
        return buildId
    }

    private fun convertBuildParameters(
        userId: String,
        projectId: String,
        pipelineId: String,
        triggerContainer: TriggerContainer,
        startParams: Map<String, Any>
    ): MutableMap<String, BuildParameters> {
        val pipelineParamMap = mutableMapOf<String, BuildParameters>()
        val paramMap = buildParamCompatibilityTransformer.parseTriggerParam(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            paramProperties = triggerContainer.params,
            paramValues = startParams.mapValues { it.value.toString() }
        )
        pipelineParamMap.putAll(paramMap)
        startParams.forEach {
            if (paramMap.containsKey(it.key)) {
                return@forEach
            }
            val newVarName = PipelineVarUtil.oldVarToNewVar(it.key)
            if (newVarName == null) {
                pipelineParamMap[it.key] = BuildParameters(key = it.key, value = it.value ?: "")
            } else if (!pipelineParamMap.contains(newVarName)) {
                pipelineParamMap[newVarName] = BuildParameters(key = newVarName, value = it.value ?: "")
            }
        }
        return pipelineParamMap
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WebhookTriggerBuildService::class.java)
    }
}
