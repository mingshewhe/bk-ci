package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板基础信息-权限")
data class PipelineTemplateInfoWithPermission(
    @get:Schema(title = "模板ID", required = true)
    override val id: String,
    @get:Schema(title = "项目ID", required = true)
    override val projectId: String,
    @get:Schema(title = "模板名称", required = true)
    override val name: String,
    @get:Schema(title = "简介", required = true)
    override val desc: String?,
    @get:Schema(title = "公共/约束/自定义模式", required = true)
    override val mode: String,
    @get:Schema(title = "应用范畴", required = true)
    override val category: String? = null,
    @get:Schema(title = "模板类型", required = true)
    override val type: PipelineTemplateType,
    @get:Schema(title = "logo地址", required = true)
    override val logoUrl: String? = null,
    @get:Schema(title = "是否开启PAC", required = true)
    override val enablePac: Boolean,
    @get:Schema(title = "最新版本号", required = true)
    override val lastedVersion: Long,
    @get:Schema(title = "最新版本状态", required = true)
    override val lastedVersionStatus: VersionStatus,
    @get:Schema(title = "最新版本名称", required = true)
    override val lastedVersionName: String? = null,
    @get:Schema(title = "最新设置版本号", required = true)
    override val lastedSettingVersion: Int,
    @get:Schema(title = "模板来源", required = true)
    override val source: PipelineTemplateSource,
    @get:Schema(title = "是否从研发商店安装至项目", required = true)
    override val storeFlag: Boolean,
    @get:Schema(title = "父模板ID", required = true)
    override val srcTemplateId: String? = null,
    @get:Schema(title = "父模板项目ID", required = true)
    override val srcTemplateProjectId: String? = null,
    @get:Schema(title = "调试流水线数", required = true)
    override val debugPipelineCount: Int? = 0,
    @get:Schema(title = "实例流水线数", required = true)
    override val instancePipelineCount: Int? = 0,
    @get:Schema(title = "创建人", required = true)
    override val creator: String,
    @get:Schema(title = "更新人", required = true)
    override val updater: String? = null,
    @get:Schema(title = "是否有模版查看权限", required = true)
    val canView: Boolean? = null,
    @get:Schema(title = "是否有模版编辑权限", required = true)
    val canEdit: Boolean? = null,
    @get:Schema(title = "是否有模版删除权限", required = true)
    val canDelete: Boolean? = null
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
    lastedVersion = lastedVersion,
    lastedVersionStatus = lastedVersionStatus,
    lastedVersionName = lastedVersionName,
    lastedSettingVersion = lastedSettingVersion,
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
                    lastedVersion = lastedVersion,
                    lastedVersionStatus = lastedVersionStatus,
                    lastedVersionName = lastedVersionName,
                    lastedSettingVersion = lastedSettingVersion,
                    source = source,
                    storeFlag = storeFlag,
                    srcTemplateId = srcTemplateId,
                    srcTemplateProjectId = srcTemplateProjectId,
                    debugPipelineCount = debugPipelineCount,
                    instancePipelineCount = instancePipelineCount,
                    creator = creator,
                    updater = updater,
                    canView = canView,
                    canEdit = canEdit,
                    canDelete = canDelete
                )
            }
        }
    }
}
