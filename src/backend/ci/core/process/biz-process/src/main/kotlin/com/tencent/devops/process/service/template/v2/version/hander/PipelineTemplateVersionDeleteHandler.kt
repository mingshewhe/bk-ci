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

package com.tencent.devops.process.service.template.v2.version.hander

import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceUpdateInfo
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelLock
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionDeleteContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版删除处理
 */
@Service
class PipelineTemplateVersionDeleteHandler @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val redisOperation: RedisOperation
) {

    fun handle(context: PipelineTemplateVersionDeleteContext) {
        with(context) {
            logger.info(
                "handle pipeline template version delete|" +
                        "projectId:$projectId|templateId:$templateId|versionAction:$versionAction" +
                        "|version:$version|branch:$branch"
            )
            val lock = PipelineTemplateModelLock(redisOperation = redisOperation, templateId = templateId)
            try {
                lock.lock()
                return doHandle()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun PipelineTemplateVersionDeleteContext.doHandle() {
        pipelineTemplateInfoService.get(projectId = projectId, templateId = templateId)
        when (versionAction) {
            PipelineVersionAction.DELETE_VERSION -> {
                deleteVersion()
            }

            PipelineVersionAction.INACTIVE_BRANCH -> {
                inactiveBranch()
            }

            else -> {

            }
        }
    }

    private fun PipelineTemplateVersionDeleteContext.deleteVersion() {
        if (version == null) {
            throw IllegalArgumentException("version is null")
        }
        val updateInfo = PipelineTemplateResourceUpdateInfo(
            status = VersionStatus.DELETE
        )
        val condition = PipelineTemplateResourceCommonCondition(
            projectId = projectId,
            templateId = templateId,
            version = version
        )
        pipelineTemplateResourceService.update(
            record = updateInfo,
            commonCondition = condition
        )
    }

    private fun PipelineTemplateVersionDeleteContext.inactiveBranch() {
        if (branch == null) {
            throw IllegalArgumentException("branchName is null")
        }
        val inactiveBranchUpdateInfo = PipelineTemplateResourceUpdateInfo(
            branchAction = BranchVersionAction.INACTIVE
        )
        val inactiveBranchCondition = PipelineTemplateResourceCommonCondition(
            projectId = projectId,
            templateId = templateId,
            versionName = branch,
            branchAction = BranchVersionAction.ACTIVE
        )
        pipelineTemplateResourceService.update(
            record = inactiveBranchUpdateInfo,
            commonCondition = inactiveBranchCondition
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateVersionDeleteHandler::class.java)
    }
}
