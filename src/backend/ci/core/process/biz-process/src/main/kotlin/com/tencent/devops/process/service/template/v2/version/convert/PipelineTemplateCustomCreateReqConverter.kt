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
import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceWithoutVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCustomCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateVersionReq
import com.tencent.devops.process.service.template.v2.PipelineTemplateCommonService
import com.tencent.devops.process.service.template.v2.PipelineTemplateGenerator
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionContext
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionReqConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模板自定义创建请求转换
 */
@Service
class PipelineTemplateCustomCreateReqConverter @Autowired constructor(
    private val pipelineTemplateCommonService: PipelineTemplateCommonService,
    private val pipelineTemplateGenerator: PipelineTemplateGenerator
) : PipelineTemplateVersionReqConverter {

    override fun support(request: PipelineTemplateVersionReq): Boolean {
        return request is PipelineTemplateCustomCreateReq
    }

    override fun convert(
        userId: String,
        projectId: String,
        request: PipelineTemplateVersionReq
    ): PipelineTemplateVersionContext {
        request as PipelineTemplateCustomCreateReq
        with(request) {
            pipelineTemplateCommonService.checkTemplateBasicInfo(
                projectId = projectId,
                name = name
            )
            val templateId = pipelineTemplateGenerator.generateTemplateId()
            val setting = pipelineTemplateGenerator.getDefaultSetting(
                type = type,
                projectId = projectId,
                templateId = templateId,
                templateName = name,
                desc = desc,
                creator = userId
            )
            val defaultTemplateModel = pipelineTemplateGenerator.getDefaultTemplateModel(
                name = name,
                type = type,
                userId = userId
            )
            val pipelineTemplateInfo = PipelineTemplateInfo(
                id = templateId,
                projectId = projectId,
                name = request.name,
                desc = request.desc,
                mode = TemplateType.CUSTOMIZE.name,
                type = request.type,
                enablePac = false,
                source = PipelineTemplateSource.CUSTOM,
                storeFlag = false,
                creator = userId,
                latestVersionStatus = VersionStatus.COMMITTING
            )

            val modelTransferResult = pipelineTemplateGenerator.transfer(
                userId = userId,
                projectId = projectId,
                storageType = PipelineStorageType.MODEL,
                templateType = type,
                templateModel = defaultTemplateModel,
                templateSetting = setting,
                yaml = null
            )
            val pTemplateResourceWithoutVersion = PTemplateResourceWithoutVersion(
                id = pipelineTemplateGenerator.generateTemplateVersion(),
                projectId = projectId,
                templateId = templateId,
                type = type,
                model = modelTransferResult.templateModel,
                yaml = modelTransferResult.yamlWithVersion?.yamlStr,
                status = VersionStatus.COMMITTING,
                creator = userId
            )
            return PipelineTemplateVersionContext(
                userId = userId,
                projectId = projectId,
                templateId = templateId,
                versionAction = PipelineVersionAction.SAVE_DRAFT,
                pipelineTemplateInfo = pipelineTemplateInfo,
                pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
                pipelineTemplateSetting = setting
            )
        }
    }
}
