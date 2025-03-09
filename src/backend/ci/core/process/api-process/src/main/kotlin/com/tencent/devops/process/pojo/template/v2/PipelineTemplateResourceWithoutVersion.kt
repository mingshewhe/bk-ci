package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模版资源没有版本信息")
data class PipelineTemplateResourceWithoutVersion(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "模板ID", required = true)
    val templateId: String,
    @get:Schema(title = "模板类型", required = true)
    val type: PipelineTemplateType,
    @get:Schema(title = "源模板项目ID", required = false)
    val srcTemplateProjectId: String? = null,
    @get:Schema(title = "源模板ID", required = false)
    val srcTemplateId: String? = null,
    @get:Schema(title = "源模板版本", required = false)
    val srcTemplateVersion: Long? = null,
    @get:Schema(title = "构建参数", required = false)
    val params: List<BuildFormProperty>? = emptyList(),
    @get:Schema(title = "编排", required = false)
    val model: ITemplateModel?,
    @get:Schema(title = "编排yaml", required = false)
    val yaml: String?
)
