package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.common.pipeline.template.PipelineTemplateSetting
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线YAML导入请求体")
data class PipelineTemplateYamlImportReq(
    @get:Schema(title = "模板名称", required = true)
    val name: String,
    @get:Schema(title = "简介", required = false)
    val desc: String?,
    @get:Schema(title = "构建参数", required = false)
    val params: List<BuildFormProperty> = listOf(),
    @get:Schema(title = "编排", required = true)
    val model: ITemplateModel,
    @get:Schema(title = "模板配置", required = false)
    val setting: PipelineTemplateSetting?,
    @get:Schema(title = "模板YAML", required = true)
    val yaml: String
) : PipelineTemplateVersionReq
