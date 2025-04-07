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

package com.tencent.devops.process.pojo.pipeline.version

import com.tencent.devops.common.pipeline.enums.CodeTargetAction
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.BuildNo
import com.tencent.devops.process.pojo.pipeline.PipelineYamlFileInfo
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模版实例创建请求")
data class PipelineTemplateInstanceCreateReq(
    @get:Schema(title = "项目ID", required = false)
    val projectId: String,
    @get:Schema(title = "模版ID", required = false)
    val templateId: String,
    @get:Schema(title = "模版版本", required = false)
    val templateVersion: Long,
    @get:Schema(title = "流水线名称", required = false)
    val pipelineName: String,
    @get:Schema(title = "构建号（推荐版本号）", required = false)
    val buildNo: BuildNo?,
    @get:Schema(title = "流水线变量列表", required = false)
    val param: List<BuildFormProperty>? = null,
    @get:Schema(title = "是否使用模版设置", required = false)
    val useTemplateSettings: Boolean,
    @get:Schema(title = "是否开启PAC", required = true)
    val enablePac: Boolean = false,
    @get:Schema(title = "yaml文件分支信息", required = true)
    val yamlFileInfo: PipelineYamlFileInfo? = null,
    @get:Schema(title = "发布操作", required = false)
    val targetAction: CodeTargetAction? = null,
    @get:Schema(title = "分支名,发布时指定的分支或者代码库推送的分支", required = false)
    val targetBranch: String? = null,
) : PipelineVersionCreateReq
