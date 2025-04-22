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

package com.tencent.devops.process.service.pipeline.version.convert

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.PipelineModelAndSetting
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.common.pipeline.pojo.transfer.TransferActionType
import com.tencent.devops.common.pipeline.pojo.transfer.TransferBody
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.engine.cfg.PipelineIdGenerator
import com.tencent.devops.process.engine.dao.PipelineInfoDao
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.engine.utils.PipelineUtils
import com.tencent.devops.common.pipeline.template.PipelineTemplateType
import com.tencent.devops.process.pojo.pipeline.PipelineResourceWithoutVersion
import com.tencent.devops.process.pojo.pipeline.PipelineTemplateInstanceBasicInfo
import com.tencent.devops.process.pojo.pipeline.PipelineYamlFileInfo
import com.tencent.devops.process.pojo.pipeline.version.PipelineTemplateInstanceReq
import com.tencent.devops.process.pojo.pipeline.version.PipelineVersionCreateReq
import com.tencent.devops.process.service.StageTagService
import com.tencent.devops.process.service.pipeline.PipelineTransferYamlService
import com.tencent.devops.process.service.pipeline.version.PipelineResourceFactory
import com.tencent.devops.process.service.pipeline.version.PipelineVersionCreateContext
import com.tencent.devops.process.service.pipeline.version.PipelineVersionGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateInstanceSettingService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateSettingService
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 模版实例化创建请求转换
 */
