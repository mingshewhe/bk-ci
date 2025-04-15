package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.BuildNo
import com.tencent.devops.common.pipeline.pojo.element.trigger.TimerTriggerElement
import com.tencent.devops.process.pojo.template.TemplateInstanceStatus
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板实例具体类")
data class PipelineTemplateInstanceItem(
    val id: String,
    val baseId: String,
    val projectId: String,
    val pipelineId: String,
    val pipelineName: String,
    val buildNo: BuildNo?,
    val status: TemplateInstanceStatus,
    val params: List<BuildFormProperty>?,
    val timerTrigger: TimerTriggerElement?,
    val filePath: String?,
    val errorMessage: String?,
    val creator: String,
    val modifier: String,
    val createTime: Long,
    val updateTime: Long,
)
