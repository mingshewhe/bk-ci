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

package com.tencent.devops.process.service.template.v2.version

import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.process.pojo.pipeline.PipelineYamlFileInfo
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceWithoutVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 模版版本上下文
 */
@Schema(title = "模版版本上下文")
data class PipelineTemplateVersionContext(
    val userId: String,
    val projectId: String,
    val templateId: String,
    @get:Schema(title = "模版版本变更动作", required = true)
    val versionAction: PipelineVersionAction,
    @get:Schema(title = "模版信息", required = true)
    val pipelineTemplateInfo: PipelineTemplateInfo,
    @get:Schema(title = "模版编排", required = true)
    val pTemplateResourceWithoutVersion: PTemplateResourceWithoutVersion,
    @get:Schema(title = "模版设置", required = true)
    val pipelineTemplateSetting: PipelineSetting,
    @get:Schema(title = "是否开启PAC", required = true)
    val enablePac: Boolean = false,
    @get:Schema(title = "yaml文件分支信息", required = true)
    val yamlFileInfo: PipelineYamlFileInfo? = null,
    @get:Schema(title = "是否需要推送到远端仓库", required = true)
    val push: Boolean = false,
    @get:Schema(title = "是否是merge主干", required = true)
    val merged: Boolean = false
)
