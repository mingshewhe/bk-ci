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
import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.PipelineModelAndSetting
import com.tencent.devops.common.pipeline.pojo.TemplateInstanceTriggerConfig
import com.tencent.devops.common.pipeline.pojo.TemplateVariable
import com.tencent.devops.process.constant.ProcessTemplateMessageCode
import com.tencent.devops.process.engine.cfg.PipelineIdGenerator
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.engine.utils.TemplateInstanceUtil
import com.tencent.devops.process.pojo.pipeline.PipelineResourceWithoutVersion
import com.tencent.devops.process.pojo.pipeline.version.PipelineDraftSaveReq
import com.tencent.devops.process.pojo.pipeline.version.PipelineVersionCreateReq
import com.tencent.devops.process.pojo.template.TemplateRefType
import com.tencent.devops.process.service.pipeline.version.PipelineResourceFactory
import com.tencent.devops.process.service.pipeline.version.PipelineVersionCreateContext
import com.tencent.devops.process.service.pipeline.version.PipelineVersionGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateRelatedService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.yaml.PipelineYamlService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 流水线草稿保存转换器
 */
@Service
class PipelineDraftSaveReqConvert(
    private val pipelineIdGenerator: PipelineIdGenerator,
    private val pipelineResourceFactory: PipelineResourceFactory,
    private val pipelineVersionGenerator: PipelineVersionGenerator,
    private val pipelineTemplateRelatedService: PipelineTemplateRelatedService,
    private val pipelineVersionCommonConvert: PipelineVersionCommonConvert,
    private val pipelineYamlService: PipelineYamlService,
    private val pipelineRepositoryService: PipelineRepositoryService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
) : PipelineVersionCreateReqConverter {
    override fun support(request: PipelineVersionCreateReq): Boolean {
        return request is PipelineDraftSaveReq
    }

    override fun convert(
        userId: String,
        projectId: String,
        pipelineId: String?,
        version: Int?,
        request: PipelineVersionCreateReq
    ): PipelineVersionCreateContext {
        request as PipelineDraftSaveReq
        with(request) {
            val (modelAndSetting, yamlWithVersion) = if (storageType == PipelineStorageType.YAML) {
                if (yaml.isNullOrEmpty()) {
                    throw IllegalArgumentException("yaml can not be empty")
                }
                pipelineVersionGenerator.yaml2model(
                    userId = userId,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    yaml = yaml!!,
                )
            } else {
                if (modelAndSetting == null) {
                    throw IllegalArgumentException("modelAndSetting can not be null")
                }
                val newModel = createPipelineModel(projectId = projectId, pipelineId = pipelineId)
                val newModelAndSetting = PipelineModelAndSetting(
                    model = newModel,
                    setting = modelAndSetting!!.setting
                )
                val newYaml = pipelineVersionGenerator.model2yaml(
                    userId = userId,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    modelAndSetting = newModelAndSetting,
                    oldYaml =  pipelineId?.let {
                        pipelineRepositoryService.getPipelineResourceVersion(
                            projectId = projectId,
                            pipelineId = it,
                            version = request.baseVersion,
                            includeDraft = true
                        )?.yaml
                    } ?: ""
                )
                Pair(newModelAndSetting, newYaml)
            }
            // 生成流水线ID
            val newPipelineId = pipelineId ?: pipelineIdGenerator.getNextId()
            val pipelineSettingWithoutVersion = modelAndSetting.setting.copy(
                projectId = projectId,
                pipelineId = newPipelineId
            )

            val versionStatus = VersionStatus.COMMITTING
            val pipelineResourceWithoutVersion = PipelineResourceWithoutVersion(
                projectId = projectId,
                pipelineId = newPipelineId,
                model = modelAndSetting.model,
                yaml = yamlWithVersion?.yamlStr,
                yamlVersion = yamlWithVersion?.versionTag,
                creator = userId,
                createTime = LocalDateTime.now(),
                updater = userId,
                updateTime = LocalDateTime.now(),
                status = versionStatus,
                baseVersion = baseVersion
            )
            // 通过路径引用的方式,模版yaml文件所属的仓库ID应与流水线相同
            val pipelineYamlInfo = pipelineYamlService.getPipelineYamlInfo(
                projectId = projectId,
                pipelineId = newPipelineId
            )
            return pipelineVersionCommonConvert.convert(
                userId = userId,
                projectId = projectId,
                pipelineId = newPipelineId,
                version = version,
                pipelineResourceWithoutVersion = pipelineResourceWithoutVersion,
                pipelineSettingWithoutVersion = pipelineSettingWithoutVersion,
                versionStatus = versionStatus,
                versionAction = PipelineVersionAction.SAVE_DRAFT,
                repoHashId = pipelineYamlInfo?.repoHashId
            )
        }
    }

    private fun PipelineDraftSaveReq.createPipelineModel(
        projectId: String,
        pipelineId: String?
    ): Model {
        // 前端传过来的model是完整的model,如果是模版实例化的,需要转换成引用的方式
        val model = modelAndSetting!!.model

        // 前端传过来的参数是模版+流水线自定义的,templateVariables只需要流水线自定义的值
        val overrideParamIds = model.overrideTemplateField?.paramIds
        val templateVariables = model.getTriggerContainer().params.filter {
            overrideParamIds?.contains(it.id) ?: false
        }.map { TemplateVariable(it) }

        // 前端传过来的是所有的触发器,triggerConfigs只需要流水线自定义的
        val overrideTriggerStepIds = model.overrideTemplateField?.triggerStepIds
        val triggerConfigs = model.getTriggerContainer().elements.filter { element ->
            element.stepId?.let { overrideTriggerStepIds?.contains(it) } ?: false
        }.map { TemplateInstanceTriggerConfig(it) }

        val recommendedVersion = TemplateInstanceUtil.getRecommendedVersion(
            buildNo = model.getTriggerContainer().buildNo,
            params = model.getTriggerContainer().params,
            overrideReCommendedVersion = model.overrideTemplateField?.recommendedVersion
        )

        return if (model.fromTemplate == true) {
            val refType = when {
                !model.templateId.isNullOrEmpty() -> TemplateRefType.ID
                !model.templatePath.isNullOrEmpty() -> TemplateRefType.PATH
                else -> TemplateRefType.ID
            }

            pipelineResourceFactory.createPipelineModelRef(
                name = model.name,
                desc = model.desc,
                refType = refType,
                templatePath = model.templatePath,
                templateRef = model.templateRef,
                templateId = model.templateId,
                templateVersionName = model.templateVersionName,
                templateVariables = templateVariables,
                triggerConfigs = triggerConfigs,
                recommendedVersion = recommendedVersion,
                overrideTemplateField = model.overrideTemplateField
            )
        } else {
            val pipelineTemplateRelated = pipelineId?.let {
                pipelineTemplateRelatedService.get(projectId = projectId, pipelineId = pipelineId)
            }
            // 兼容历史数据,历史的模版实例化是一个完整的model,需要改造成按照模版引用的方式
            if (pipelineTemplateRelated != null &&
                pipelineTemplateRelated.instanceType == PipelineInstanceTypeEnum.CONSTRAINT
            ) {
                val templateResource = pipelineTemplateResourceService.get(
                    projectId = projectId,
                    templateId = pipelineTemplateRelated.templateId,
                    version = pipelineTemplateRelated.version
                )
                val templateModel = templateResource.model
                if (templateModel !is Model) {
                    throw ErrorCodeException(
                        errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_TYPE_MODEL_TYPE_NOT_MATCH
                    )
                }
                pipelineResourceFactory.createPipelineModelRef(
                    name = model.name,
                    desc = model.desc,
                    refType = TemplateRefType.ID,
                    templateId = pipelineTemplateRelated.templateId,
                    templateVersionName = pipelineTemplateRelated.versionName,
                    templateVariables = templateVariables,
                    triggerConfigs = triggerConfigs,
                    recommendedVersion = recommendedVersion,
                    overrideTemplateField = model.overrideTemplateField
                )
            } else {
                model
            }
        }
    }
}
