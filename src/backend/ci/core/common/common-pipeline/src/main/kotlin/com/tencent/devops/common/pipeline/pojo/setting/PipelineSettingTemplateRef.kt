package com.tencent.devops.common.pipeline.pojo.setting

import com.tencent.devops.common.pipeline.TemplateDescriptor
import com.tencent.devops.common.pipeline.enums.TemplateRefType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线设置引用的模板")
data class PipelineSettingTemplateRef(
    @get:Schema(title = "模版引用方式", required = true)
    val templateRefType: TemplateRefType,
    @get:Schema(title = "模板ID,ID方式引用时有值", required = false)
    val templateId: String? = null,
    @get:Schema(title = "模版版本名称,ID方式引用时有值", required = false)
    val templateVersionName: String? = null,
    @get:Schema(title = "模板路径,PATH方式引用时有值", required = false)
    val templatePath: String? = null,
    @get:Schema(title = "模板分支,PATH方式引用时有值,为空时使用默认分支", required = false)
    val templateRef: String? = null,
    @get:Schema(title = "流水线自定义的设置分组,未包含的分组使用模板设置", required = false)
    val settingGroups: List<PipelineSettingGroupType>? = null
) {
    fun toDescriptor() = TemplateDescriptor(
        templateRefType = templateRefType,
        templatePath = templatePath,
        templateRef = templateRef,
        templateId = templateId,
        templateVersionName = templateVersionName,
        templateVariables = null
    )
}
