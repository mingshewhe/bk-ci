package com.tencent.devops.process.pojo.template.v2

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板自定义创建请求体")
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "source",
    visible = true
)
@JsonSubTypes(
    JsonSubTypes.Type(value = PipelineTemplateCustomCreateReq::class, name = PipelineTemplateCustomCreateReq.SOURCE),
    JsonSubTypes.Type(value = PipelineTemplateMarketCreateReq::class, name = PipelineTemplateMarketCreateReq.SOURCE),
    JsonSubTypes.Type(PipelineTemplateRepositoryCreateReq::class, name = PipelineTemplateRepositoryCreateReq.SOURCE),
    JsonSubTypes.Type(value = PipelineTemplateYamlCreateReq::class, name = PipelineTemplateYamlCreateReq.SOURCE)
)
open class PipelineTemplateBasicCreateReq(
    @get:Schema(title = "来源", required = true)
    open val source: PipelineTemplateSource,
    @get:Schema(title = "模板Id", required = false)
    open var id: String? = null
) : PipelineTemplateVersionReq {
    fun generateId(): String {
        val templateId = UUIDUtil.generate()
        id = templateId
        return templateId
    }
}
