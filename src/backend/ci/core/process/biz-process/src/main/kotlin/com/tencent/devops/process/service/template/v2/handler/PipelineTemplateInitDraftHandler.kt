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

package com.tencent.devops.process.service.template.v2.handler

import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.VersionEvent
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCreateResp
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCustomCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplatePermission
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.service.template.v2.PipelineTemplateCommonService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplatePersistenceService
import com.tencent.devops.project.api.service.ServiceAllocIdResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineTemplateInitDraftHandler @Autowired constructor(
    private val pipelineTemplateCommonService: PipelineTemplateCommonService,
    private val client: Client,
    private val pipelineTemplateModelGenerator: PipelineTemplateModelGenerator,
    private val templatePersistenceService: PipelineTemplatePersistenceService
) : PipelineTemplateVersionHandler<PipelineTemplateCustomCreateReq, PipelineTemplateCreateResp> {

    override fun support(source: VersionStatus, event: VersionEvent): Boolean {
        return source == VersionStatus.INIT && event == VersionEvent.INIT_DRAFT
    }

    override fun execute(
        source: VersionStatus,
        event: VersionEvent,
        context: PipelineTemplateVersionContext<PipelineTemplateCustomCreateReq>
    ): PipelineTemplateCreateResp {
        val userId = context.userId
        val request = context.request
        pipelineTemplateCommonService.checkTemplateBasicInfo(
            projectId = request.projectId,
            name = request.name
        )
        val templateId = request.id!!
        val version = client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!
        val (setting, settingVersion) = pipelineTemplateModelGenerator.getDefaultSettingAndVersion(
            type = request.type,
            projectId = request.projectId,
            templateId = templateId,
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

        val pipelineTemplateResource = PipelineTemplateResource(
            projectId = request.projectId,
            templateId = templateId,
            name = request.name,
            desc = request.desc,
            type = request.type,
            settingVersion = settingVersion,
            version = version,
            number = 1,
            model = defaultTemplateModel,
            yaml = null,
            creator = userId,
            status = VersionStatus.COMMITTING
        )

        val pipelineTemplatePermission = PipelineTemplatePermission(
            projectId = request.projectId,
            id = templateId,
            name = request.name,
            creator = userId
        )
        templatePersistenceService.saveTemplate(
            pipelineTemplateInfo = pipelineTemplateInfo,
            pipelineTemplateResource = pipelineTemplateResource,
            pipelineTemplateSetting = setting,
            pipelineTemplatePermission = pipelineTemplatePermission
        )
        return PipelineTemplateCreateResp(
            projectId = request.projectId,
            templateId = templateId,
            version = 1
        )
    }

    companion object {
        private const val TEMPLATE_BIZ_TAG_NAME = "TEMPLATE"
        private val logger = LoggerFactory.getLogger(PipelineTemplateInitDraftHandler::class.java)
    }
}
