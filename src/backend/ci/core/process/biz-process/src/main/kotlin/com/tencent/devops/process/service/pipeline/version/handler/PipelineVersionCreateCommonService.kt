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

import com.tencent.devops.process.pojo.pipeline.PipelineResourceOnlyVersion
import com.tencent.devops.process.pojo.pipeline.PipelineResourceVersion
import com.tencent.devops.process.pojo.pipeline.PipelineYamlFileReleaseReq
import com.tencent.devops.process.pojo.pipeline.PipelineYamlFileReleaseResult
import com.tencent.devops.process.service.pipeline.version.PipelineVersionCreateContext
import com.tencent.devops.process.service.pipeline.version.PipelineVersionGenerator
import com.tencent.devops.process.service.pipeline.version.PipelineVersionPersistenceService
import com.tencent.devops.process.yaml.PipelineYamlFacadeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线版本创建通用服务
 */
@Service
class PipelineVersionCreateCommonService @Autowired constructor(
    private val pipelineYamlFacadeService: PipelineYamlFacadeService,
    private val pipelineVersionGenerator: PipelineVersionGenerator,
    private val pipelineVersionPersistenceService: PipelineVersionPersistenceService
) {

    /**
     * 创建流水线
     */
    fun initializeTemplate(context: PipelineVersionCreateContext): PipelineResourceOnlyVersion {
        with(context) {
            val resourceOnlyVersion = pipelineVersionGenerator.getDefaultVersion(
                versionStatus = pipelineResourceWithoutVersion.status,
                branchName = branchName
            )
            val pipelineResourceVersion = PipelineResourceVersion(
                pipelineResourceWithoutVersion = pipelineResourceWithoutVersion,
                pipelineResourceOnlyVersion = resourceOnlyVersion
            )
            pipelineVersionPersistenceService.initializeTemplate(
                userId = userId,
                pipelineBasicInfo = pipelineBasicInfo,
                pipelineModelBasicInfo = pipelineModelBasicInfo,
                pipelineResourceVersion = pipelineResourceVersion,
                pipelineSetting = pipelineSetting.copy(
                    version = resourceOnlyVersion.settingVersion!!
                ),
                templateInstanceBasicInfo = templateInstanceBasicInfo
            )
            return resourceOnlyVersion
        }
    }

    /**
     * 发布yaml文件
     */
    fun releaseYamlFile(
        context: PipelineVersionCreateContext,
        resourceOnlyVersion: PipelineResourceOnlyVersion
    ): PipelineYamlFileReleaseResult? {
        with(context) {
            if (!enablePac) {
                return null
            }
            val yamlFileReleaseReq = PipelineYamlFileReleaseReq(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                pipelineName = pipelineBasicInfo.pipelineName,
                version = resourceOnlyVersion.version,
                versionName = resourceOnlyVersion.versionName,
                repoHashId = yamlFileInfo!!.repoHashId,
                filePath = yamlFileInfo.filePath,
                content = pipelineResourceWithoutVersion.yaml!!,
                commitMessage = pipelineResourceWithoutVersion.description
                    ?: "update template ${pipelineBasicInfo.pipelineName}",
                targetAction = targetAction!!,
                targetBranch = branchName
            )
            return pipelineYamlFacadeService.releaseYamlFile(
                yamlFileReleaseReq = yamlFileReleaseReq
            )
        }
    }
}
