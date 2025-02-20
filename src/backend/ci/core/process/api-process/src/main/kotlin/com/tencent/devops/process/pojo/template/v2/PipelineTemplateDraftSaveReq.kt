package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.ITemplateModel
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板更新请求体")
data class PipelineTemplateDraftSaveReq(
    @get:Schema(title = "项目Id", required = true)
    val projectId: String,
    @get:Schema(title = "模板Id", required = true)
    val templateId: String,
    @get:Schema(title = "草稿源版本", required = true)
    val draftSourceVersion: Long,
    @get:Schema(title = "模板名称", required = true)
    val name: String,
    @get:Schema(title = "简介", required = false)
    val desc: String? = null,
    @get:Schema(title = "logo地址", required = false)
    val logoUrl: String? = null,
    @get:Schema(title = "是否开启PAC", required = false)
    val enablePac: Boolean? = null,
    @get:Schema(title = "构建参数", required = false)
    val params: List<BuildFormProperty>? = null,
    @get:Schema(title = "原始编排,局部模版没有解析", required = true)
    val originalModel: ITemplateModel,
    @get:Schema(title = "模板配置", required = false)
    val templateSetting: PipelineTemplateSetting? = null,
    @get: Schema(title = "编排yaml", required = true)
    val yaml: String,
    @get:Schema(title = "操作人", required = true)
    val operator: String
)
