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

package com.tencent.devops.common.pipeline.template

import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.ITemplateModel
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(title = "流水线模版资源")
data class PipelineTemplateResource(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "模板ID", required = true)
    val templateId: String,
    @get:Schema(title = "模板类型", required = true)
    val type: PipelineTemplateType,
    @get:Schema(title = "版本号", required = true)
    val version: Int,
    @get:Schema(title = "版本名称", required = true)
    val versionName: String,
    @get:Schema(title = "模板发布版本号", required = true)
    val versionNum: Int?,
    @get:Schema(title = "模板编排版本号", required = true)
    val modelVersion: Int?,
    @get:Schema(title = "模板触发器版本号", required = true)
    val triggerVersion: Int?,
    @get:Schema(title = "草稿来源版本", required = true)
    val draftSourceVersion: Int?,
    @get:Schema(title = "构建参数", required = false)
    val params: List<BuildFormProperty> = listOf(),
    @get:Schema(title = "原始编排,局部模版没有解析", required = true)
    val originalModel: ITemplateModel,
    @get:Schema(title = "实际编排,局部模版已经全部解析成具体的流水线编排", required = true)
    val model: ITemplateModel,
    @get:Schema(title = "编排yaml", required = true)
    val yaml: String?,
    @get:Schema(title = "状态", required = true)
    val status: VersionStatus,
    @get:Schema(title = "分支状态", required = true)
    val branchAction: BranchVersionAction?,
    @get:Schema(title = "版本描述", required = true)
    val description: String?,
    @get:Schema(title = "创建人", required = true)
    val creator: String,
    @get:Schema(title = "更新人", required = true)
    val updater: String?,
    @get:Schema(title = "发布时间", required = true)
    val releaseTime: LocalDateTime
)
