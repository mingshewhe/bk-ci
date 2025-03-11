package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板基础信息")
data class PipelineTemplateInfo(
    @get:Schema(title = "模板ID", required = true)
    val id: String,
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "模板名称", required = true)
    val name: String,
    @get:Schema(title = "简介", required = true)
    val desc: String?,
    @get:Schema(title = "公共/约束/自定义模式", required = true)
    val mode: String,
    @get:Schema(title = "应用范畴", required = true)
    val category: String? = null,
    @get:Schema(title = "模板类型", required = true)
    val type: PipelineTemplateType,
    @get:Schema(title = "logo地址", required = false)
    val logoUrl: String? = null,
    @get:Schema(title = "是否开启PAC", required = true)
    val enablePac: Boolean,
    @get:Schema(title = "最新发布版本号", required = true)
    val releasedVersion: Int? = null,
    @get:Schema(title = "最新发布版本名称", required = false)
    val releasedVersionName: String? = null,
    @get:Schema(title = "最新发布配置版本号", required = false)
    val releasedSettingVersion: Int? = null,
    @get:Schema(title = "模板状态", required = false)
    val latestVersionStatus: VersionStatus,
    @get:Schema(title = "模板来源", required = true)
    val source: PipelineTemplateSource,
    @get:Schema(title = "来源名称", required = true)
    val sourceName: String? = null,
    @get:Schema(title = "是否从研发商店安装至项目", required = true)
    val storeFlag: Boolean,
    @get:Schema(title = "父模板ID", required = false)
    val srcTemplateId: String? = null,
    @get:Schema(title = "父模板项目ID", required = false)
    val srcTemplateProjectId: String? = null,
    @get:Schema(title = "调试流水线数", required = true)
    val debugPipelineCount: Int? = 0,
    @get:Schema(title = "实例流水线数", required = true)
    val instancePipelineCount: Int? = 0,
    @get:Schema(title = "创建人", required = true)
    val creator: String,
    @get:Schema(title = "更新人", required = false)
    val updater: String? = null,
    @get:Schema(title = "更新人", required = false)
    val createdTime: Long? = null,
    @get:Schema(title = "更新人", required = false)
    val updateTime: Long? = null,
    @get:Schema(title = "是否有模版查看权限", required = true)
    val canView: Boolean? = null,
    @get:Schema(title = "是否有模版编辑权限", required = true)
    val canEdit: Boolean? = null,
    @get:Schema(title = "是否有模版删除权限", required = true)
    val canDelete: Boolean? = null
)
