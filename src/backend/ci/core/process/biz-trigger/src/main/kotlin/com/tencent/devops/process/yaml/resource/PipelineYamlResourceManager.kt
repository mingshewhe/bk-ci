/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.devops.process.yaml.resource

import com.tencent.devops.process.pojo.pipeline.DeployPipelineResult
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlBranchActionReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlDeployReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlPullRequestReq
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

@Service
class PipelineYamlResourceManager @Autowired constructor(
    private val pipelineYamlResourceClient: PipelineYamlResourceClient,
    @Lazy private val pTemplateYamlResourceClient: PTemplateYamlResourceClient
) {
    fun createYamlPipeline(
        userId: String,
        projectId: String,
        request: PipelineYamlDeployReq,
        isTemplate: Boolean
    ): DeployPipelineResult {
        return getService(isTemplate).createYamlPipeline(
            userId = userId,
            projectId = projectId,
            request = request
        )
    }

    fun updateYamlPipeline(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlDeployReq,
        isTemplate: Boolean
    ): DeployPipelineResult {
        return getService(isTemplate).updateYamlPipeline(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = request
        )
    }

    fun updateBranchAction(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlBranchActionReq,
        isTemplate: Boolean
    ) {
        return getService(isTemplate).updateBranchAction(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = request
        )
    }

    fun deletePipeline(
        userId: String,
        projectId: String,
        pipelineId: String,
        isTemplate: Boolean
    ) {
        return getService(isTemplate).deletePipeline(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId
        )
    }

    fun getPipelineName(
        projectId: String,
        pipelineId: String,
        isTemplate: Boolean
    ): String? {
        return getService(isTemplate).getPipelineName(
            projectId = projectId,
            pipelineId = pipelineId
        )
    }

    fun existsReleaseVersion(
        projectId: String,
        pipelineId: String,
        isTemplate: Boolean
    ): Boolean {
        return getService(isTemplate).existsReleaseVersion(
            projectId = projectId,
            pipelineId = pipelineId
        )
    }

    /**
     * 合并请求完成
     */
    fun completePullRequest(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlPullRequestReq,
        isTemplate: Boolean
    ) {
        getService(isTemplate).completePullRequest(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = request
        )
    }

    fun getService(isTemplate: Boolean): IPipelineYamlResourceService {
        return if (isTemplate) {
            pTemplateYamlResourceClient
        } else {
            pipelineYamlResourceClient
        }
    }
}
