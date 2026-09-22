package com.tencent.devops.process.webhook.artifact.condition

import com.tencent.devops.process.webhook.artifact.pojo.ArtifactConditionContext

/**
 * 制品触发条件（每个条件负责一个过滤维度）。
 *
 * 不匹配时可将失败原因写入 [ArtifactConditionContext.response]。
 */
interface ArtifactCondition {
    fun match(context: ArtifactConditionContext): Boolean
}
