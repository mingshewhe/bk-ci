package com.tencent.devops.process.trigger

import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.I18Variable
import com.tencent.devops.common.api.util.MessageUtil
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.service.trace.TraceTag
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.common.webhook.enums.WebhookI18nConstants.EVENT_REPLAY_DESC
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TRIGGER_DETAIL_NOT_FOUND
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TRIGGER_REPLAY_PIPELINE_NOT_EMPTY
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.pojo.trigger.PipelineTriggerEvent
import com.tencent.devops.process.pojo.trigger.PipelineTriggerType
import com.tencent.devops.process.webhook.CodeWebhookEventDispatcher
import com.tencent.devops.process.webhook.pojo.event.commit.ReplayWebhookEvent
import com.tencent.devops.repository.api.ServiceRepositoryPermissionResource
import com.tencent.devops.repository.api.ServiceRepositoryWebhookResource
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PipelineTriggerReplayService(
    private val client: Client,
    private val streamBridge: StreamBridge,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineTriggerEventService: PipelineTriggerEventService
) {
    fun replay(
        userId: String,
        projectId: String,
        detailId: Long
    ): Long {
        logger.info("replay pipeline trigger event|$userId|$projectId|$detailId")
        val triggerDetail = pipelineTriggerEventService.getTriggerDetailById(
            projectId = projectId,
            detailId = detailId
        ) ?: throw ErrorCodeException(
            errorCode = ERROR_TRIGGER_DETAIL_NOT_FOUND,
            params = arrayOf(detailId.toString())
        )
        val pipelineId = triggerDetail.pipelineId ?: throw ErrorCodeException(
            errorCode = ERROR_TRIGGER_REPLAY_PIPELINE_NOT_EMPTY,
            params = arrayOf(detailId.toString())
        )
        val permission = AuthPermission.EXECUTE
        pipelinePermissionService.validPipelinePermission(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            permission = permission,
            message = MessageUtil.getMessageByLocale(
                CommonMessageCode.USER_NOT_PERMISSIONS_OPERATE_PIPELINE,
                I18nUtil.getLanguage(userId),
                arrayOf(
                    userId,
                    projectId,
                    permission.getI18n(I18nUtil.getLanguage(userId)),
                    pipelineId
                )
            )
        )
        return replayAll(
            userId = userId,
            projectId = projectId,
            eventId = triggerDetail.eventId,
            pipelineId = pipelineId
        )
    }

    fun replayAll(
        userId: String,
        projectId: String,
        eventId: Long,
        pipelineId: String? = null
    ): Long {
        logger.info("replay all pipeline trigger event|$userId|$projectId|$eventId")
        val triggerEvent = pipelineTriggerEventService.getTriggerEvent(
            projectId = projectId,
            eventId = eventId
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TRIGGER_EVENT_NOT_FOUND,
            params = arrayOf(eventId.toString())
        )
        val triggerType = PipelineTriggerType.parse(triggerEvent.triggerType) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TRIGGER_TYPE_REPLAY_NOT_SUPPORT,
            params = arrayOf(triggerEvent.triggerType)
        )
        if (triggerType.toScmType() != null) {
            client.get(ServiceRepositoryPermissionResource::class).validatePermission(
                userId = userId,
                projectId = projectId,
                repositoryHashId = triggerEvent.eventSource!!,
                permission = AuthPermission.USE
            )
        }
        val requestId = MDC.get(TraceTag.BIZID)
        val replayEventId = pipelineTriggerEventService.getEventId()
        if (triggerType.toScmType() != null) {
            val webhookRequest = client.get(ServiceRepositoryWebhookResource::class).getWebhookRequest(
                requestId = triggerEvent.requestId
            ).data ?: throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_WEBHOOK_REQUEST_NOT_FOUND
            )
            client.get(ServiceRepositoryWebhookResource::class).saveWebhookRequest(
                repositoryWebhookRequest = webhookRequest.copy(
                    requestId = requestId,
                    createTime = LocalDateTime.now()
                )
            )
        }
        val replayRequestId = triggerEvent.replayRequestId ?: triggerEvent.requestId
        val replayTriggerEvent = with(triggerEvent) {
            PipelineTriggerEvent(
                requestId = requestId,
                projectId = projectId,
                eventId = replayEventId,
                triggerType = triggerType.name,
                eventSource = eventSource,
                eventType = eventType,
                triggerUser = userId,
                eventDesc = I18Variable(
                    code = EVENT_REPLAY_DESC,
                    params = listOf(eventId.toString(), userId)
                ).toJsonStr(),
                replayRequestId = replayRequestId,
                requestParams = requestParams,
                createTime = LocalDateTime.now(),
                eventBody = triggerEvent.eventBody
            )
        }
        pipelineTriggerEventService.saveTriggerEvent(triggerEvent = replayTriggerEvent)
        CodeWebhookEventDispatcher.dispatchReplayEvent(
            streamBridge = streamBridge,
            event = ReplayWebhookEvent(
                userId = userId,
                projectId = projectId,
                eventId = replayEventId,
                replayRequestId = replayRequestId,
                scmType = triggerType.toScmType(),
                triggerType = triggerType.name,
                pipelineId = pipelineId
            )
        )
        return replayEventId
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTriggerReplayService::class.java)
    }
}
