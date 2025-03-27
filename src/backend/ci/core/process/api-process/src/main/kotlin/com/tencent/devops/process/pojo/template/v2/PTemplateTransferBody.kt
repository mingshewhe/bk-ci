package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.common.pipeline.template.ITemplateModel
import io.swagger.v3.oas.annotations.media.Schema

data class PTemplateTransferBody (
    @get:Schema(title = "流水线模板编排", required = true)
    val templateModel: ITemplateModel?,
    @get:Schema(title = "流水线模板配置", required = true)
    val templateSetting: PipelineSetting?,
    @get:Schema(title = "流水线模板YAML", required = true)
    val yaml: String?
)
