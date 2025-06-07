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

package com.tencent.devops.process.service.pipeline.version.handler

import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.process.engine.control.lock.PipelineModelLock
import com.tencent.devops.process.pojo.pipeline.DeployPipelineResult
import com.tencent.devops.process.service.pipeline.version.PipelineVersionCreateContext
import com.tencent.devops.process.service.pipeline.version.PipelineVersionGenerator
import com.tencent.devops.process.service.pipeline.version.PipelineVersionPersistenceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineTemplateInstanceHandler @Autowired constructor(
    private val redisOperation: RedisOperation,
    private val pipelineVersionGenerator: PipelineVersionGenerator,
    private val pipelineVersionPersistenceService: PipelineVersionPersistenceService
) : PipelineVersionCreateHandler {
    override fun support(context: PipelineVersionCreateContext) =
        context.versionAction == PipelineVersionAction.TEMPLATE_INSTANCE

    override fun handle(context: PipelineVersionCreateContext): DeployPipelineResult {
        with(context) {
            if (templateInstanceBasicInfo == null) {
                throw IllegalArgumentException("templateInstanceBasicInfo is null")
            }
            if (enablePac) {
                if (targetAction == null) {
                    throw IllegalArgumentException("targetAction is null")
                }
                if (yamlFileInfo == null) {
                    throw IllegalArgumentException("yamlFileInfo is null")
                }
                if (pipelineResourceWithoutVersion.yaml == null) {
                    throw IllegalArgumentException("yaml is null")
                }
            }
            val lock = PipelineModelLock(redisOperation, pipelineId)
            try {
                lock.lock()
                return doHandle()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun PipelineVersionCreateContext.doHandle(): DeployPipelineResult {
        val resourceOnlyVersion = if (pipelineInfo == null) {
            val resourceOnlyVersion = pipelineVersionGenerator.getDefaultVersion(
                versionStatus = pipelineResourceWithoutVersion.status,
                branchName = branchName
            )
            pipelineVersionPersistenceService.initializeTemplate(
                context = this, resourceOnlyVersion = resourceOnlyVersion
            )
            resourceOnlyVersion
        } else {
            val resourceOnlyVersion = pipelineVersionGenerator.generateInstanceVersion(
                projectId = projectId,
                pipelineId = pipelineId,
                newModel = pipelineResourceWithoutVersion.model,
                enablePac = enablePac,
                repoHashId = yamlFileInfo?.repoHashId,
                targetAction = targetAction,
                targetBranch = branchName,
                templateId = templateInstanceBasicInfo!!.templateId,
                templateVersion = templateInstanceBasicInfo.templateVersion,
                useTemplateSetting = templateInstanceBasicInfo.useTemplateSetting
            )
            if (pipelineResourceWithoutVersion.status == VersionStatus.RELEASED) {
                pipelineVersionPersistenceService.createReleaseVersion(
                    context = this, resourceOnlyVersion = resourceOnlyVersion
                )
            } else {
                pipelineVersionPersistenceService.createBranchVersion(
                    context = this, resourceOnlyVersion = resourceOnlyVersion
                )
            }
            resourceOnlyVersion
        }

        // 推送文件
        val yamlFileReleaseResult = enablePac.takeIf { it }?.let {
            pipelineVersionPersistenceService.releaseYamlFile(
                context = this,
                resourceOnlyVersion = resourceOnlyVersion
            )
        }

        return DeployPipelineResult(
            pipelineId = pipelineId,
            pipelineName = pipelineSettingWithoutVersion.pipelineName,
            version = resourceOnlyVersion.version,
            versionNum = resourceOnlyVersion.versionNum,
            versionName = resourceOnlyVersion.versionName,
            pullRequestId = yamlFileReleaseResult?.pullRequestId,
            targetUrl = yamlFileReleaseResult?.pullRequestUrl
        )
    }
}
