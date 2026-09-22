package com.tencent.devops.process.webhook.pojo

import com.tencent.devops.process.webhook.enums.MatchStatus

data class WebhookAtomResponse(
    val matchStatus: MatchStatus,
    val outputVars: Map<String, Any> = emptyMap(),
    val failedReason: String? = null
)
