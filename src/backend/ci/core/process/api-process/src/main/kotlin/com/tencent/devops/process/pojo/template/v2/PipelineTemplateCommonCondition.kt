package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板通用条件")
data class PipelineTemplateCommonCondition(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "模板名称", required = true)
    val name: String? = null,
    @get:Schema(title = "公共/约束/自定义模式", required = true)
    val mode: String? = null,
    @get:Schema(title = "应用范畴", required = true)
    val category: String? = null,
    @get:Schema(title = "模板类型", required = true)
    val type: PipelineTemplateType? = null,
    @get:Schema(title = "是否开启PAC", required = true)
    val enablePac: Boolean? = null,
    @get:Schema(title = "最新版本号", required = true)
    val lastedVersion: Long? = null,
    @get:Schema(title = "最新版本名称", required = true)
    val lastedVersionName: String? = null,
    @get:Schema(title = "最新设置版本号", required = true)
    val lastedSettingVersion: Long? = null,
    @get:Schema(title = "模板来源", required = true)
    val source: PipelineTemplateSource? = null,
    @get:Schema(title = "是否关联研发商店", required = true)
    val storeFlag: Boolean? = null,
    @get:Schema(title = "父模板ID", required = true)
    val srcTemplateId: String? = null,
    @get:Schema(title = "父模板项目ID", required = true)
    val srcTemplateProjectId: String? = null,
    @get:Schema(title = "调试流水线数", required = true)
    val debugPipelineCount: Int? = null,
    @get:Schema(title = "实例流水线数", required = true)
    val instancePipelineCount: Int? = null,
    @get:Schema(title = "创建人", required = true)
    val creator: String? = null,
    @get:Schema(title = "更新人", required = true)
    val updater: String? = null
)