@Service
class PipelineTemplateInstanceReqConverter(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val stageTagService: StageTagService,
    private val pipelineTemplateInstanceSettingService: PipelineTemplateInstanceSettingService,
    private val pipelineIdGenerator: PipelineIdGenerator,
    private val transferService: PipelineTransferYamlService,
    private val pipelineResourceFactory: PipelineResourceFactory,
    private val pipelineVersionGenerator: PipelineVersionGenerator,
    private val pipelineRepositoryService: PipelineRepositoryService,
    private val dslContext: DSLContext,
    private val pipelineInfoDao: PipelineInfoDao
) : PipelineVersionCreateReqConverter {
    override fun support(request: PipelineVersionCreateReq) = request is PipelineTemplateInstanceReq

    @Suppress("LongMethod")
    override fun convert(
        userId: String,
        projectId: String,
        pipelineId: String?,
        version: Int?,
        request: PipelineVersionCreateReq
    ): PipelineVersionCreateContext {
        request as PipelineTemplateInstanceReq
        with(request) {
            if (enablePac) {
                if (targetAction == null) {
                    throw IllegalArgumentException("targetAction is null")
                }
                if (repoHashId == null) {
                    throw IllegalArgumentException("repoHashId is null")
                }
                if (filePath.isNullOrEmpty()) {
                    throw IllegalArgumentException("filePath is null")
                }
            }
            val templateInfo = pipelineTemplateInfoService.get(projectId = projectId, templateId = templateId)
            if (templateInfo.type != PipelineTemplateType.PIPELINE) {
                throw ErrorCodeException(
                    errorCode = ERROR_TEMPLATE_NOT_EXISTS
                )
            }
            val templateResource = pipelineTemplateResourceService.get(
                projectId = projectId,
                templateId = templateId,
                version = templateVersion
            )
            if (templateResource.model !is Model) {
                throw ErrorCodeException(
                    errorCode = ERROR_TEMPLATE_NOT_EXISTS
                )
            }

            // 生成流水线ID
            val newPipelineId = pipelineId ?: pipelineIdGenerator.getNextId()

            val pipelineInfo = pipelineInfoDao.getPipelineInfo(
                dslContext = dslContext,
                projectId = projectId,
                pipelineId = newPipelineId
            )

            // 根据模版model生成流水线model
            val defaultStageTagId = stageTagService.getDefaultStageTag().data?.id
            val instanceModel = PipelineUtils.instanceModel(
                templateModel = templateResource.model as Model,
                pipelineName = pipelineName,
                buildNo = buildNo,
                param = params,
                instanceFromTemplate = true,
                defaultStageTagId = defaultStageTagId,
                templateId = templateId
            )

            // 生成流水线配置
            val pipelineSetting = getPipelineSetting(
                projectId = projectId,
                pipelineId = newPipelineId,
                templateSettingVersion = templateResource.settingVersion
            )

            val transferResult = transferService.transfer(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                actionType = TransferActionType.FULL_MODEL2YAML,
                data = TransferBody(
                    modelAndSetting = PipelineModelAndSetting(
                        model = instanceModel,
                        setting = pipelineSetting
                    )
                )
            )
            val pipelineBasicInfo = pipelineResourceFactory.createPipelineBasicInfo(
                projectId = projectId,
                pipelineId = newPipelineId,
                channelCode = ChannelCode.BS,
                model = instanceModel
            )
            val (versionStatus, branchName) = pipelineVersionGenerator.getVersionStatusAndBranchName(
                projectId = projectId,
                templateId = templateId,
                templateVersion = templateVersion,
                enablePac = enablePac,
                repoHashId = repoHashId,
                targetAction = targetAction,
                targetBranch = targetBranch
            )
            val pipelineModelBasicInfo = pipelineResourceFactory.createPipelineModelBasicInfo(
                model = instanceModel,
                projectId = projectId,
                pipelineId = newPipelineId,
                userId = userId,
                create = pipelineInfo == null,
                versionStatus = versionStatus,
                channelCode = ChannelCode.BS
            )
            val pipelineResourceWithoutVersion = PipelineResourceWithoutVersion(
                projectId = projectId,
                pipelineId = newPipelineId,
                model = instanceModel,
                yaml = transferResult.yamlWithVersion?.yamlStr,
                yamlVersion = transferResult.yamlWithVersion?.versionTag,
                creator = userId,
                createTime = LocalDateTime.now(),
                updater = userId,
                updateTime = LocalDateTime.now(),
                status = versionStatus,
                branchAction = BranchVersionAction.ACTIVE.takeIf {
                    versionStatus == VersionStatus.BRANCH
                }
            )

            val templateInstanceBasicInfo = PipelineTemplateInstanceBasicInfo(
                templateId = templateId,
                templateVersion = templateVersion,
                templateVersionName = templateResource.versionName,
                instanceType = PipelineInstanceTypeEnum.CONSTRAINT
            )

            return PipelineVersionCreateContext(
                userId = userId,
                projectId = projectId,
                pipelineId = newPipelineId,
                versionAction = PipelineVersionAction.TEMPLATE_INSTANCE,
                pipelineBasicInfo = pipelineBasicInfo,
                pipelineModelBasicInfo = pipelineModelBasicInfo,
                pipelineResourceWithoutVersion = pipelineResourceWithoutVersion,
                pipelineSetting = pipelineSetting,
                enablePac = enablePac,
                yamlFileInfo = enablePac.takeIf { it }?.let {
                    PipelineYamlFileInfo(
                        repoHashId = repoHashId!!,
                        filePath = filePath!!,
                    )
                },
                targetAction = targetAction,
                branchName = branchName,
                templateInstanceBasicInfo = templateInstanceBasicInfo
            )
        }
    }

    private fun PipelineTemplateInstanceReq.getPipelineSetting(
        projectId: String,
        pipelineId: String,
        templateSettingVersion: Int
    ): PipelineSetting {
        val pipelineSetting = if (useTemplateSetting) {
            val templateSetting = pipelineTemplateSettingService.get(
                projectId = projectId,
                templateId = templateId,
                settingVersion = templateSettingVersion
            )
            templateSetting.copy(
                pipelineId = pipelineId,
                pipelineName = pipelineName
            )
        } else {
            pipelineRepositoryService.getSetting(
                projectId = projectId,
                pipelineId = pipelineId
            )?.copy(
                pipelineName = pipelineName
            ) ?: pipelineTemplateInstanceSettingService.getTemplateInstanceDefaultSetting(
                projectId = projectId,
                pipelineId = pipelineId,
                pipelineName = pipelineName
            )
        }
        return pipelineSetting
    }
}
