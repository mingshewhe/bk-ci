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

package com.tencent.devops.process.service.template.v2.version.convert

import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceWithoutVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateBranchPushReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateVersionReq
import com.tencent.devops.process.service.template.v2.PipelineTemplateGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionCreateContext
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionReqConverter
import org.springframework.stereotype.Service

/**
 * 流水线模板分支推送请求转换
 */
@Service
class PipelineTemplateBranchPushReqConverter(
    private val pipelineTemplateGenerator: PipelineTemplateGenerator,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService
) : PipelineTemplateVersionReqConverter {
    override fun support(request: PipelineTemplateVersionReq): Boolean {
        return request is PipelineTemplateBranchPushReq
    }

    override fun convert(
        userId: String,
        projectId: String,
        templateId: String?,
        version: Long?,
        request: PipelineTemplateVersionReq
    ): PipelineTemplateVersionCreateContext {
        request as PipelineTemplateBranchPushReq
        with(request) {
            val modelTransferResult = pipelineTemplateGenerator.transfer(
                userId = userId,
                projectId = projectId,
                storageType = PipelineStorageType.YAML,
                templateType = null,
                templateModel = null,
                templateSetting = null,
                params = null,
                yaml = yaml
            )
            val (status, versionAction) = if (isDefaultBranch) {
                Pair(VersionStatus.RELEASED, PipelineVersionAction.CREATE_RELEASE)
            } else {
                Pair(VersionStatus.BRANCH, PipelineVersionAction.CREATE_BRANCH)
            }
            val (newTemplateId, templateInfo) = if (templateId == null) {
                val newTemplateId = pipelineTemplateGenerator.generateTemplateId()
                val templateSetting = modelTransferResult.templateSetting
                val templateInfo = PipelineTemplateInfo(
                    id = newTemplateId,
                    projectId = projectId,
                    name = templateSetting.pipelineName,
                    desc = templateSetting.desc,
                    mode = TemplateType.CUSTOMIZE,
                    type = modelTransferResult.templateType,
                    enablePac = true,
                    storeFlag = false,
                    creator = userId,
                    latestVersionStatus = status
                )
                Pair(newTemplateId, templateInfo)
            } else {
                val templateInfo = pipelineTemplateInfoService.get(
                    projectId = projectId,
                    templateId = templateId
                )
                Pair(templateId, templateInfo)
            }
            val pTemplateResourceWithoutVersion = PTemplateResourceWithoutVersion(
                projectId = projectId,
                templateId = newTemplateId,
                params = modelTransferResult.params,
                type = modelTransferResult.templateType,
                model = modelTransferResult.templateModel,
                yaml = modelTransferResult.yamlWithVersion?.yamlStr,
                description = description,
                status = status,
                creator = userId,
                updater = userId
            )
            val pipelineTemplateSetting = modelTransferResult.templateSetting.copy(
                projectId = projectId,
                pipelineId = newTemplateId,
                creator = userId,
                updater = userId
            )
            return PipelineTemplateVersionCreateContext(
                userId = userId,
                projectId = projectId,
                templateId = newTemplateId,
                version = version,
                versionAction = versionAction,
                pipelineTemplateInfo = templateInfo,
                pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
                pipelineTemplateSetting = pipelineTemplateSetting,
                enablePac = true,
                yamlFileInfo = yamlFileInfo,
                branchName = branchName
            )
        }
    }
}
