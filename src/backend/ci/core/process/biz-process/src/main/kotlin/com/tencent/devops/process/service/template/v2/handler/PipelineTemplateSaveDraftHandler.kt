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

import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftSaveReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceUpdateInfo
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplatePersistenceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateSettingService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineTemplateSaveDraftHandler @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateModelGenerator: PipelineTemplateModelGenerator,
    private val pipelineTemplatePersistenceService: PipelineTemplatePersistenceService,
    private val dslContext: DSLContext,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService
) : PipelineTemplateVersionHandler<PipelineTemplateDraftSaveReq, Long> {

    override fun support(source: VersionStatus, event: PipelineVersionAction): Boolean {
        return source == VersionStatus.COMMITTING && event == PipelineVersionAction.SAVE_DRAFT
    }

    override fun execute(
        source: VersionStatus,
        event: PipelineVersionAction,
        context: PipelineTemplateVersionContext<PipelineTemplateDraftSaveReq>
    ): Long {
        val userId = context.userId
        val request = context.request
        logger.info("save template draft {}|{}|{}", request.projectId, userId, request)
        val templateInfo = pipelineTemplateInfoService.get(
            projectId = request.projectId,
            templateId = request.templateId
        )

        val isExistDraft = pipelineTemplateResourceService.getDraftVersionResource(
            projectId = request.projectId,
            templateId = request.templateId
        ) != null

        // todo 检查模型，模板参数，配置检查

        return if (isExistDraft) {
            updateDraftVersion(
                userId = userId,
                templateInfo = templateInfo,
                request = request
            )
        } else {
            createDraftVersion(
                userId = userId,
                templateInfo = templateInfo,
                request = request
            )
        }
    }

    /**
     * 创建草稿
     */
    private fun createDraftVersion(
        userId: String,
        templateInfo: PipelineTemplateInfo,
        request: PipelineTemplateDraftSaveReq
    ): Long {
        // 若不存在草稿版本，则基于版本进行创建新版本草稿
        val latestTemplateResource = pipelineTemplateResourceService.getLatestReleasedResource(
            projectId = request.projectId,
            templateId = request.templateId
        )
        val baseVersionResource = pipelineTemplateResourceService.get(
            projectId = request.projectId,
            templateId = request.templateId,
            version = request.baseVersion
        )

        val transferResult = pipelineTemplateModelGenerator.transfer(
            userId = userId,
            projectId = request.projectId,
            storageType = request.storageType!!,
            templateType = PipelineTemplateType.PIPELINE,
            templateModel = request.model,
            templateSetting = request.templateSetting,
            yaml = request.yaml
        )

        val version = pipelineTemplateModelGenerator.generateVersion()
        val pipelineTemplateResource = PipelineTemplateResource(
            projectId = request.projectId,
            templateId = request.templateId,
            type = templateInfo.type,
            settingVersion = latestTemplateResource.settingVersion?.let { it + 1 },
            version = pipelineTemplateModelGenerator.generateVersion(),
            number = latestTemplateResource.number + 1,
            baseVersion = baseVersionResource.version,
            params = request.params,
            model = transferResult.templateModel,
            yaml = transferResult.yamlWithVersion?.yamlStr,
            status = VersionStatus.COMMITTING,
            creator = userId
        )
        val pipelineTemplateSetting = transferResult.templateSetting?.let { setting ->
            latestTemplateResource.settingVersion?.let { currentVersion ->
                setting.copy(
                    pipelineId = templateInfo.id,
                    version = currentVersion + 1
                )
            }
        }
        pipelineTemplatePersistenceService.createTemplate(
            pipelineTemplateSetting = pipelineTemplateSetting,
            pipelineTemplateResource = pipelineTemplateResource
        )
        return version
    }

    /**
     * 更新草稿
     */
    private fun updateDraftVersion(
        userId: String,
        templateInfo: PipelineTemplateInfo,
        request: PipelineTemplateDraftSaveReq
    ): Long {
        // 若存在草稿，则在原草稿版本上更新
        val draftVersionResource = pipelineTemplateResourceService.get(
            PipelineTemplateResourceCommonCondition(
                projectId = request.projectId,
                templateId = request.templateId,
                status = VersionStatus.COMMITTING
            )
        )
        val transferResult = pipelineTemplateModelGenerator.transfer(
            userId = userId,
            projectId = request.projectId,
            storageType = request.storageType!!,
            templateType = PipelineTemplateType.PIPELINE,
            templateModel = request.model,
            templateSetting = request.templateSetting,
            yaml = request.yaml
        )
        val templateResourceUpdateInfo = PipelineTemplateResourceUpdateInfo(
            params = request.params,
            model = transferResult.templateModel,
            yaml = transferResult.yamlWithVersion?.yamlStr,
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
            if (transferResult.templateSetting != null) {
                pipelineTemplateSettingService.create(
                    transactionContext = dslContext,
                    pipelineTemplateSetting = transferResult.templateSetting!!.copy(
                        pipelineId = request.templateId,
                        version = draftVersionResource.settingVersion!!,
                        creator = draftVersionResource.creator
                    )
                )
            }
        }
        return draftVersionResource.version
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateSaveDraftHandler::class.java)
    }
}
