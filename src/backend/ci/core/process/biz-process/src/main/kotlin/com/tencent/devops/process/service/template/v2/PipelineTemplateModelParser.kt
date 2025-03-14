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
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.container.Container
import com.tencent.devops.common.pipeline.container.JobTemplateContainer
import com.tencent.devops.common.pipeline.container.Stage
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.StepTemplateElement
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.common.pipeline.template.JobTemplateModel
import com.tencent.devops.common.pipeline.template.StageTemplateModel
import com.tencent.devops.common.pipeline.template.StepTemplateModel
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版模型解析器
 */
@Service
class PipelineTemplateModelParser @Autowired constructor(
    private val pipelineTemplateResourceService: PipelineTemplateResourceService
) {

    fun parseModel(model: Model): Model {
        val newStages = parseStages(model.stages)
        return model.copy(stages = newStages)
    }

    fun parseTemplateModel(model: ITemplateModel): ITemplateModel {
        return when (model) {
            is Model -> {
                parseModel(model)
            }

            is StageTemplateModel -> {
                model.copy(stages = parseStages(model.stages))
            }

            is JobTemplateModel -> {
                model.copy(containers = parseContainers(model.containers))
            }

            is StepTemplateModel -> {
                val newElements = parseElements(model.container.elements)
                val newContainer = model.container.copyElements(newElements)
                model.copy(container = newContainer)
            }

            else -> model
        }
    }

    private fun parseStages(stages: List<Stage>): List<Stage> {
        val newStages = mutableListOf<Stage>()
        stages.forEach { stage ->
            val newStage = if (stage.fromTemplate == true) {
                parseStageTemplate(stage)
            } else {
                val newContainers = parseContainers(stage.containers)
                listOf(stage.copy(containers = newContainers))
            }
            newStages.addAll(newStage)
        }
        return newStages
    }

    private fun parseContainers(containers: List<Container>): List<Container> {
        val newContainers = mutableListOf<Container>()
        containers.forEach { container ->
            val newContainer = if (container is JobTemplateContainer) {
                parseJobTemplateContainer(container)
            } else {
                val newElements = parseElements(container.elements)
                listOf(container.copyElements(newElements))
            }
            newContainers.addAll(newContainer)
        }
        return newContainers
    }

    private fun parseElements(elements: List<Element>): List<Element> {
        val newElements = mutableListOf<Element>()
        elements.forEach { element ->
            val newElement = if (element is StepTemplateElement) {
                parseStepTemplateElement(element)
            } else {
                listOf(element)
            }
            newElements.addAll(newElement)
        }
        return newElements
    }

    private fun parseStageTemplate(stage: Stage): List<Stage> {
        val templateId = stage.templateId!!
        val templateVersion = stage.template!!
        val templateModel = pipelineTemplateResourceService.getTemplateResourceVersion(
            templateId = templateId,
            version = TODO()
        )?.model ?: throw ErrorCodeException(
            errorCode = "",
            params = arrayOf(templateId, templateVersion.toString())
        )
        if (templateModel !is StageTemplateModel) {
            // 模型不匹配
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        return templateModel.stages
    }

    private fun parseJobTemplateContainer(container: JobTemplateContainer): List<Container> {
        val templateId = container.templateId!!
        val templateVersion = container.template!!
        val templateModel = pipelineTemplateResourceService.getTemplateResourceVersion(
            templateId = templateId,
            version = TODO()
        )?.model ?: throw ErrorCodeException(
            errorCode = "",
            params = arrayOf(templateId, templateVersion)
        )
        if (templateModel !is JobTemplateModel) {
            // 模型不匹配
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        return templateModel.containers
    }

    private fun parseStepTemplateElement(element: StepTemplateElement): List<Element> {
        val templateId = element.templateId!!
        val templateVersion = element.template!!
        val templateModel = pipelineTemplateResourceService.getTemplateResourceVersion(
            templateId = templateId,
            version = TODO()
        )?.model ?: throw ErrorCodeException(
            errorCode = "",
            params = arrayOf(templateId, templateVersion)
        )
        if (templateModel !is StepTemplateModel) {
            // 模型不匹配
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        return templateModel.container.elements
    }
}
