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
import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.PipelineModelAndSetting
import com.tencent.devops.common.pipeline.pojo.transfer.YamlWithVersion
import com.tencent.devops.common.pipeline.template.PipelineTemplateType
import com.tencent.devops.process.constant.ProcessTemplateMessageCode
import com.tencent.devops.process.engine.cfg.PipelineIdGenerator
import com.tencent.devops.process.engine.utils.PipelineUtils
import com.tencent.devops.process.pojo.pipeline.PipelineResourceWithoutVersion
import com.tencent.devops.process.pojo.pipeline.PipelineTemplateInstanceBasicInfo
import com.tencent.devops.process.pojo.pipeline.version.PipelineVersionCreateReq
import com.tencent.devops.process.pojo.pipeline.version.PipelineYamlWebhookReq
import com.tencent.devops.process.service.PipelineAsCodeService
import com.tencent.devops.process.service.StageTagService
import com.tencent.devops.process.service.pipeline.version.PipelineResourceFactory
import com.tencent.devops.process.service.pipeline.version.PipelineVersionCreateContext
import com.tencent.devops.process.service.pipeline.version.PipelineVersionGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelParser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PipelineYamlWebhookReqConvert @Autowired constructor(
    private val pipelineIdGenerator: PipelineIdGenerator,
    private val pipelineResourceFactory: PipelineResourceFactory,
    private val pipelineVersionGenerator: PipelineVersionGenerator,
    private val pipelineAsCodeService: PipelineAsCodeService,
    private val pipelineTemplateModelParser: PipelineTemplateModelParser,
    private val stageTagService: StageTagService,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService
) : PipelineVersionCreateReqConverter {
    override fun support(request: PipelineVersionCreateReq): Boolean {
        return request is PipelineYamlWebhookReq
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun convert(
        userId: String,
        projectId: String,
        pipelineId: String?,
        version: Int?,
        request: PipelineVersionCreateReq
    ): PipelineVersionCreateContext {
        request as PipelineYamlWebhookReq
        with(request) {
            if (yamlFileInfo == null) {
                throw IllegalArgumentException("yamlFileInfo is null")
            }
            val (modelAndSetting, yamlWithVersion) = pipelineVersionGenerator.yaml2model(
                userId = userId,
                projectId = projectId,
                yaml = yaml,
                yamlFileName = yamlFileName,
                isDefaultBranch = isDefaultBranch,
                branchName = branchName
            )
            return if (modelAndSetting.model.fromTemplate == true) {
                convertFromTemplate(
                    userId = userId,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    modelAndSetting = modelAndSetting,
                    yamlWithVersion = yamlWithVersion
                )
            } else {
                convertFromNonTemplate(
                    userId = userId,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    modelAndSetting = modelAndSetting,
                    yamlWithVersion = yamlWithVersion
                )
            }
        }
    }

    private fun PipelineYamlWebhookReq.convertFromNonTemplate(
        userId: String,
        projectId: String,
        pipelineId: String?,
        modelAndSetting: PipelineModelAndSetting,
        yamlWithVersion: YamlWithVersion?
    ): PipelineVersionCreateContext {
        // 生成流水线ID
        val newPipelineId = pipelineId ?: pipelineIdGenerator.getNextId()
        // 流水线名称实际取值优先级：setting > model > fileName
        val pipelineName = modelAndSetting.setting.pipelineName.takeIf {
            it.isNotBlank()
        } ?: modelAndSetting.model.name.ifBlank {
            yamlFileName
        }

        val model = modelAndSetting.model.copy(
            name = pipelineName
        )
        val pipelineBasicInfo = pipelineResourceFactory.createPipelineBasicInfo(
            projectId = projectId,
            pipelineId = newPipelineId,
            channelCode = ChannelCode.BS,
            pipelineName = pipelineName,
            pipelineDesc = model.desc,
        )

        val (versionStatus, versionAction) = if (isDefaultBranch) {
            Pair(VersionStatus.RELEASED, PipelineVersionAction.CREATE_RELEASE)
        } else {
            Pair(VersionStatus.BRANCH, PipelineVersionAction.CREATE_BRANCH)
        }

        val pipelineAsCodeSettings = modelAndSetting.setting.pipelineAsCodeSettings?.copy(
            enable = true
        ) ?: PipelineAsCodeSettings(enable = true)
        val pipelineSettingWithoutVersion = modelAndSetting.setting.copy(
            projectId = projectId,
            pipelineId = newPipelineId,
            pipelineName = pipelineName,
            pipelineAsCodeSettings = pipelineAsCodeSettings
        )

        val pipelineResourceWithoutVersion = PipelineResourceWithoutVersion(
            projectId = projectId,
            pipelineId = newPipelineId,
            model = model,
            yaml = yamlWithVersion?.yamlStr,
            yamlVersion = yamlWithVersion?.versionTag,
            creator = userId,
            createTime = LocalDateTime.now(),
            updater = userId,
            updateTime = LocalDateTime.now(),
            status = versionStatus,
            branchAction = BranchVersionAction.ACTIVE.takeIf {
                versionStatus == VersionStatus.BRANCH
            },
            description = description,
        )

        val pipelineDialect = pipelineAsCodeService.getPipelineDialect(
            projectId = projectId,
            asCodeSettings = pipelineSettingWithoutVersion.pipelineAsCodeSettings
        )
        val pipelineModelBasicInfo = pipelineResourceFactory.createPipelineModelBasicInfo(
            model = model,
            projectId = projectId,
            pipelineId = newPipelineId,
            userId = userId,
            create = pipelineId == null,
            versionStatus = versionStatus,
            channelCode = ChannelCode.BS,
            pipelineDialect = pipelineDialect
        )
        return PipelineVersionCreateContext(
            userId = userId,
            projectId = projectId,
            pipelineId = newPipelineId,
            versionAction = versionAction,
            newPipeline = pipelineId == null,
            pipelineBasicInfo = pipelineBasicInfo,
            pipelineModelBasicInfo = pipelineModelBasicInfo,
            pipelineResourceWithoutVersion = pipelineResourceWithoutVersion,
            pipelineSettingWithoutVersion = pipelineSettingWithoutVersion,
            enablePac = true,
            yamlFileInfo = yamlFileInfo,
            branchName = branchName
        )
    }

    @Suppress("LongMethod")
    private fun PipelineYamlWebhookReq.convertFromTemplate(
        userId: String,
        projectId: String,
        pipelineId: String?,
        modelAndSetting: PipelineModelAndSetting,
        yamlWithVersion: YamlWithVersion?
    ): PipelineVersionCreateContext {
        // 生成流水线ID
        val newPipelineId = pipelineId ?: pipelineIdGenerator.getNextId()
        // 流水线名称实际取值优先级：setting > model > fileName
        val pipelineName = modelAndSetting.setting.pipelineName.takeIf {
            it.isNotBlank()
        } ?: modelAndSetting.model.name.ifBlank {
            yamlFileName
        }

        val model = modelAndSetting.model.copy(
            name = pipelineName
        )
        val pipelineBasicInfo = pipelineResourceFactory.createPipelineBasicInfo(
            projectId = projectId,
            pipelineId = newPipelineId,
            channelCode = ChannelCode.BS,
            pipelineName = pipelineName,
            pipelineDesc = model.desc,
        )

        val (versionStatus, versionAction) = if (isDefaultBranch) {
            Pair(VersionStatus.RELEASED, PipelineVersionAction.CREATE_RELEASE)
        } else {
            Pair(VersionStatus.BRANCH, PipelineVersionAction.CREATE_BRANCH)
        }

        val templateResource = pipelineTemplateModelParser.parseTemplateDescriptor(
            projectId = projectId,
            repoHashId = yamlFileInfo!!.repoHashId,
            descriptor = model,
            webhookRef = branchName
        )
        if (templateResource.model !is Model) {
            throw ErrorCodeException(
                errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_TYPE_MODEL_TYPE_NOT_MATCH
            )
        }
        val templateInfo =
            pipelineTemplateInfoService.get(projectId = projectId, templateId = templateResource.templateId)
        if (templateInfo.type != PipelineTemplateType.PIPELINE) {
            throw ErrorCodeException(
                errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_INSTANCE_NEED_PIPELINE_TYPE,
            )
        }

        val templateModel = templateResource.model as Model
        val pipelineParams = PipelineUtils.mergeTemplateParams(
            templateParams = templateModel.getTriggerContainer().params,
            templateParameters = model.templateVariables,
        )
        val defaultStageTagId = stageTagService.getDefaultStageTag().data?.id
        val instanceModel = PipelineUtils.instanceModelV2(
            templateModel = templateResource.model as Model,
            pipelineName = pipelineName,
            buildNo = null,
            param = pipelineParams,
            instanceFromTemplate = true,
            defaultStageTagId = defaultStageTagId,
            templateId = templateResource.templateId,
            triggerConfigs = model.triggerConfigs
        )

        val pipelineAsCodeSettings = modelAndSetting.setting.pipelineAsCodeSettings?.copy(
            enable = true
        ) ?: PipelineAsCodeSettings(enable = true)
        val pipelineSettingWithoutVersion = modelAndSetting.setting.copy(
            projectId = projectId,
            pipelineId = newPipelineId,
            pipelineName = pipelineName,
            pipelineAsCodeSettings = pipelineAsCodeSettings
        )

        val pipelineResourceWithoutVersion = PipelineResourceWithoutVersion(
            projectId = projectId,
            pipelineId = newPipelineId,
            model = model,
            yaml = yamlWithVersion?.yamlStr,
            yamlVersion = yamlWithVersion?.versionTag,
            instanceModel = instanceModel,
            creator = userId,
            createTime = LocalDateTime.now(),
            updater = userId,
            updateTime = LocalDateTime.now(),
            status = versionStatus,
            branchAction = BranchVersionAction.ACTIVE.takeIf {
                versionStatus == VersionStatus.BRANCH
            },
            description = description,
        )
        val pipelineDialect = pipelineAsCodeService.getPipelineDialect(
            projectId = projectId,
            asCodeSettings = pipelineAsCodeSettings
        )
        val pipelineModelBasicInfo = pipelineResourceFactory.createPipelineModelBasicInfo(
            model = instanceModel,
            projectId = projectId,
            pipelineId = newPipelineId,
            userId = userId,
            create = pipelineId == null,
            versionStatus = versionStatus,
            channelCode = ChannelCode.BS,
            pipelineDialect = pipelineDialect
        )
        val useTemplateSetting =
            model.overrideTemplateSettingGroups != null && model.overrideTemplateSettingGroups!!.isEmpty()
        val templateInstanceBasicInfo = PipelineTemplateInstanceBasicInfo(
            templateId = templateResource.templateId,
            templateName = templateInfo.name,
            templateVersion = templateResource.version,
            templateVersionName = templateResource.versionName,
            instanceType = PipelineInstanceTypeEnum.CONSTRAINT,
            useTemplateSetting = useTemplateSetting
        )
        return PipelineVersionCreateContext(
            userId = userId,
            projectId = projectId,
            pipelineId = newPipelineId,
            versionAction = versionAction,
            newPipeline = pipelineId == null,
            pipelineBasicInfo = pipelineBasicInfo,
            pipelineModelBasicInfo = pipelineModelBasicInfo,
            pipelineResourceWithoutVersion = pipelineResourceWithoutVersion,
            pipelineSettingWithoutVersion = pipelineSettingWithoutVersion,
            templateInstanceBasicInfo = templateInstanceBasicInfo,
            enablePac = true,
            yamlFileInfo = yamlFileInfo,
            branchName = branchName
        )
    }
}
