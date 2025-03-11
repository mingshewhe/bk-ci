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

import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.template.v2.PipelineTemplatePermission
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceUpdateInfo
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplatePersistenceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateSettingService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionContext
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionHandler
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版保存草稿
 *
 */
@Service
class PipelineTemplateSaveDraftHandler @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val pipelineTemplatePersistenceService: PipelineTemplatePersistenceService
) : PipelineTemplateVersionHandler {
    override fun support(versionAction: PipelineVersionAction): Boolean {
        return versionAction == PipelineVersionAction.SAVE_DRAFT
    }

    override fun handle(context: PipelineTemplateVersionContext): DeployTemplateResult {
        with(context) {
            val templateInfo = pipelineTemplateInfoService.getOrNull(
                projectId = projectId,
                templateId = templateId
            )
            if (templateInfo == null) {
                pipelineTemplatePersistenceService.createTemplate(
                    pipelineTemplateInfo = pipelineTemplateInfo,
                    pipelineTemplateResource = pipelineTemplateResource,
                    pipelineTemplateSetting = pipelineTemplateSetting,
                    pipelineTemplatePermission = PipelineTemplatePermission(
                        projectId = projectId,
                        id = templateId,
                        name = pipelineTemplateInfo.name,
                        creator = userId
                    )
                )
            } else {
                val draftVersion = pipelineTemplateResourceService.getDraftVersionResource(
                    projectId = projectId,
                    templateId = templateId
                )
                if (draftVersion == null) {
                    createDraftVersion()
                } else {
                    updateDraftVersion()
                }
            }
            return DeployTemplateResult(
                templateId = templateId,
                templateName = pipelineTemplateInfo.name,
                version = pipelineTemplateResource.version,
                versionNum = pipelineTemplateResource.versionNum,
                versionName = pipelineTemplateResource.versionName
            )
        }
    }

    private fun PipelineTemplateVersionContext.createDraftVersion() {
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            pipelineTemplateResourceService.create(
                transactionContext = transactionContext,
                pipelineTemplateResource = pipelineTemplateResource
            )
            pipelineTemplateSettingService.create(
                transactionContext = transactionContext,
                pipelineTemplateSetting = pipelineTemplateSetting
            )
        }
    }

    private fun PipelineTemplateVersionContext.updateDraftVersion() {
        // 若存在草稿，则在原草稿版本上更新
        val draftVersionResource = pipelineTemplateResourceService.get(
            PipelineTemplateResourceCommonCondition(
                projectId = projectId,
                templateId = templateId,
                status = VersionStatus.COMMITTING
            )
        )
        val templateResourceUpdateInfo = PipelineTemplateResourceUpdateInfo(
            params = pipelineTemplateResource.params,
            model = pipelineTemplateResource.model,
            yaml = pipelineTemplateResource.yaml,
            updater = userId,
            sortWeight = PipelineTemplateConstant.COMMITTING_STATUS_VERSION_SORT_WIGHT
        )
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            pipelineTemplateResourceService.update(
                transactionContext = context,
                record = templateResourceUpdateInfo,
                commonCondition = PipelineTemplateResourceCommonCondition(
                    projectId = draftVersionResource.projectId,
                    templateId = draftVersionResource.templateId,
                    version = draftVersionResource.version
                )
            )
            pipelineTemplateSettingService.create(
                transactionContext = context,
                pipelineTemplateSetting = pipelineTemplateSetting
            )
        }
    }
}
