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

package com.tencent.devops.process.service

import com.tencent.devops.common.api.enums.RepositoryType
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.TemplateDescriptor
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.constant.ProcessTemplateMessageCode
import com.tencent.devops.process.dao.template.PipelineTemplateInfoDao
import com.tencent.devops.process.dao.template.PipelineTemplateResourceDao
import com.tencent.devops.process.engine.dao.PipelineYamlInfoDao
import com.tencent.devops.process.engine.dao.PipelineYamlVersionDao
import com.tencent.devops.process.engine.utils.PipelineUtils
import com.tencent.devops.process.pojo.pipeline.PipelineYamlVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.repository.api.ServiceRepositoryResource
import com.tencent.devops.repository.api.scm.ServiceScmFileApiResource
import com.tencent.devops.repository.api.scm.ServiceScmRepositoryApiResource
import com.tencent.devops.repository.pojo.credential.AuthRepository
import com.tencent.devops.scm.api.pojo.repository.git.GitScmServerRepository
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版模型解析器
 */
@Service
class PipelineModelParser @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineTemplateInfoDao: PipelineTemplateInfoDao,
    private val pipelineTemplateResourceDao: PipelineTemplateResourceDao,
    private val pipelineYamlInfoDao: PipelineYamlInfoDao,
    private val pipelineYamlVersionDao: PipelineYamlVersionDao,
    private val client: Client,
    private val stageTagService: StageTagService,
) {

    /**
     * 解析模版编排,将模版引用转换成具体的编排
     */
    fun parseModel(
        projectId: String,
        pipelineId: String,
        model: Model,
        branchName: String? = null
    ): Model {
        return if (model.fromTemplate == true) {
            val pipelineYamlInfo = pipelineYamlInfoDao.get(
                dslContext = dslContext,
                projectId = projectId,
                pipelineId = pipelineId,
            )
            val templateResource = parseTemplateDescriptor(
                projectId = projectId,
                descriptor = model,
                repoHashId = pipelineYamlInfo?.repoHashId,
                branchName = branchName
            )
            instanceModel(
                model = model,
                templateResource = templateResource
            )
        } else {
            model
        }
    }

    /*fun parseTemplateModel(
        projectId: String,
        model: ITemplateModel
    ): ITemplateModel {
        return when (model) {
            is Model -> {
                parseModel(projectId = projectId, model = model)
            }

            is StageTemplateModel -> {
                model.copy(stages = parseStages(projectId = projectId, stages = model.stages))
            }

            is JobTemplateModel -> {
                model.copy(containers = parseContainers(projectId = projectId, containers = model.containers))
            }

            is StepTemplateModel -> {
                val newElements = parseElements(projectId = projectId, elements = model.container.elements)
                val newContainer = model.container.copyElements(newElements)
                model.copy(container = newContainer)
            }

            else -> model
        }
    }

    private fun parseStages(
        projectId: String,
        stages: List<Stage>
    ): List<Stage> {
        val newStages = mutableListOf<Stage>()
        stages.forEach { stage ->
            val newStage = if (stage.fromTemplate == true) {
                parseStageTemplate(projectId = projectId, stage = stage)
            } else {
                val newContainers = parseContainers(projectId = projectId, containers = stage.containers)
                listOf(stage.copy(containers = newContainers))
            }
            newStages.addAll(newStage)
        }
        return newStages
    }

    private fun parseContainers(
        projectId: String,
        containers: List<Container>
    ): List<Container> {
        val newContainers = mutableListOf<Container>()
        containers.forEach { container ->
            val newContainer = if (container is JobTemplateContainer) {
                parseJobTemplateContainer(
                    projectId = projectId,
                    container = container
                )
            } else {
                val newElements = parseElements(projectId = projectId, elements = container.elements)
                listOf(container.copyElements(newElements))
            }
            newContainers.addAll(newContainer)
        }
        return newContainers
    }

    private fun parseElements(
        projectId: String,
        elements: List<Element>
    ): List<Element> {
        val newElements = mutableListOf<Element>()
        elements.forEach { element ->
            val newElement = if (element is StepTemplateElement) {
                parseStepTemplateElement(
                    projectId = projectId,
                    element = element
                )
            } else {
                listOf(element)
            }
            newElements.addAll(newElement)
        }
        return newElements
    }

    private fun parseStageTemplate(
        projectId: String,
        stage: Stage
    ): List<Stage> {
        val templateModel = getPipelineTemplateResource(
            projectId = projectId,
            descriptor = stage
        ).model
        if (templateModel !is StageTemplateModel) {
            // 模型不匹配
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        return templateModel.stages
    }

    private fun parseJobTemplateContainer(
        projectId: String,
        container: JobTemplateContainer
    ): List<Container> {
        val templateModel = getPipelineTemplateResource(
            projectId = projectId,
            descriptor = container
        ).model
        if (templateModel !is JobTemplateModel) {
            // 模型不匹配
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        return templateModel.containers
    }

    private fun parseStepTemplateElement(
        projectId: String,
        element: StepTemplateElement
    ): List<Element> {
        val templateModel = getPipelineTemplateResource(
            projectId = projectId,
            descriptor = element
        ).model
        if (templateModel !is StepTemplateModel) {
            // 模型不匹配
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        return templateModel.container.elements
    }*/

    fun instanceModel(
        model: Model,
        templateResource: PipelineTemplateResource
    ): Model {
        if (templateResource.model !is Model) {
            throw ErrorCodeException(
                errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_TYPE_MODEL_TYPE_NOT_MATCH
            )
        }

        val templateModel = templateResource.model as Model
        val defaultStageTagId = stageTagService.getDefaultStageTag().data?.id
        return PipelineUtils.mergeModel(
            model = model,
            templateModel = templateModel,
            defaultStageTagId = defaultStageTagId
        )
    }

    /**
     * @param repoHashId 仓库hashId,当通过模版路径引用时，必须传入
     * @param branchName 触发分支名称,当webhook触发时才有值
     */
    fun parseTemplateDescriptor(
        projectId: String,
        descriptor: TemplateDescriptor,
        repoHashId: String? = null,
        branchName: String? = null
    ): PipelineTemplateResource {
        with(descriptor) {
            return when {
                // 通过模版ID方式引用
                !templateId.isNullOrEmpty() -> {
                    if (templateVersionName.isNullOrEmpty()) {
                        throw ErrorCodeException(
                            errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_VERSION_NAME_NOT_EMPTY,
                        )
                    }
                    logger.info("parse template descriptor by id|$projectId|$templateId|$templateVersionName")
                    pipelineTemplateInfoDao.get(
                        dslContext = dslContext,
                        projectId = projectId,
                        templateId = templateId!!
                    ) ?: throw ErrorCodeException(
                        errorCode = ERROR_TEMPLATE_NOT_EXISTS,
                        params = arrayOf(templateId!!)
                    )
                    pipelineTemplateResourceDao.getLatestRecord(
                        dslContext = dslContext,
                        projectId = projectId,
                        templateId = templateId!!,
                        versionName = templateVersionName!!
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_VERSION_BY_ID_NOT_FOUND,
                        params = arrayOf(templateId!!, templateVersionName!!)
                    )
                }

                // 通过模版路径方式引用
                !templatePath.isNullOrEmpty() -> {
                    if (repoHashId.isNullOrEmpty()) {
                        throw IllegalArgumentException("repoHashId is required")
                    }
                    logger.info("parse template descriptor by path|$projectId|$repoHashId|$templatePath|$templateRef")
                    // 1. 获取yaml文件绑定的模版
                    val pipelineYamlInfo = pipelineYamlInfoDao.get(
                        dslContext = dslContext,
                        projectId = projectId,
                        repoHashId = repoHashId,
                        filePath = templatePath!!
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessTemplateMessageCode.ERROR_YAML_FOR_TEMPLATE_NOT_FOUND,
                        params = arrayOf(templatePath!!)
                    )
                    // 2. 获取yaml文件对应的模版版本
                    val pipelineYamlVersion = getPipelineYamlVersion(
                        projectId = projectId,
                        repoHashId = repoHashId,
                        branchName = branchName
                    )
                    logger.info(
                        "parse template descriptor result by path|$projectId|$repoHashId|" +
                                "${pipelineYamlInfo.pipelineId}|${pipelineYamlVersion.version}"
                    )

                    pipelineTemplateInfoDao.get(
                        dslContext = dslContext,
                        projectId = projectId,
                        templateId = pipelineYamlInfo.pipelineId
                    )
                    pipelineTemplateResourceDao.getLatestRecord(
                        dslContext = dslContext,
                        projectId = projectId,
                        templateId = pipelineYamlInfo.pipelineId,
                        version = pipelineYamlVersion.version.toLong()
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_VERSION_BY_PATH_NOT_FOUND
                    )
                }

                else -> {
                    throw ErrorCodeException(
                        errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_REF_TYPE
                    )
                }
            }
        }
    }

    private fun TemplateDescriptor.getPipelineYamlVersion(
        projectId: String,
        repoHashId: String,
        branchName: String?
    ): PipelineYamlVersion {
        val repository = client.get(ServiceRepositoryResource::class).get(
            projectId = projectId,
            repositoryId = repoHashId,
            repositoryType = RepositoryType.ID
        ).data ?: throw ErrorCodeException(
            errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_YAML_REPOSITORY_NOT_FOUND
        )

        val authRepository = AuthRepository(repository)
        val serverRepository = client.get(ServiceScmRepositoryApiResource::class).getServerRepository(
            projectId = projectId,
            authRepository = authRepository
        ).data
        if (serverRepository !is GitScmServerRepository) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_NOT_SUPPORT_REPOSITORY_TYPE_ENABLE_PAC
            )
        }
        val defaultBranch = serverRepository.defaultBranch

        /**
         * 1. 如果指定分支，则使用指定分支
         * 2. 如果没有指定分支,当webhook触发时,使用触发的分支,否则使用默认分支
         */
        val ref = templateRef?.takeIf { it.isNotEmpty() } ?: branchName?.takeIf { it.isNotEmpty() } ?: defaultBranch
        // 这里后续看是否可以改成从T_PIPELINE_YAML_BRANCH_FILE表中获取
        val fileContent = client.get(ServiceScmFileApiResource::class).getFileContent(
            projectId = projectId,
            path = templatePath!!,
            ref = ref,
            authRepository = authRepository
        ).data!!
        return getPipelineYamlVersion(
            projectId = projectId,
            repoHashId = repoHashId,
            filePath = templatePath!!,
            ref = ref,
            blobId = fileContent.blobId,
            defaultBranch = defaultBranch
        ) ?: throw ErrorCodeException(
            errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_YAML_VERSION_NOT_FOUND,
            params = arrayOf(ref, templatePath!!)
        )
    }

    /**
     * 获取触发时版本
     *
     * 1. 查看触发分支有没有blobId对应的版本
     * 2. 如果触发分支没有,则查看默认分支
     * 3. 如果默认分支也没有,则查看其他分支是不是有对应的版本
     */
    private fun getPipelineYamlVersion(
        projectId: String,
        repoHashId: String,
        filePath: String,
        ref: String,
        blobId: String,
        defaultBranch: String
    ): PipelineYamlVersion? {
        val pipelineBranchVersion = pipelineYamlVersionDao.getPipelineYamlVersion(
            dslContext = dslContext,
            projectId = projectId,
            repoHashId = repoHashId,
            filePath = filePath,
            ref = ref,
            blobId = blobId,
            branchAction = BranchVersionAction.ACTIVE.name
        )
        return if (ref == defaultBranch) {
            pipelineBranchVersion
        } else {
            pipelineBranchVersion ?: pipelineYamlVersionDao.getPipelineYamlVersion(
                dslContext = dslContext,
                projectId = projectId,
                repoHashId = repoHashId,
                filePath = filePath,
                ref = defaultBranch,
                blobId = blobId,
                branchAction = BranchVersionAction.ACTIVE.name
            ) ?: pipelineYamlVersionDao.getPipelineYamlVersion(
                dslContext = dslContext,
                projectId = projectId,
                repoHashId = repoHashId,
                filePath = filePath,
                blobId = blobId
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineModelParser::class.java)
    }
}
