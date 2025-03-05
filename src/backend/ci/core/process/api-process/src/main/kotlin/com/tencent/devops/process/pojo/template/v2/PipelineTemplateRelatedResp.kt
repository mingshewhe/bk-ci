package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.process.pojo.template.TemplatePipelineStatus
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "模板与流水线关联返回体")
data class PipelineTemplateRelatedResp(
    @get:Schema(title = "模板id", required = false)
    val templateId: String,
    @get:Schema(title = "版本名称", required = false)
    val versionName: String,
    @get:Schema(title = "版本", required = false)
    val version: Long,
    @get:Schema(title = "流水线id", required = false)
    val pipelineId: String,
    @get:Schema(title = "流水线名称", required = false)
    val pipelineName: String,
    @get:Schema(title = "更新时间", required = false)
    val updateTime: Long,
    @get:Schema(title = "是否有编辑权限", required = false)
    val hasPermission: Boolean,
    @get:Schema(title = "流水线模板状态", required = false)
    val status: TemplatePipelineStatus,
    @get:Schema(title = "是否开启PAC", required = false)
    val enabledPac: Boolean,
    @get:Schema(title = "模板实例化错误信息", required = false)
    val instanceErrorInfo: String? = null,
    @get:Schema(title = "更新人", required = false)
    val updater: String? = null
)
