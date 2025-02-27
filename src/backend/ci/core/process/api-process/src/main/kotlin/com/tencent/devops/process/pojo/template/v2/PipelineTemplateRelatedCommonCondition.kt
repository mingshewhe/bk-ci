package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.BuildNo
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "模板与流水线关联实体")
data class PipelineTemplateRelatedCommonCondition(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "模板Id", required = true)
    val templateId: String? = null,
    @get:Schema(title = "流水线Id", required = true)
    val pipelineId: String? = null,
    @get:Schema(title = "模板版本号", required = true)
    val version: Long? = null,
    @get:Schema(title = "模板版本名称", required = true)
    val versionName: String? = null,
    @get:Schema(title = "实例化类型", required = false)
    val instanceType: PipelineInstanceTypeEnum? = null,
    @get:Schema(title = "父模板ID", required = false)
    val rootTemplateId: String? = null,
    @get:Schema(title = "是否软删除", required = true)
    val deleted: Boolean? = null,
    @get:Schema(title = "创建人", required = true)
    val creator: String? = null,
    @get:Schema(title = "更新人", required = true)
    val updater: String? = null
)
