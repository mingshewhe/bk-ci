package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板自定义创建请求体")
data class PipelineTemplateCustomCreateReq(
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
    val desc: String?
) : PipelineTemplateBasicCreateReq(
    projectId = projectId,
    creator = creator,
    source = source,
    type = type
) {
    companion object {
        const val SOURCE = "CUSTOM"
    }
}
