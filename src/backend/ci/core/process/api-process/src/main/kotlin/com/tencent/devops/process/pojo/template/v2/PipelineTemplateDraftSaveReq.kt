package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.common.pipeline.template.ITemplateModel
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板更新请求体")
data class PipelineTemplateDraftSaveReq(
    @get:Schema(title = "模板Id", required = true)
    val templateId: String,
    @get:Schema(title = "草稿源版本", required = true)
    val baseVersion: Int,
    @get:Schema(title = "logo地址", required = false)
    val logoUrl: String? = null,
    @get:Schema(title = "是否开启PAC", required = false)
    val enablePac: Boolean? = null,
    @get:Schema(title = "构建参数", required = false)
    val params: List<BuildFormProperty>? = null,
    @get:Schema(title = "原始编排,局部模版没有解析", required = false)
    val model: ITemplateModel? = null,
    @get:Schema(title = "模板配置", required = false)
    val templateSetting: PipelineSetting? = null,
    @get: Schema(title = "编排yaml", required = false)
    val yaml: String? = null,
    @get:Schema(title = "存储格式", required = true)
    val storageType: PipelineStorageType = PipelineStorageType.MODEL
) : PipelineTemplateVersionReq
