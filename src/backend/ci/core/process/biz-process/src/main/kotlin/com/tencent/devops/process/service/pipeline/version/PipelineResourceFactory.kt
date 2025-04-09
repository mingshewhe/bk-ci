/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.service.pipeline.version

import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.dialect.IPipelineDialect
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.element.trigger.ManualTriggerElement
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.pojo.pipeline.PipelineBasicInfo
import com.tencent.devops.process.pojo.pipeline.PipelineModelBasicInfo
import com.tencent.devops.process.pojo.pipeline.PipelineYamlVo
import com.tencent.devops.project.api.service.ServiceAllocIdResource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineResourceFactory @Autowired constructor(
    private val client: Client,
    private val pipelineRepositoryService: PipelineRepositoryService
) {

    fun createPipelineBasicInfo(
        projectId: String,
        pipelineId: String,
        channelCode: ChannelCode,
        model: Model,
        pipelineDisable: Boolean? = false
    ): PipelineBasicInfo {
        val id = client.get(ServiceAllocIdResource::class).generateSegmentId("PIPELINE_INFO").data
        return PipelineBasicInfo(
            projectId = projectId,
            pipelineId = pipelineId,
            pipelineName = model.name,
            pipelineDesc = model.desc ?: model.name,
            channelCode = channelCode,
            id = id,
            pipelineDisable = pipelineDisable
        )
    }

    fun createPipelineModelBasicInfo(
        userId: String,
        projectId: String,
        pipelineId: String,
        model: Model,
        create: Boolean = true,
        versionStatus: VersionStatus? = VersionStatus.RELEASED,
        channelCode: ChannelCode,
        yamlInfo: PipelineYamlVo? = null,
        pipelineDialect: IPipelineDialect? = null
    ): PipelineModelBasicInfo {
        val triggerContainer = model.getTriggerContainer()
        var canManualStartup = false
        var canElementSkip = false
        run lit@{
            triggerContainer.elements.forEach {
                if (it is ManualTriggerElement && it.elementEnabled()) {
                    canManualStartup = true
                    canElementSkip = it.canElementSkip ?: false
                    return@lit
                }
            }
            // 保存时将别名name补全为id
            triggerContainer.params.forEach { param ->
                param.name = param.name ?: param.id
            }
        }
        val buildNo = triggerContainer.buildNo?.apply {
            // #10958 每次存储model都需要忽略当前的推荐版本号值，在返回前端时重查
            currentBuildNo = null
        }
        val modelTasks = pipelineRepositoryService.initModel(
            model = model,
            projectId = projectId,
            pipelineId = pipelineId,
            userId = userId,
            create = create,
            versionStatus = versionStatus,
            channelCode = channelCode,
            yamlInfo = yamlInfo,
            pipelineDialect = pipelineDialect
        )
        return PipelineModelBasicInfo(
            canManualStartup = canManualStartup,
            canElementSkip = canElementSkip,
            taskCount = model.taskCount(),
            param = triggerContainer.params,
            buildNo = buildNo,
            events = model.events,
            modelTasks = modelTasks,
            staticViews = model.staticViews
        )
    }
}
