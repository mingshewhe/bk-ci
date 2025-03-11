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

package com.tencent.devops.process.api.template.v2

import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.pojo.template.TemplateInstanceUpdate
import com.tencent.devops.process.pojo.template.TemplateOperationRet
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstancesReleaseRequest
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedResp
import com.tencent.devops.process.service.template.v2.PipelineTemplateInstanceFacadeService
import org.slf4j.LoggerFactory

@RestResource
class UserPipelineTemplateInstanceV2ResourceImpl(
    private val instanceFacadeService: PipelineTemplateInstanceFacadeService
) : UserPipelineTemplateInstanceV2Resource {
    override fun createTemplateInstances(
        userId: String,
        projectId: String,
        templateId: String,
        version: Int,
        useTemplateSettings: Boolean,
        request: PipelineTemplateInstancesReleaseRequest
    ): TemplateOperationRet {
        return instanceFacadeService.createTemplateInstances(
            projectId = projectId,
            userId = userId,
            templateId = templateId,
            version = version,
            useTemplateSettings = useTemplateSettings,
            request = request
        )
    }

    override fun asyncCreateTemplateInstances(
        userId: String,
        projectId: String,
        templateId: String,
        version: Int,
        useTemplateSettings: Boolean,
        request: PipelineTemplateInstancesReleaseRequest
    ): Result<String> {
        return Result(
            data = instanceFacadeService.asyncCreateTemplateInstances(
                projectId = projectId,
                userId = userId,
                templateId = templateId,
                version = version,
                useTemplateSettings = useTemplateSettings,
                request = request
            )
        )
    }

    override fun updateTemplateInstances(
        userId: String,
        projectId: String,
        templateId: String,
        version: Int,
        useTemplateSettings: Boolean,
        instances: List<TemplateInstanceUpdate>
    ): TemplateOperationRet {
        return instanceFacadeService.syncUpdateTemplateInstances(
            projectId = projectId,
            userId = userId,
            templateId = templateId,
            version = version,
            useTemplateSettings = useTemplateSettings,
            instances = instances
        )
    }

    override fun asyncUpdateTemplateInstances(
        userId: String,
        projectId: String,
        templateId: String,
        version: Int,
        useTemplateSettings: Boolean,
        instances: List<TemplateInstanceUpdate>
    ): Result<Boolean> {
        return Result(
            instanceFacadeService.asyncUpdateTemplateInstances(
                projectId = projectId,
                userId = userId,
                templateId = templateId,
                version = version,
                useTemplateSettings = useTemplateSettings,
                instances = instances
            )
        )
    }

    override fun listTemplateInstances(
        userId: String,
        projectId: String,
        templateId: String,
        pipelineName: String?,
        updater: String?,
        page: Int,
        pageSize: Int
    ): Result<SQLPage<PipelineTemplateRelatedResp>> {
        return Result(
            instanceFacadeService.list(
                userId = userId,
                projectId = projectId,
                templateId = templateId,
                pipelineName = pipelineName,
                updater = updater,
                page = page,
                pageSize = pageSize
            )
        )
    }
}
