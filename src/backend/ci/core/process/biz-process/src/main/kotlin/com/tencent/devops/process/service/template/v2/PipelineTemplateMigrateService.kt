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

package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.model.process.tables.records.TTemplateRecord
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.dao.PipelineSettingDao
import com.tencent.devops.process.engine.dao.template.TemplateDao
import com.tencent.devops.process.engine.dao.template.TemplatePipelineDao
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.process.pojo.enums.UpgradeStrategyEnum
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.TemplateVersion
import com.tencent.devops.process.pojo.template.v2.PTemplateModelTransferResult
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.service.template.TemplateFacadeService
import com.tencent.devops.process.utils.PipelineVersionUtils
import org.jooq.DSLContext
import org.springframework.stereotype.Service

@Service
class PipelineTemplateMigrateService(
    val templateDao: TemplateDao,
    val dslContext: DSLContext,
    val templateFacadeService: TemplateFacadeService,
    val pipelineTemplateTransactionService: PipelineTemplateTransactionService,
    val pipelineSettingDao: PipelineSettingDao,
    val pipelineTemplateGenerator: PipelineTemplateGenerator,
    val pipelineTemplateResourceService: PipelineTemplateResourceService,
    val templatePipelineDao: TemplatePipelineDao
) {

    fun migrateTemplate(projectId: String) {
        var offset = 0
        val limit = PageUtil.MAX_PAGE_SIZE / 2
        do {
            val templateIds = templateDao.list(
                dslContext = dslContext,
                projectId = projectId,
                limit = limit,
                offset = offset
            )

            templateIds.forEach { templateId ->
                migrateTemplate(
                    templateId = templateId,
                    projectId = projectId
                )
            }

            offset += limit
        } while (templateIds.size == limit)
    }

    fun migrateTemplate(templateId: String, projectId: String) {
        val latestTemplate = templateDao.getLatestTemplate(
            dslContext = dslContext,
            projectId = projectId,
            templateId = templateId
        )

        val setting = pipelineSettingDao.getSetting(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = latestTemplate.id
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
        )

        val templateVersionInfos = getTemplateVersions(latestTemplate = latestTemplate)

        var seq = 0
        var pipelineVersion = 0
        var triggerVersion = 0

        templateVersionInfos.forEachIndexed { index, templateVersionInfo ->
            seq += 1
            val currentVersionSetting = setting.copy(version = seq)
            // 当前实际模板，可能为当前模板的版本或父模板版本
            val currentVersionTemplate = templateDao.getTemplate(
                dslContext = dslContext,
                projectId = templateVersionInfo.projectId!!,
                version = templateVersionInfo.version
            ) ?: throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
            )

            val currentVersionTemplateModel = JsonUtil.to(currentVersionTemplate.template, Model::class.java)
            val currentVersionTemplateParams = currentVersionTemplateModel.getTriggerContainer().params

            if (index == 0) {
                pipelineVersion = 1
                triggerVersion = 1
            } else {
                // 上一个版本的模板
                val previousVersionTemplate = templateDao.getTemplate(
                    dslContext = dslContext,
                    projectId = projectId,
                    version = templateVersionInfos[index - 1].version
                ) ?: throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
                )

                val previousVersionTemplateModel = JsonUtil.to(previousVersionTemplate.template, Model::class.java)
                val previousVersionTemplateParams = previousVersionTemplateModel.getTriggerContainer().params

                pipelineVersion = PipelineVersionUtils.getPipelineVersion(
                    currVersion = pipelineVersion,
                    originTemplateModel = previousVersionTemplateModel,
                    newTemplateModel = currentVersionTemplateModel,
                    originParams = previousVersionTemplateParams,
                    newParams = currentVersionTemplateParams
                )

                triggerVersion = PipelineVersionUtils.getTriggerVersion(
                    currVersion = triggerVersion,
                    originModel = previousVersionTemplateModel,
                    newModel = currentVersionTemplateModel
                )
            }

            val modelTransferResult = pipelineTemplateGenerator.transfer(
                userId = latestTemplate.creator,
                projectId = latestTemplate.projectId,
                storageType = PipelineStorageType.MODEL,
                templateType = PipelineTemplateType.PIPELINE,
                templateModel = currentVersionTemplateModel,
                templateSetting = currentVersionSetting,
                yaml = null
            )

            val pipelineTemplateResource = createPipelineTemplateResource(
                latestTemplate = latestTemplate,
                currentTemplate = currentVersionTemplate,
                seq = seq,
                pipelineVersion = pipelineVersion,
                triggerVersion = triggerVersion,
                params = currentVersionTemplateParams,
                modelTransferResult = modelTransferResult,
            )

            pipelineTemplateTransactionService.createTemplate(
                pipelineTemplateResource = pipelineTemplateResource,
                pipelineTemplateSetting = currentVersionSetting,
            )
        }

        val pipelineTemplateInfo = createPipelineTemplateInfo(latestTemplate = latestTemplate)

        pipelineTemplateTransactionService.createTemplate(
            pipelineTemplateInfo = pipelineTemplateInfo,
            syncPermission = false,
        )
    }

    fun getTemplateVersions(latestTemplate: TTemplateRecord): List<TemplateVersion> {
        return if (latestTemplate.type == TemplateType.CONSTRAINT.name) {
            val srcLatestTemplate = templateDao.getLatestTemplate(
                dslContext = dslContext,
                templateId = latestTemplate.srcTemplateId
            )
            templateFacadeService.listTemplateVersions(
                projectId = srcLatestTemplate.projectId,
                templateId = srcLatestTemplate.id
            )
        } else {
            templateFacadeService.listTemplateVersions(
                projectId = latestTemplate.projectId,
                templateId = latestTemplate.id
            )
        }
    }

    fun createPipelineTemplateResource(
        latestTemplate: TTemplateRecord,
        currentTemplate: TTemplateRecord,
        params: List<BuildFormProperty>,
        modelTransferResult: PTemplateModelTransferResult,
        seq: Int,
        pipelineVersion: Int,
        triggerVersion: Int
    ): PipelineTemplateResource {
        val isConstraint = latestTemplate.type == TemplateType.CONSTRAINT.name
        val (srcTemplateProjectId, srcTemplateVersion, srcTemplateId) =
            if (isConstraint) {
                Triple(currentTemplate.projectId, currentTemplate.version, currentTemplate.id)
            } else {
                Triple(null, null, null)
            }
        return PipelineTemplateResource(
            projectId = latestTemplate.projectId,
            templateId = latestTemplate.id,
            type = PipelineTemplateType.PIPELINE,
            settingVersion = seq,
            version = if (isConstraint) {
                pipelineTemplateGenerator.generateTemplateVersion()
            } else {
                currentTemplate.version
            },
            number = seq,
            versionName = currentTemplate.versionName,
            versionNum = seq,
            settingVersionNum = seq,
            pipelineVersion = pipelineVersion,
            triggerVersion = triggerVersion,
            srcTemplateProjectId = srcTemplateProjectId,
            srcTemplateId = srcTemplateId,
            srcTemplateVersion = srcTemplateVersion,
            params = params,
            model = modelTransferResult.templateModel,
            yaml = modelTransferResult.yamlWithVersion?.yamlStr,
            status = VersionStatus.RELEASED,
            description = currentTemplate.desc,
            sortWeight = 0,
            creator = latestTemplate.creator,
            updater = latestTemplate.creator,
            releaseTime = currentTemplate.createdTime.timestampmilli()
        )
    }

    fun createPipelineTemplateInfo(latestTemplate: TTemplateRecord): PipelineTemplateInfo {
        val latestReleasedResource = pipelineTemplateResourceService.getLatestReleasedResource(
            projectId = latestTemplate.projectId,
            templateId = latestTemplate.id
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
        )
        val instanceSize = templatePipelineDao.countByVersionFeat(
            dslContext = dslContext,
            projectId = latestTemplate.projectId,
            templateId = latestTemplate.template,
            instanceType = "CONSTRAINT"
        )
        val isConstraint = latestTemplate.type == TemplateType.CONSTRAINT.name
        val strategy = if (isConstraint) UpgradeStrategyEnum.AUTO else null
        return PipelineTemplateInfo(
            id = latestTemplate.id,
            projectId = latestTemplate.projectId,
            name = latestTemplate.templateName,
            desc = latestTemplate.desc,
            mode = TemplateType.valueOf(latestTemplate.type),
            category = latestTemplate.category,
            type = PipelineTemplateType.PIPELINE,
            logoUrl = latestTemplate.logoUrl,
            enablePac = false,
            releasedVersion = latestReleasedResource.version,
            releasedVersionName = latestReleasedResource.versionName,
            releasedSettingVersion = latestReleasedResource.settingVersion,
            latestVersionStatus = VersionStatus.RELEASED,
            storeFlag = latestTemplate.storeFlag,
            srcTemplateId = latestReleasedResource.srcTemplateId,
            srcTemplateProjectId = latestReleasedResource.srcTemplateProjectId,
            instancePipelineCount = instanceSize,
            upgradeStrategy = strategy,
            settingSyncStrategy = strategy,
            creator = latestTemplate.creator,
            updater = latestTemplate.creator,
            createdTime = latestTemplate.createdTime.timestampmilli(),
            updateTime = latestTemplate.updateTime.timestampmilli()
        )
    }
}
