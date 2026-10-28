package com.tencent.devops.common.pipeline

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "模板引用解析结果")
data class ResolvedTemplate(
    @get:Schema(title = "模板ID", required = true)
    val templateId: String,
    @get:Schema(title = "模板版本", required = true)
    val version: Long,
    @get:Schema(title = "模板版本名称", required = false)
    val versionName: String?,
    @get:Schema(title = "模板文件commitId,仅分支引用时有值", required = false)
    val commitId: String? = null,
    @get:Schema(title = "模板文件blobId,仅分支引用时有值", required = false)
    val blobId: String? = null
)
