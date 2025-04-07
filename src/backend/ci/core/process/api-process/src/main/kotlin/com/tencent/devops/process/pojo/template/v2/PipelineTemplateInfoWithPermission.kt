package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "流水线模板基础信息-权限")
data class PipelineTemplateInfoWithPermission(
    @get:Schema(title = "模板ID", required = true)
    override val id: String,
    @get:Schema(title = "项目ID", required = true)
    override val projectId: String,
    @get:Schema(title = "模板名称", required = true)
    override val name: String,
    @get:Schema(title = "简介", required = false)
    override val desc: String?,
    @get:Schema(title = "公共/约束/自定义模式", required = true)
    override val mode: String,
    @get:Schema(title = "应用范畴", required = false)
    override val category: String? = null,
    @get:Schema(title = "模板类型", required = true)
    override val type: PipelineTemplateType,
    @get:Schema(title = "logo地址", required = false)
    override val logoUrl: String? = null,
    @get:Schema(title = "是否开启PAC", required = true)
    override val enablePac: Boolean,
    @get:Schema(title = "最新版本号", required = true)
    override val latestVersion: Long,
    @get:Schema(title = "最新版本状态", required = true)
    override val latestVersionStatus: VersionStatus,
    @get:Schema(title = "最新版本名称", required = false)
    override val latestVersionName: String? = null,
    @get:Schema(title = "最新设置版本号", required = false)
    override val latestSettingVersion: Int?,
    @get:Schema(title = "模板来源", required = true)
    override val source: PipelineTemplateSource,
    @get:Schema(title = "来源名称", required = true)
    override val sourceName: String? = null,
    @get:Schema(title = "是否从研发商店安装至项目", required = true)
    override val storeFlag: Boolean,
    @get:Schema(title = "父模板ID", required = false)
    override val srcTemplateId: String? = null,
    @get:Schema(title = "父模板项目ID", required = false)
    override val srcTemplateProjectId: String? = null,
    @get:Schema(title = "调试流水线数", required = true)
    override val debugPipelineCount: Int? = 0,
    @get:Schema(title = "实例流水线数", required = true)
    override val instancePipelineCount: Int? = 0,
    @get:Schema(title = "创建人", required = true)
    override val creator: String,
    @get:Schema(title = "更新人", required = false)
    override val updater: String? = null,
    @get:Schema(title = "更新人", required = false)
    override val createdTime: LocalDateTime? = null,
    @get:Schema(title = "更新人", required = false)
    override val updateTime: LocalDateTime? = null,
    @get:Schema(title = "是否有模版查看权限", required = true)
    val canView: Boolean,
    @get:Schema(title = "是否有模版编辑权限", required = true)
    val canEdit: Boolean,
    @get:Schema(title = "是否有模版删除权限", required = true)
    val canDelete: Boolean
) : PipelineTemplateInfo(
    id = id,
    projectId = projectId,
    name = name,
    desc = desc,
    mode = mode,
    category = category,
    type = type,
    logoUrl = logoUrl,
    enablePac = enablePac,
    latestVersion = latestVersion,
    latestVersionStatus = latestVersionStatus,
    latestVersionName = latestVersionName,
    latestSettingVersion = latestSettingVersion,
    source = source,
    storeFlag = storeFlag,
    srcTemplateId = srcTemplateId,
    srcTemplateProjectId = srcTemplateProjectId,
    debugPipelineCount = debugPipelineCount,
    instancePipelineCount = instancePipelineCount,
    creator = creator,
    updater = updater
) {
    companion object {
        fun buildTemplateInfoWithPermission(
            templateInfo: PipelineTemplateInfo,
            canView: Boolean,
            canEdit: Boolean,
            canDelete: Boolean
        ): PipelineTemplateInfoWithPermission {
            with(templateInfo) {
                return PipelineTemplateInfoWithPermission(
                    id = id,
                    projectId = projectId,
                    name = name,
                    desc = desc,
                    mode = mode,
                    category = category,
                    type = type,
                    logoUrl = logoUrl,
                    enablePac = enablePac,
                    latestVersion = latestVersion,
                    latestVersionStatus = latestVersionStatus,
                    latestVersionName = latestVersionName,
                    latestSettingVersion = latestSettingVersion,
                    source = source,
                    sourceName = sourceName,
                    storeFlag = storeFlag,
                    srcTemplateId = srcTemplateId,
                    srcTemplateProjectId = srcTemplateProjectId,
                    debugPipelineCount = debugPipelineCount,
                    instancePipelineCount = instancePipelineCount,
                    creator = creator,
                    updater = updater,
                    createdTime = createdTime,
                    updateTime = updateTime,
                    canView = canView,
                    canEdit = canEdit,
                    canDelete = canDelete
                )
            }
        }
    }
}
