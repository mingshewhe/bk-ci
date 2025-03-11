package com.tencent.devops.common.pipeline.pojo.element

import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "step模板模型")
data class StepTemplateElement(
    @get:Schema(title = "任务名称", required = true)
    override val name: String = "step模版",
    @get:Schema(title = "id", required = false)
    override var id: String? = null,
    @get:Schema(title = "状态", required = false)
    override var status: String? = null,
    @get:Schema(title = "来源于模版", required = false)
    override var fromTemplate: Boolean? = true
) : Element(name, id, status) {
    companion object {
        const val classType = "stepTemplate"
    }

    override fun getClassType(): String = classType
}
