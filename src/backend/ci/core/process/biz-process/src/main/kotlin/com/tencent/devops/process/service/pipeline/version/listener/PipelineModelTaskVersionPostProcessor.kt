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

import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.engine.dao.PipelineModelTaskDao
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线版本创建后,model task后置处理器
 */
@Service
class PipelineModelTaskVersionPostProcessor @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineModelTaskDao: PipelineModelTaskDao
) : PipelineVersionCreatePostProcessor {

    override fun postProcessAfterCreation(
        postCreationContext: PipelineVersionPostCreationContext
    ) {
        val pipelineModelBasicInfo = postCreationContext.pipelineModelBasicInfo
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            pipelineModelTaskDao.batchSave(
                dslContext = transactionContext,
                modelTasks = pipelineModelBasicInfo.modelTasks
            )
        }
    }

    override fun postProcessBeforeVersionCreation(
        postCreationContext: PipelineVersionPostCreationContext
    ) {
        val pipelineBasicInfo = postCreationContext.pipelineBasicInfo
        val pipelineModelBasicInfo = postCreationContext.pipelineModelBasicInfo
        val pipelineResourceVersion = postCreationContext.pipelineResourceVersion
        with(pipelineBasicInfo) {
            if (pipelineResourceVersion.status != VersionStatus.RELEASED) {
                return
            }
            dslContext.transaction { configuration ->
                val transactionContext = DSL.using(configuration)
                pipelineModelTaskDao.deletePipelineTasks(
                    dslContext = transactionContext,
                    projectId = projectId,
                    pipelineId = pipelineId
                )
                pipelineModelTaskDao.batchSave(
                    dslContext = transactionContext,
                    modelTasks = pipelineModelBasicInfo.modelTasks
                )
            }
        }
    }
}
