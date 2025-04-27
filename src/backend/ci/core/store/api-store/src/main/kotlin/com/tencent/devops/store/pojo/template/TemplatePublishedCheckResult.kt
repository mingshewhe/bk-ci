package com.tencent.devops.store.pojo.template

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "模板是否上架检查结果")
data class TemplatePublishedCheckResult(
    @get:Schema(title = "研发商店模板ID")
    val templateId: String = "",
    @get:Schema(title = "是否上架")
    val published: Boolean
)
