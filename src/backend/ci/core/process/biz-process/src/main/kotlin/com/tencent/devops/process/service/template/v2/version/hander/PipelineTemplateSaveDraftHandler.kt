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
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceOnlyVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplatePermission
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceUpdateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingUpdateInfo
import com.tencent.devops.process.service.template.v2.PipelineTemplateGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelLock
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateTransactionService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionContext
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版保存草稿
 *
 */
@Service
class PipelineTemplateSaveDraftHandler @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateTransactionService: PipelineTemplateTransactionService,
    private val pipelineTemplateGenerator: PipelineTemplateGenerator,
    private val redisOperation: RedisOperation
) : PipelineTemplateVersionHandler {
    override fun support(context: PipelineTemplateVersionContext): Boolean {
        return context.versionAction == PipelineVersionAction.SAVE_DRAFT
    }

    override fun handle(context: PipelineTemplateVersionContext): DeployTemplateResult {
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

    private fun PipelineTemplateVersionContext.doHandle(): DeployTemplateResult {
        val templateInfo = pipelineTemplateInfoService.getOrNull(
            projectId = projectId,
            templateId = templateId
        )
        val pTemplateResourceOnlyVersion = if (templateInfo == null) {
            val defaultVersion = pipelineTemplateGenerator.getDefaultVersion(
                versionStatus = VersionStatus.COMMITTING
            )
            pipelineTemplateTransactionService.createTemplateAndPermission(
                pipelineTemplateInfo = pipelineTemplateInfo,
                pipelineTemplateResource = PipelineTemplateResource(
                    pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
                    pTemplateResourceOnlyVersion = defaultVersion
                ),
                pipelineTemplateSetting = pipelineTemplateSetting,
                pipelineTemplatePermission = PipelineTemplatePermission(
                    projectId = projectId,
                    id = templateId,
                    name = pipelineTemplateInfo.name,
                    creator = userId
                )
            )
            defaultVersion
        } else {
            val draftVersion = pipelineTemplateResourceService.getDraftVersionResource(
                projectId = projectId,
                templateId = templateId
            )
            if (draftVersion == null) {
                createDraftVersion()
            } else {
                updateDraftVersion(draftVersion = draftVersion)
            }
        }
        return DeployTemplateResult(
            templateId = templateId,
            templateName = pipelineTemplateInfo.name,
            version = pTemplateResourceOnlyVersion.version,
            versionNum = pTemplateResourceOnlyVersion.versionNum,
            versionName = pTemplateResourceOnlyVersion.versionName
        )
    }

    private fun PipelineTemplateVersionContext.createDraftVersion(): PTemplateResourceOnlyVersion {
        val latestVersion = pipelineTemplateResourceService.getLatestVersionResource(
            projectId = projectId,
            templateId = templateId
        ) ?: throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
        val draftTemplateResourceOnlyVersion = pipelineTemplateGenerator.generateVersion(
            latestVersion = latestVersion,
            baseVersion = pTemplateResourceWithoutVersion.baseVersion
        )
        val templateResource = PipelineTemplateResource(
            pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
            pTemplateResourceOnlyVersion = draftTemplateResourceOnlyVersion
        )
        pipelineTemplateTransactionService.createDraftVersion(
            templateResource = templateResource,
            templateSetting = pipelineTemplateSetting
        )
        return draftTemplateResourceOnlyVersion
    }

    private fun PipelineTemplateVersionContext.updateDraftVersion(
        draftVersion: PipelineTemplateResource
    ): PTemplateResourceOnlyVersion {
        val templateResourceUpdateInfo = PipelineTemplateResourceUpdateInfo(
            params = pTemplateResourceWithoutVersion.params,
            model = pTemplateResourceWithoutVersion.model,
            yaml = pTemplateResourceWithoutVersion.yaml,
            updater = userId,
            sortWeight = PipelineTemplateConstant.COMMITTING_STATUS_VERSION_SORT_WIGHT
        )
        val templateResourceCondition = PipelineTemplateResourceCommonCondition(
            projectId = draftVersion.projectId,
            templateId = draftVersion.templateId,
            version = draftVersion.version
        )
        val templateSettingUpdateInfo = PipelineTemplateSettingUpdateInfo(
            userId = userId,
            pipelineSetting = pipelineTemplateSetting
        )
        val templateSettingCondition = PipelineTemplateSettingCommonCondition(
            projectId = draftVersion.projectId,
            templateId = draftVersion.templateId,
            settingVersion = draftVersion.settingVersion
        )
        pipelineTemplateTransactionService.updateDraftVersion(
            templateResourceUpdateInfo = templateResourceUpdateInfo,
            templateResourceCondition = templateResourceCondition,
            templateSettingUpdateInfo = templateSettingUpdateInfo,
            templateSettingCondition = templateSettingCondition
        )
        return PTemplateResourceOnlyVersion(draftVersion)
    }
}
