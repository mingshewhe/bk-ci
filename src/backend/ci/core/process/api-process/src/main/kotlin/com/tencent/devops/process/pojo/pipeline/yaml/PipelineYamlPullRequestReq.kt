package com.tencent.devops.process.pojo.pipeline.yaml

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "PAC 合并请求完成请求")
data class PipelineYamlPullRequestReq(
    @get:Schema(title = "合并请求 ID", required = true)
    val pullRequestId: Long,
    @get:Schema(title = "合并请求 URL", required = true)
    val pullRequestUrl: String,
    @get:Schema(title = "合并请求编号", required = true)
    val pullRequestNumber: Int,
    @get:Schema(title = "是否已合并", required = true)
    val merged: Boolean,
    @get:Schema(title = "异常信息", required = false)
    val errorMessage: String? = null
)
