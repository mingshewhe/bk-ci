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

package com.tencent.devops.process.service.pipeline.version.listener

import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.process.engine.dao.template.TemplatePipelineDao
import com.tencent.devops.process.pojo.template.TemplateInstanceUpdate
import com.tencent.devops.process.service.template.v2.PipelineTemplateRelatedService
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineTemplateVersionPostProcessor @Autowired constructor(
    private val pipelineTemplateRelatedService: PipelineTemplateRelatedService,
    private val templatePipelineDao: TemplatePipelineDao
) : PipelineVersionCreatePostProcessor {

    override fun postProcessInTransactionCreation(
        transactionContext: DSLContext,
        postCreationContext: PipelineVersionPostCreationContext
    ) {
        val templateInstanceBasicInfo = postCreationContext.templateInstanceBasicInfo ?: return
        val pipelineBasicInfo = postCreationContext.pipelineBasicInfo
        val pipelineModelBasicInfo = postCreationContext.pipelineModelBasicInfo

        pipelineTemplateRelatedService.createRelation(
            userId = postCreationContext.userId,
            projectId = pipelineBasicInfo.projectId,
            pipelineId = pipelineBasicInfo.pipelineId,
            templateId = templateInstanceBasicInfo.templateId,
            instanceType = templateInstanceBasicInfo.instanceType.type,
            buildNo = pipelineModelBasicInfo.buildNo,
            param = pipelineModelBasicInfo.param,
            fixTemplateVersion = templateInstanceBasicInfo.templateVersion
        )
    }

    override fun postProcessInTransactionVersionCreation(
        transactionContext: DSLContext,
        postCreationContext: PipelineVersionPostCreationContext
    ) {
        val templateInstanceBasicInfo = postCreationContext.templateInstanceBasicInfo ?: return
        // 制约模式下才需要更新
        if (templateInstanceBasicInfo.instanceType != PipelineInstanceTypeEnum.CONSTRAINT) {
            return
        }
        val pipelineBasicInfo = postCreationContext.pipelineBasicInfo
        val pipelineModelBasicInfo = postCreationContext.pipelineModelBasicInfo

        templatePipelineDao.update(
            dslContext = transactionContext,
            projectId = pipelineBasicInfo.projectId,
            templateVersion = templateInstanceBasicInfo.templateVersion,
            versionName = templateInstanceBasicInfo.templateVersionName ?: "",
            userId = postCreationContext.userId,
            instance = TemplateInstanceUpdate(
                pipelineId = pipelineBasicInfo.pipelineId,
                pipelineName = pipelineBasicInfo.pipelineName,
                buildNo = pipelineModelBasicInfo.buildNo,
                param = pipelineModelBasicInfo.param
            )
        )
    }
}
