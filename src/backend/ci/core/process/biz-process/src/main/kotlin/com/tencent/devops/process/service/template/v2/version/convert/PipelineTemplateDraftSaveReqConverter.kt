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

import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceWithoutVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftSaveReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateVersionReq
import com.tencent.devops.process.service.template.v2.PipelineTemplateGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionCreateContext
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionReqConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 保存草稿请求转换
 */
@Service
class PipelineTemplateDraftSaveReqConverter @Autowired constructor(
    private val pipelineTemplateGenerator: PipelineTemplateGenerator,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService
) : PipelineTemplateVersionReqConverter {

    override fun support(request: PipelineTemplateVersionReq): Boolean {
        return request is PipelineTemplateDraftSaveReq
    }

    override fun convert(
        userId: String,
        projectId: String,
        templateId: String?,
        version: Long?,
        request: PipelineTemplateVersionReq
    ): PipelineTemplateVersionCreateContext {
        request as PipelineTemplateDraftSaveReq
        with(request) {
            val params = if (storageType == PipelineStorageType.MODEL && type == PipelineTemplateType.PIPELINE) {
                (model as Model).getTriggerContainer().params
            } else {
                request.params
            }
            val modelTransferResult = pipelineTemplateGenerator.transfer(
                userId = userId,
                projectId = projectId,
                storageType = storageType,
                templateType = type,
                templateModel = model,
                params = params,
                templateSetting = templateSetting,
                yaml = yaml
            )
            val templateInfo = if (templateId != null) {
                pipelineTemplateInfoService.get(
                    projectId = projectId,
                    templateId = templateId
                )
            } else {
                PipelineTemplateInfo(
                    id = pipelineTemplateGenerator.generateTemplateId(),
                    projectId = projectId,
                    name = modelTransferResult.templateSetting.pipelineName,
                    desc = modelTransferResult.templateSetting.desc,
                    mode = TemplateType.CUSTOMIZE,
                    type = modelTransferResult.templateType,
                    enablePac = false,
                    storeFlag = false,
                    creator = userId,
                    latestVersionStatus = VersionStatus.COMMITTING
                )
            }
            val pTemplateResourceWithoutVersion = PTemplateResourceWithoutVersion(
                projectId = projectId,
                templateId = templateInfo.id,
                type = templateInfo.type,
                params = modelTransferResult.params,
                model = modelTransferResult.templateModel,
                yaml = modelTransferResult.yamlWithVersion?.yamlStr,
                status = VersionStatus.COMMITTING,
                sortWeight = PipelineTemplateConstant.COMMITTING_STATUS_VERSION_SORT_WIGHT,
                creator = userId,
                updater = userId
            )
            val pipelineTemplateSetting = modelTransferResult.templateSetting.copy(
                projectId = projectId,
                pipelineId = templateInfo.id,
                creator = userId,
                updater = userId
            )
            return PipelineTemplateVersionCreateContext(
                userId = userId,
                projectId = projectId,
                templateId = templateInfo.id,
                version = version,
                versionAction = PipelineVersionAction.SAVE_DRAFT,
                pipelineTemplateInfo = templateInfo,
                pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
                pipelineTemplateSetting = pipelineTemplateSetting
            )
        }
    }
}
