package com.tencent.devops.process.api.external

import com.tencent.devops.common.api.exception.InvalidParamException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.event.dispatcher.SampleEventDispatcher
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.constant.TapdWebhookConstant
import com.tencent.devops.process.constant.TapdWebhookConstant.TAPD_EVENT_SEPARATOR
import com.tencent.devops.process.constant.TapdWebhookConstant.TAPD_KEY_CURRENT_USER
import com.tencent.devops.process.constant.TapdWebhookConstant.TAPD_KEY_EVENT
import com.tencent.devops.process.constant.TapdWebhookConstant.TAPD_KEY_WORKSPACE_ID
import com.tencent.devops.process.trigger.event.TapdWebhookRequestEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

@RestResource
class ExternalTapdEventResourceImpl @Autowired constructor(
    private val simpleDispatcher: SampleEventDispatcher
) : ExternalTapdEventResource {

    @Value("\${external.webhook.tapd.secret:}")
    private val tapdWebhookSecret: String = ""

    override fun webhook(
        body: Map<String, Any>
    ): Result<Boolean> {
        if (tapdWebhookSecret.isNotBlank()) {
            val secret = body[TapdWebhookConstant.TAPD_KEY_SECRET]?.toString()
            if (secret != tapdWebhookSecret) {
                logger.warn("the secret of tapd webhook is illegal")
                throw InvalidParamException(
                    message = "secret illegal",
                    params = arrayOf("secret")
                )
            }
        }
        return dispatch(body)
    }

    private fun dispatch(body: Map<String, Any>): Result<Boolean> {
        logger.info("Receive TapdWebhook|${JsonUtil.toJson(body, false)}")
        val rawEvent = body[TAPD_KEY_EVENT]?.toString() ?: run {
            logger.warn("Tapd webhook missing event field")
            return Result(false)
        }
        val parts = rawEvent.split(TAPD_EVENT_SEPARATOR)
        if (parts.size != 2) {
            logger.warn("Tapd webhook invalid event format|event=$rawEvent")
            return Result(false)
        }
        val workspaceId = body[TAPD_KEY_WORKSPACE_ID]?.toString() ?: run {
            logger.warn("Tapd webhook missing workspace_id|event=$rawEvent")
            return Result(false)
        }
        simpleDispatcher.dispatch(
            TapdWebhookRequestEvent(
                workspaceId = workspaceId,
                eventType = parts[0].lowercase(),
                eventAction = parts[1].lowercase(),
                rawEvent = rawEvent,
                triggerUser = body[TAPD_KEY_CURRENT_USER]?.toString().orEmpty(),
                body = body.mapValues { it.value }
            )
        )
        return Result(true)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExternalTapdEventResourceImpl::class.java)
    }
}
