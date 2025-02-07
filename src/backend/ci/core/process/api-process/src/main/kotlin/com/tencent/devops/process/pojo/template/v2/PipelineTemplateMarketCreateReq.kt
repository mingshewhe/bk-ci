package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板商店导入创建请求体")
data class PipelineTemplateMarketCreateReq(
    @get:Schema(title = "项目ID", required = true)
    override val projectId: String,
    @get:Schema(title = "创建人", required = true)
    override val creator: String,
    @get:Schema(title = "来源", required = true)
    override val source: PipelineTemplateSource,
    @get:Schema(title = "研发商店模板ID", required = true)
    val marketTemplateId: String,
) : PipelineTemplateBasicCreateReq(
    projectId = projectId,
    creator = creator,
    source = source
){
    companion object{
        const val SOURCE = "MARKET"
    }
}
