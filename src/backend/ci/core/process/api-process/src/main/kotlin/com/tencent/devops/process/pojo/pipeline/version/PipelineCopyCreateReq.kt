package com.tencent.devops.process.pojo.pipeline.version

import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.process.pojo.PipelineCopy
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线复制创建请求")
data class PipelineCopyCreateReq(
    @get:Schema(title = "源流水线ID", required = true)
    val pipelineId: String,
    @get:Schema(title = "复制后的流水线创建信息", required = true)
    val pipelineCopy: PipelineCopy,
    @get:Schema(title = "流水线渠道", required = true)
    val channelCode: ChannelCode,
    @get:Schema(title = "是否校验权限", required = false)
    val checkPermission: Boolean = true
) : PipelineVersionCreateReq
