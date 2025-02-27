package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.BuildNo
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "模板与流水线关联实体")
data class PipelineTemplateRelatedSample(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "模板Id", required = true)
    val templateId: String,
    @get:Schema(title = "模板版本号", required = true)
    val version: Long,
    @get:Schema(title = "模板版本名称", required = true)
    val versionName: String,
    @get:Schema(title = "流水线Id", required = true)
    val pipelineId: String,
    @get:Schema(title = "实例化类型", required = false)
    val instanceType: PipelineInstanceTypeEnum
)
