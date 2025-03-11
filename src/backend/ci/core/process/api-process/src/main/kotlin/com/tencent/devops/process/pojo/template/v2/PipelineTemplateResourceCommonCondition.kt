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
    @get:Schema(title = "模板ID", required = true)
    val templateId: String? = null,
    @get:Schema(title = "模板类型", required = true)
    val type: PipelineTemplateType? = null,
    @get:Schema(title = "版本号", required = true)
    val version: Int? = null,
    @get:Schema(title = "版本名称", required = true)
    val versionName: String? = null,
    @get:Schema(title = "模板发布版本号", required = true)
    val versionNum: Int?,
    @get:Schema(title = "模板编排版本号", required = true)
    val modelVersion: Int?,
    @get:Schema(title = "模板触发器版本号", required = true)
    val triggerVersion: Int?,
    @get:Schema(title = "草稿来源版本", required = true)
    val draftSourceVersion: Int?,
    @get:Schema(title = "状态", required = true)
    val status: VersionStatus? = null,
    @get:Schema(title = "分支状态", required = true)
    val branchAction: BranchVersionAction? = null,
    @get:Schema(title = "创建人", required = true)
    val creator: String? = null,
    @get:Schema(title = "更新人", required = true)
    val updater: String? = null,
    @get:Schema(title = "发布时间", required = true)
    val releaseTime: LocalDateTime? = null
)
