package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.VersionStatus
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板更新请求体")
data class PipelineTemplateInfoUpdateInfo(
    @get:Schema(title = "模板名称", required = true)
    val name: String? = null,
    @get:Schema(title = "简介", required = true)
    val desc: String? = null,
    @get:Schema(title = "应用范畴", required = true)
    val category: String? = null,
    @get:Schema(title = "logo地址", required = true)
    val logoUrl: String? = null,
    @get:Schema(title = "是否开启PAC", required = true)
    val enablePac: Boolean? = null,
    @get:Schema(title = "最新版本号", required = true)
    val latestVersion: Long? = null,
    @get:Schema(title = "最新版本名称", required = true)
    val latestVersionName: String? = null,
    @get:Schema(title = "最新版本状态", required = true)
    val latestVersionStatus: VersionStatus? = null,
    @get:Schema(title = "最新设置版本号", required = true)
    val latestSettingVersion: Int? = null,
    @get:Schema(title = "是否从研发商店安装至项目", required = true)
    val storeFlag: Boolean? = null,
    @get:Schema(title = "调试流水线数", required = true)
    val debugPipelineCount: Int? = null,
    @get:Schema(title = "实例流水线数", required = true)
    val instancePipelineCount: Int? = null,
    @get:Schema(title = "更新人", required = true)
    val updater: String
)
