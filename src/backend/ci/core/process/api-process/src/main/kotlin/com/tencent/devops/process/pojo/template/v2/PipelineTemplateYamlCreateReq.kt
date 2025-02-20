package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.ITemplateModel
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板商店导入创建请求体")
data class PipelineTemplateYamlCreateReq(
    @get:Schema(title = "项目ID", required = true)
    override val projectId: String,
    @get:Schema(title = "创建人", required = true)
    override val creator: String,
    @get:Schema(title = "来源", required = true)
    override val source: PipelineTemplateSource,
    @get:Schema(title = "类型", required = true)
    override val type: PipelineTemplateType,
    @get:Schema(title = "模板名称", required = true)
    val name: String,
    @get:Schema(title = "简介", required = false)
    val desc: String?,
    @get:Schema(title = "构建参数", required = false)
    val params: List<BuildFormProperty> = listOf(),
    @get:Schema(title = "模板原始模型", required = true)
    val originalModel: ITemplateModel,
    @get:Schema(title = "模板配置", required = false)
    val setting: PipelineTemplateSetting?,
    @get:Schema(title = "模板YAML", required = true)
    val yaml: String
) : PipelineTemplateBasicCreateReq(
    projectId = projectId,
    creator = creator,
    source = source,
    type = type
) {
    companion object {
        const val SOURCE = "YAML"
    }
}
