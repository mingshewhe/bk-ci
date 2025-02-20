package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "流水线模板资源通用条件")
data class PipelineTemplateResourceCommonCondition(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "模板ID", required = false)
    val templateId: String? = null,
    @get:Schema(title = "模板名称", required = false)
    val name: String? = null,
    @get:Schema(title = "模板类型", required = false)
    val type: PipelineTemplateType? = null,
    @get:Schema(title = "配置版本号", required = false)
    val settingVersion: Int? = null,
    @get:Schema(title = "版本号", required = false)
    val version: Long? = null,
    @get:Schema(title = "版本名称", required = false)
    val versionName: String? = null,
    @get:Schema(title = "模板发布版本号", required = false)
    val versionNum: Int? = null,
    @get:Schema(title = "模板编排版本号", required = false)
    val modelVersion: Int? = null,
    @get:Schema(title = "源模板项目ID", required = false)
    val srcTemplateProjectId: String? = null,
    @get:Schema(title = "源模板ID", required = false)
    val srcTemplateId: String? = null,
    @get:Schema(title = "源模板版本", required = false)
    val srcTemplateVersion: Long? = null,
    @get:Schema(title = "模板触发器版本号", required = false)
    val triggerVersion: Int? = null,
    @get:Schema(title = "草稿来源版本", required = false)
    val draftSourceVersion: Long? = null,
    @get:Schema(title = "状态", required = false)
    val status: VersionStatus? = null,
    @get:Schema(title = "分支状态", required = false)
    val branchAction: BranchVersionAction? = null,
    @get:Schema(title = "创建人", required = false)
    val creator: String? = null,
    @get:Schema(title = "更新人", required = false)
    val updater: String? = null,
    @get:Schema(title = "发布时间", required = false)
    val releaseTime: LocalDateTime? = null,
    @get:Schema(title = "版本发布描述", required = false)
    val releaseComment: String? = null,
    @get:Schema(title = "page", required = true)
    val page: Int? = null,
    @get:Schema(title = "pageSize", required = true)
    val pageSize: Int? = null
)
