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
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCustomCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateVersionReq
import com.tencent.devops.process.service.template.v2.PipelineTemplateCommonService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelGenerator
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
    private val pipelineTemplateModelGenerator: PipelineTemplateModelGenerator
) : PipelineTemplateVersionReqConverter {

    override fun support(request: PipelineTemplateVersionReq): Boolean {
        return request is PipelineTemplateCustomCreateReq
    }

    override fun convert(userId: String, request: PipelineTemplateVersionReq): PipelineTemplateVersionContext {
        request as PipelineTemplateCustomCreateReq
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = request.projectId,
            name = request.name
        )
        val templateId = request.id!!
        val setting = pipelineTemplateModelGenerator.getDefaultSetting(
            type = request.type,
            projectId = request.projectId,
            templateId = templateId,
            templateName = request.name,
            desc = request.desc,
            creator = userId
        )
        val defaultTemplateModel = pipelineTemplateModelGenerator.getDefaultTemplateModel(
            name = request.name,
            type = request.type,
            userId = userId
        )
        val pipelineTemplateInfo = PipelineTemplateInfo(
            id = templateId,
            projectId = request.projectId,
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

        val modelTransferResult = pipelineTemplateModelGenerator.transfer(
            userId = userId,
            projectId = request.projectId,
            storageType = PipelineStorageType.MODEL,
            templateType = request.type,
            templateModel = defaultTemplateModel,
            templateSetting = setting,
            yaml = null
        )
        val pipelineTemplateResource = PipelineTemplateResource(
            id = pipelineTemplateModelGenerator.generateId(),
            projectId = request.projectId,
            templateId = templateId,
            type = request.type,
            settingVersion = PipelineTemplateConstant.INIT_VERSION,
            version = PipelineTemplateConstant.INIT_VERSION,
            model = modelTransferResult.templateModel,
            yaml = modelTransferResult.yamlWithVersion?.yamlStr,
            creator = userId,
            status = VersionStatus.COMMITTING
        )
        return PipelineTemplateVersionContext(
            userId = userId,
            projectId = request.projectId,
            templateId = templateId,
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateResource = pipelineTemplateResource,
            pipelineTemplateSetting = setting
        )
    }
}
