package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "流水线模板基础信息")
open class PipelineTemplateInfo(
    @get:Schema(title = "模板ID", required = true)
    open val id: String,
    @get:Schema(title = "项目ID", required = true)
    open val projectId: String,
    @get:Schema(title = "模板名称", required = true)
    open val name: String,
    @get:Schema(title = "简介", required = true)
    open val desc: String?,
    @get:Schema(title = "公共/约束/自定义模式", required = true)
    open val mode: String,
    @get:Schema(title = "应用范畴", required = true)
    open val category: String? = null,
    @get:Schema(title = "模板类型", required = true)
    open val type: PipelineTemplateType,
    @get:Schema(title = "logo地址", required = false)
    open val logoUrl: String? = null,
    @get:Schema(title = "是否开启PAC", required = true)
    open val enablePac: Boolean,
    @get:Schema(title = "最新版本号", required = true)
    open val latestVersion: Long,
    @get:Schema(title = "最新版本状态", required = true)
    open val latestVersionStatus: VersionStatus,
    @get:Schema(title = "最新版本名称", required = false)
    open val latestVersionName: String? = null,
    @get:Schema(title = "最新设置版本号", required = false)
    open val latestSettingVersion: Int? = null,
    @get:Schema(title = "模板来源", required = true)
    open val source: PipelineTemplateSource,
    @get:Schema(title = "是否从研发商店安装至项目", required = true)
    open val storeFlag: Boolean,
    @get:Schema(title = "父模板ID", required = false)
    open val srcTemplateId: String? = null,
    @get:Schema(title = "父模板项目ID", required = false)
    open val srcTemplateProjectId: String? = null,
    @get:Schema(title = "调试流水线数", required = true)
    open val debugPipelineCount: Int? = 0,
    @get:Schema(title = "实例流水线数", required = true)
    open val instancePipelineCount: Int? = 0,
    @get:Schema(title = "创建人", required = true)
    open val creator: String,
    @get:Schema(title = "更新人", required = false)
    open val updater: String? = null,
    @get:Schema(title = "更新人", required = false)
    open val createdTime: LocalDateTime? = null,
    @get:Schema(title = "更新人", required = false)
    open val updateTime: LocalDateTime? = null
)
