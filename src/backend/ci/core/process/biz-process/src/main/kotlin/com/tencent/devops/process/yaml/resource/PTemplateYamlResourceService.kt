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
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlFileInfo
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlPullRequestReq
import com.tencent.devops.process.service.template.v2.PipelineTemplateFacadeService
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.yaml.common.YamlFileUtils
import org.springframework.stereotype.Service

/**
 * yaml文件对应的模版操作服务类
 */
@Service
class PTemplateYamlResourceService(
    private val pipelineTemplateFacadeService: PipelineTemplateFacadeService,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService
) {
    fun createYamlPipeline(
        userId: String,
        projectId: String,
        request: PipelineYamlDeployReq
    ): DeployPipelineResult {
        val yamlFileInfo = PipelineYamlFileInfo(repoHashId = request.repoHashId, filePath = request.filePath)
        val yamlFileName = YamlFileUtils.getCiTemplateName(request.filePath)
        val deployTemplateResult = pipelineTemplateFacadeService.createYamlTemplate(
            userId = userId,
            projectId = projectId,
            yaml = request.yaml,
            yamlFileName = yamlFileName,
            branchName = request.ref,
            isDefaultBranch = request.ref == request.defaultBranch,
            description = request.commitMsg,
            yamlFileInfo = yamlFileInfo
        )
        return with(deployTemplateResult) {
            DeployPipelineResult(
                pipelineId = templateId,
                pipelineName = templateName,
                version = version.toInt(),
                versionNum = versionNum,
                versionName = versionName,
                targetUrl = targetUrl,
                yamlInfo = yamlInfo
            )
        }
    }

    fun updateYamlPipeline(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlDeployReq
    ): DeployPipelineResult {
        val yamlFileInfo = PipelineYamlFileInfo(repoHashId = request.repoHashId, filePath = request.filePath)
        val yamlFileName = YamlFileUtils.getCiTemplateName(request.filePath)
        val deployTemplateResult = pipelineTemplateFacadeService.updateYamlTemplate(
            userId = userId,
            projectId = projectId,
            templateId = pipelineId,
            yaml = request.yaml,
            yamlFileName = yamlFileName,
            branchName = request.ref,
            isDefaultBranch = request.ref == request.defaultBranch,
            description = request.commitMsg,
            yamlFileInfo = yamlFileInfo
        )
        return with(deployTemplateResult) {
            DeployPipelineResult(
                pipelineId = templateId,
                pipelineName = templateName,
                version = version.toInt(),
                versionNum = versionNum,
                versionName = versionName,
                targetUrl = targetUrl,
                yamlInfo = yamlInfo
            )
        }
    }

    fun updateBranchAction(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlBranchActionReq
    ) {
        pipelineTemplateFacadeService.inactiveBranch(
            userId = userId,
            projectId = projectId,
            templateId = pipelineId,
            branch = request.branchName
        )
    }

    fun deletePipeline(userId: String, projectId: String, pipelineId: String) {
        pipelineTemplateFacadeService.deleteTemplate(
            userId = userId,
            projectId = projectId,
            templateId = pipelineId
        )
    }

    fun getPipelineName(projectId: String, pipelineId: String): String? {
        return pipelineTemplateInfoService.getOrNull(
            projectId = projectId,
            templateId = pipelineId
        )?.name
    }

    fun existsReleaseVersion(projectId: String, pipelineId: String): Boolean {
        return pipelineTemplateResourceService.getLatestReleasedResource(
            projectId = projectId,
            templateId = pipelineId
        ) != null
    }

    @Suppress("UNUSED_PARAMETER")
    fun completePullRequest(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlPullRequestReq
    ) {
        // 模板没有流水线实例的 PR 状态回写
    }
}
