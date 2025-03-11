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

import com.tencent.devops.common.api.check.Preconditions
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.pojo.TemplateModelAndSetting
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.common.pipeline.pojo.transfer.TransferActionType
import com.tencent.devops.common.pipeline.pojo.transfer.TransferBody
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.common.pipeline.template.JobTemplateModel
import com.tencent.devops.common.pipeline.template.StageTemplateModel
import com.tencent.devops.common.pipeline.template.StepTemplateModel
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.process.pojo.template.v2.TemplateModelTransferResult
import com.tencent.devops.process.service.pipeline.PipelineTransferYamlService
import com.tencent.devops.project.api.service.ServiceAllocIdResource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 生成流水线模版模型
 */
@Service
class PipelineTemplateModelGenerator @Autowired constructor(
    private val client: Client,
    private val transferService: PipelineTransferYamlService
) {

    fun getDefaultTemplateModel(
        name: String,
        type: PipelineTemplateType,
        userId: String
    ): ITemplateModel {
        return when (type) {
            PipelineTemplateType.PIPELINE -> Model.defaultModel(name, userId)
            PipelineTemplateType.STAGE -> StageTemplateModel.defaultStageTemplate()
            PipelineTemplateType.JOB -> JobTemplateModel.defaultJobTemplate()
            PipelineTemplateType.STEP -> StepTemplateModel.defaultStepTemplate()
            else -> {
                throw ErrorCodeException(errorCode = "")
            }
        }
    }

    fun getDefaultSetting(
        type: PipelineTemplateType,
        projectId: String,
        templateId: String,
        templateName: String,
        desc: String?,
        creator: String
    ): PipelineSetting {
        return if (type == PipelineTemplateType.PIPELINE) {
            PipelineSetting.defaultSetting(
                projectId = projectId,
                pipelineId = templateId,
                pipelineName = templateName,
                creator = creator,
                updater = creator
            )
        } else {
            PipelineSetting(
                projectId = projectId,
                pipelineId = templateId,
                pipelineName = templateName,
                version = PipelineTemplateConstant.INIT_VERSION,
                desc = desc ?: "",
                pipelineAsCodeSettings = null,
                creator = creator,
                updater = creator
            )
        }
    }

    fun generateId(): Long {
        return client.get(ServiceAllocIdResource::class).generateSegmentId(TEMPLATE_BIZ_TAG_NAME).data!!
    }

    fun transfer(
        userId: String,
        projectId: String,
        storageType: PipelineStorageType,
        templateType: PipelineTemplateType?,
        templateModel: ITemplateModel?,
        templateSetting: PipelineSetting?,
        yaml: String?
    ): TemplateModelTransferResult {
        return if (storageType == PipelineStorageType.YAML) {
            val newYaml = Preconditions.checkNotNull(
                yaml,
                "yaml must not be null"
            )
            val transferResult = transferService.transfer(
                userId = userId,
                projectId = projectId,
                pipelineId = null,
                actionType = TransferActionType.TEMPLATE_YAML2MODEL_PIPELINE,
                data = TransferBody(
                    oldYaml = newYaml
                )
            )
            val newTemplateModel = Preconditions.checkNotNull(
                transferResult.templateModelAndSetting?.templateModel,
                "The transfer data is incorrect, so the modelAndYaml.templateModel.model must not be null"
            )
            val newTemplateSetting = Preconditions.checkNotNull(
                transferResult.templateModelAndSetting?.setting,
                "The transfer data is incorrect, " +
                    "so the modelAndYaml.templateModel.templateSetting must not be null"
            )
            TemplateModelTransferResult(
                templateType = PipelineTemplateType.PIPELINE,
                templateModel = newTemplateModel,
                templateSetting = newTemplateSetting,
                yamlWithVersion = transferResult.yamlWithVersion
            )
        } else {
            val newTemplateType = Preconditions.checkNotNull(
                templateType,
                "template type must not be null"
            )
            val newTemplateModel = Preconditions.checkNotNull(
                templateModel,
                "template model must not be null"
            )
            val newTemplateSetting = Preconditions.checkNotNull(
                templateSetting,
                "template setting must not be null"
            )
            when (templateType) {
                PipelineTemplateType.PIPELINE -> {
                    val result = transferService.transfer(
                        userId = userId,
                        projectId = projectId,
                        pipelineId = null,
                        actionType = TransferActionType.TEMPLATE_MODEL2YAML_PIPELINE,
                        data = TransferBody(
                            templateModelAndSetting = TemplateModelAndSetting(
                                templateModel = newTemplateModel,
                                setting = newTemplateSetting,
                            ),
                            oldYaml = ""
                        )
                    )
                    TemplateModelTransferResult(
                        templateType = newTemplateType,
                        templateModel = newTemplateModel,
                        templateSetting = newTemplateSetting,
                        yamlWithVersion = result.yamlWithVersion
                    )
                }

                PipelineTemplateType.STAGE -> {
                    val result = transferService.transfer(
                        userId = userId,
                        projectId = projectId,
                        pipelineId = null,
                        actionType = TransferActionType.TEMPLATE_MODEL2YAML_STAGE,
                        data = TransferBody(
                            templateModelAndSetting = TemplateModelAndSetting(
                                templateModel = templateModel!!,
                                setting = newTemplateSetting
                            ),
                            oldYaml = ""
                        )
                    )
                    TemplateModelTransferResult(
                        templateType = newTemplateType,
                        templateModel = newTemplateModel,
                        yamlWithVersion = result.yamlWithVersion,
                        templateSetting = newTemplateSetting
                    )
                }

                PipelineTemplateType.JOB -> {
                    val result = transferService.transfer(
                        userId = userId,
                        projectId = projectId,
                        pipelineId = null,
                        actionType = TransferActionType.TEMPLATE_MODEL2YAML_JOB,
                        data = TransferBody(
                            templateModelAndSetting = TemplateModelAndSetting(
                                templateModel = templateModel!!,
                                setting = newTemplateSetting
                            ),
                            oldYaml = ""
                        )
                    )
                    TemplateModelTransferResult(
                        templateType = newTemplateType,
                        templateModel = newTemplateModel,
                        yamlWithVersion = result.yamlWithVersion,
                        templateSetting = newTemplateSetting
                    )
                }

                PipelineTemplateType.STEP -> {
                    val result = transferService.transfer(
                        userId = userId,
                        projectId = projectId,
                        pipelineId = null,
                        actionType = TransferActionType.TEMPLATE_MODEL2YAML_STEP,
                        data = TransferBody(
                            templateModelAndSetting = TemplateModelAndSetting(
                                templateModel = templateModel!!,
                                setting = newTemplateSetting
                            ),
                            oldYaml = ""
                        )
                    )
                    TemplateModelTransferResult(
                        templateType = newTemplateType,
                        templateModel = newTemplateModel,
                        yamlWithVersion = result.yamlWithVersion,
                        templateSetting = newTemplateSetting
                    )
                }

                else -> {
                    throw IllegalArgumentException("unknown template type: $templateType")
                }
            }
        }
    }

    companion object {
        private const val TEMPLATE_BIZ_TAG_NAME = "TEMPLATE"
    }
}
