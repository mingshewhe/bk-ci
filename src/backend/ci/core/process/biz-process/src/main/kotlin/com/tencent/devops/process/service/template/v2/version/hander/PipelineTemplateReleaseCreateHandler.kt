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

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.enums.OperationLogType
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceOnlyVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.service.template.v2.PipelineTemplateGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelLock
import com.tencent.devops.process.service.template.v2.PipelineTemplatePersistenceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionCreateContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 创建流水线模版正式版本
 */
@Service
class PipelineTemplateReleaseCreateHandler @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplatePersistenceService: PipelineTemplatePersistenceService,
    private val pipelineTemplateGenerator: PipelineTemplateGenerator,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val redisOperation: RedisOperation
) : PipelineTemplateVersionCreateHandler {

    override fun support(context: PipelineTemplateVersionCreateContext): Boolean {
        return context.versionAction == PipelineVersionAction.CREATE_RELEASE
    }

    override fun handle(context: PipelineTemplateVersionCreateContext): DeployTemplateResult {
        with(context) {
            val lock = PipelineTemplateModelLock(redisOperation = redisOperation, templateId = templateId)
            try {
                lock.lock()
                return doHandle()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun PipelineTemplateVersionCreateContext.doHandle(): DeployTemplateResult {
        if (pTemplateResourceWithoutVersion.status != VersionStatus.RELEASED) {
            // TEMPLATE_NOT_RELEASED
            throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
        }
        val templateInfo = pipelineTemplateInfoService.getOrNull(
            projectId = projectId,
            templateId = templateId
        )
        val resourceOnlyVersion = if (templateInfo == null) {
            val defaultTemplateVersion = pipelineTemplateGenerator.getDefaultVersion(
                versionStatus = VersionStatus.RELEASED,
                versionName = fixVersionName
            )
            pipelineTemplatePersistenceService.initializeTemplate(
                pipelineTemplateInfo = pipelineTemplateInfo.copy(
                    releasedVersion = defaultTemplateVersion.version,
                    releasedVersionName = defaultTemplateVersion.versionName,
                    releasedSettingVersion = defaultTemplateVersion.settingVersion,
                    latestVersionStatus = VersionStatus.RELEASED
                ),
                pipelineTemplateResource = PipelineTemplateResource(
                    pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
                    pTemplateResourceOnlyVersion = defaultTemplateVersion
                ).copy(
                    releaseTime = LocalDateTime.now().timestampmilli()
                ),
                pipelineTemplateSetting = pipelineTemplateSetting.copy(
                    version = defaultTemplateVersion.settingVersion
                )
            )
            defaultTemplateVersion
        } else {
            createReleaseVersion()
        }
        return DeployTemplateResult(
            projectId = projectId,
            userId = userId,
            version = resourceOnlyVersion.version,
            templateId = templateId,
            templateName = pipelineTemplateInfo.name,
            number = resourceOnlyVersion.number,
            versionNum = resourceOnlyVersion.versionNum,
            versionName = resourceOnlyVersion.versionName,
            versionAction = versionAction,
            operationLogType = OperationLogType.RELEASE_MASTER_VERSION
        )
    }

    private fun PipelineTemplateVersionCreateContext.createReleaseVersion(): PTemplateResourceOnlyVersion {
        fixVersionName?.let {
            pipelineTemplateResourceService.delete(
                commonCondition = PipelineTemplateResourceCommonCondition(
                    projectId = projectId,
                    templateId = templateId,
                    versionName = it
                )
            )
        }
        val resourceOnlyVersion = pipelineTemplateGenerator.generateReleaseVersion(
            projectId = projectId,
            templateId = templateId,
            newResource = pTemplateResourceWithoutVersion,
            newSetting = pipelineTemplateSetting,
            fixVersionName = fixVersionName
        )
        val templateResource = PipelineTemplateResource(
            pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
            pTemplateResourceOnlyVersion = resourceOnlyVersion
        )
        pipelineTemplatePersistenceService.createReleaseVersion(
            userId = userId,
            templateResource = templateResource,
            templateSetting = pipelineTemplateSetting.copy(
                version = resourceOnlyVersion.settingVersion
            )
        )
        return resourceOnlyVersion
    }
}
