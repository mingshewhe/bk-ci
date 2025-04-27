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

package com.tencent.devops.process.service.pipeline.version

import com.tencent.devops.common.api.enums.RepositoryType
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.CodeTargetAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.dao.PipelineSettingVersionDao
import com.tencent.devops.process.engine.dao.PipelineResourceDao
import com.tencent.devops.process.engine.dao.PipelineResourceVersionDao
import com.tencent.devops.process.engine.utils.PipelineUtils
import com.tencent.devops.process.pojo.pipeline.PipelineResourceOnlyVersion
import com.tencent.devops.process.pojo.pipeline.PipelineResourceVersion
import com.tencent.devops.process.pojo.pipeline.PrefetchReleaseResult
import com.tencent.devops.process.pojo.setting.PipelineSettingVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstancesRequest
import com.tencent.devops.process.service.StageTagService
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.utils.PipelineVersionUtils
import com.tencent.devops.repository.api.scm.ServiceScmRepositoryApiResource
import com.tencent.devops.scm.api.pojo.repository.git.GitScmServerRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Service

/**
 * 流水线版本生成器
 */
@Service
class PipelineVersionGenerator constructor(
    private val dslContext: DSLContext,
    private val pipelineResourceVersionDao: PipelineResourceVersionDao,
    private val pipelineResourceDao: PipelineResourceDao,
    private val pipelineSettingVersionDao: PipelineSettingVersionDao,
    private val client: Client,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val stageTagService: StageTagService
) {

    /**
     * 生成流水线默认版本
     */
    fun getDefaultVersion(
        versionStatus: VersionStatus,
        branchName: String? = null
    ): PipelineResourceOnlyVersion {
        return when (versionStatus) {
            VersionStatus.COMMITTING -> {
                PipelineResourceOnlyVersion(
                    version = INIT_VERSION,
                    settingVersion = INIT_VERSION
                )
            }

            VersionStatus.BRANCH -> {
                PipelineResourceOnlyVersion(
                    version = INIT_VERSION,
                    settingVersion = INIT_VERSION,
                    versionName = branchName
                )
            }

            else -> {
                val versionName = PipelineVersionUtils.getVersionName(
                    versionNum = INIT_VERSION,
                    pipelineVersion = INIT_VERSION,
                    triggerVersion = INIT_VERSION,
                    settingVersion = INIT_VERSION
                )
                PipelineResourceOnlyVersion(
                    version = INIT_VERSION,
                    versionName = versionName,
                    versionNum = INIT_VERSION,
                    pipelineVersion = INIT_VERSION,
                    triggerVersion = INIT_VERSION,
                    settingVersion = INIT_VERSION
                )
            }
        }
    }

    /**
     * 生成草稿版本
     */
    fun generateDraftVersion(
        latestResource: PipelineResourceVersion,
        latestSetting: PipelineSettingVersion
    ) = PipelineResourceOnlyVersion(
        version = latestResource.version + 1,
        settingVersion = latestSetting.version + 1,
        baseVersion = latestResource.version
    )

    /**
     * 生成分支版本
     */
    fun generateBranchVersion(
        latestResource: PipelineResourceVersion,
        latestSetting: PipelineSettingVersion,
        branchName: String
    ) = PipelineResourceOnlyVersion(
        version = latestResource.version + 1,
        settingVersion = latestSetting.version + 1,
        baseVersion = latestResource.version,
        versionName = branchName
    )

    /**
     * 生成分支版本
     */
    fun generateBranchVersion(
        projectId: String,
        pipelineId: String,
        branchName: String
    ): PipelineResourceOnlyVersion {
        val latestResource = pipelineResourceVersionDao.getLatestVersionResource(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = pipelineId
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_NON_LATEST_RELEASE_VERSION
        )
        val latestSetting = pipelineSettingVersionDao.getLatestSettingVersion(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = pipelineId
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_NON_LATEST_RELEASE_VERSION
        )
        return generateBranchVersion(
            latestResource = latestResource,
            latestSetting = latestSetting,
            branchName = branchName
        )
    }

    /**
     * 生成正式版本
     *
     * @param draftResource 草稿版本编排
     * @param newModel 新版编排
     * @param useTemplateSettings 是否使用模版设置
     */
    fun generateReleaseVersion(
        projectId: String,
        pipelineId: String,
        draftResource: PipelineResourceVersion? = null,
        newModel: Model,
        instanceFromTemplate: Boolean = false,
        useTemplateSettings: Boolean = false
    ): PipelineResourceOnlyVersion {
        val latestResource = pipelineResourceVersionDao.getLatestVersionResource(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = pipelineId
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_NON_LATEST_RELEASE_VERSION
        )
        val latestSetting = pipelineSettingVersionDao.getLatestSettingVersion(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = pipelineId
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_NON_LATEST_RELEASE_VERSION
        )
        val latestReleaseResource = pipelineResourceDao.getReleaseVersionResource(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = pipelineId
        )
        val (version, settingVersion) = if (draftResource == null) {
            // 不使用模版设置,则使用流水线最新版本,如果最新版本不存在,则创建一个新的
            val newSettingVersion = if (instanceFromTemplate && !useTemplateSettings) {
                latestReleaseResource?.settingVersion ?: (latestSetting.version + 1)
            } else {
                latestSetting.version + 1
            }
            Pair(latestResource.version + 1, newSettingVersion)
        } else {
            Pair(draftResource.version, draftResource.settingVersion)
        }
        // 如果没有正式版本,说明是第一次生成正式版本
        return if (latestReleaseResource == null) {
            val versionNum = INIT_VERSION
            val pipelineVersion = INIT_VERSION
            val triggerVersion = INIT_VERSION

            val versionName = PipelineVersionUtils.getVersionName(
                versionNum = versionNum,
                pipelineVersion = pipelineVersion,
                triggerVersion = triggerVersion,
                settingVersion = settingVersion
            )
            PipelineResourceOnlyVersion(
                version = version,
                versionName = versionName,
                versionNum = versionNum,
                pipelineVersion = pipelineVersion,
                triggerVersion = triggerVersion,
                settingVersion = settingVersion
            )
        } else {
            val versionNum = latestReleaseResource.versionNum?.let { it + 1 } ?: INIT_VERSION
            val pipelineVersion = PipelineVersionUtils.getPipelineVersion(
                currVersion = latestReleaseResource.pipelineVersion ?: latestReleaseResource.version,
                originModel = latestReleaseResource.model,
                newModel = newModel
            )
            val triggerVersion = PipelineVersionUtils.getTriggerVersion(
                currVersion = latestReleaseResource.triggerVersion ?: 0,
                originModel = latestReleaseResource.model,
                newModel = newModel
            ).coerceAtLeast(1)
            val versionName = PipelineVersionUtils.getVersionName(
                versionNum = versionNum,
                pipelineVersion = pipelineVersion,
                triggerVersion = triggerVersion,
                settingVersion = settingVersion
            )
            PipelineResourceOnlyVersion(
                version = version,
                versionName = versionName,
                versionNum = versionNum,
                pipelineVersion = pipelineVersion,
                triggerVersion = triggerVersion,
                settingVersion = settingVersion
            )
        }
    }

    fun getVersionStatusAndBranchName(
        projectId: String,
        templateId: String,
        templateVersion: Long,
        enablePac: Boolean,
        repoHashId: String?,
        targetAction: CodeTargetAction?,
        targetBranch: String? = null
    ): Pair<VersionStatus, String?> {
        return if (enablePac) {
            return getVersionStatusAndBranchNameWithPac(
                projectId = projectId,
                templateId = templateId,
                templateVersion = templateVersion,
                repoHashId = repoHashId,
                targetAction = targetAction,
                targetBranch = targetBranch
            )
        } else {
            Pair(VersionStatus.RELEASED, null)
        }
    }

    private fun getVersionStatusAndBranchNameWithPac(
        projectId: String,
        templateId: String,
        templateVersion: Long,
        repoHashId: String?,
        targetAction: CodeTargetAction?,
        targetBranch: String?
    ): Pair<VersionStatus, String?> {
        if (repoHashId.isNullOrBlank()) {
            throw IllegalArgumentException("repoHashId is null")
        }
        return when (targetAction) {

            CodeTargetAction.COMMIT_TO_MASTER -> {
                Pair(VersionStatus.RELEASED, null)
            }

            CodeTargetAction.CHECKOUT_BRANCH_AND_REQUEST_MERGE,
            CodeTargetAction.COMMIT_TO_SOURCE_BRANCH -> {
                val branchName = "$PAC_TEMPLATE_INSTANCE_BRANCH_PREFIX$templateId-$templateVersion"
                Pair(VersionStatus.BRANCH, branchName)
            }

            CodeTargetAction.COMMIT_TO_BRANCH -> {
                val defaultBranch = getDefaultBranch(projectId = projectId, repoHashId = repoHashId)
                if (defaultBranch == targetBranch) {
                    Pair(VersionStatus.RELEASED, null)
                } else {
                    Pair(VersionStatus.BRANCH, targetBranch)
                }
            }

            else -> {
                throw IllegalArgumentException("targetAction is illegal")
            }
        }
    }

    /**
     * 生成模版实例化版本
     *
     */
    fun generateInstanceVersion(
        projectId: String,
        pipelineId: String,
        newModel: Model,
        useTemplateSettings: Boolean,
        enablePac: Boolean,
        repoHashId: String?,
        targetAction: CodeTargetAction?,
        targetBranch: String? = null,
        defaultBranch: String? = null,
        templateId: String,
        templateVersion: Long
    ): PipelineResourceOnlyVersion {

        return if (enablePac) {
            generateInstanceVersionWithPac(
                projectId = projectId,
                pipelineId = pipelineId,
                newModel = newModel,
                useTemplateSettings = useTemplateSettings,
                repoHashId = repoHashId,
                targetAction = targetAction,
                targetBranch = targetBranch,
                defaultBranch = defaultBranch,
                templateId = templateId,
                templateVersion = templateVersion
            )
        } else {
            val resourceOnlyVersion = generateReleaseVersion(
                projectId = projectId,
                pipelineId = pipelineId,
                newModel = newModel,
                instanceFromTemplate = true,
                useTemplateSettings = useTemplateSettings
            )
            resourceOnlyVersion
        }
    }

    /**
     * 生成开启PAC实例化版本
     */
    fun generateInstanceVersionWithPac(
        projectId: String,
        pipelineId: String,
        newModel: Model,
        useTemplateSettings: Boolean,
        repoHashId: String?,
        targetAction: CodeTargetAction?,
        targetBranch: String? = null,
        defaultBranch: String? = null,
        templateId: String,
        templateVersion: Long,
    ): PipelineResourceOnlyVersion {
        if (repoHashId.isNullOrBlank()) {
            throw IllegalArgumentException("repoHashId is null")
        }
        return when (targetAction) {
            CodeTargetAction.COMMIT_TO_MASTER -> {
                generateReleaseVersion(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    newModel = newModel,
                    instanceFromTemplate = true,
                    useTemplateSettings = useTemplateSettings
                )
            }

            CodeTargetAction.CHECKOUT_BRANCH_AND_REQUEST_MERGE,
            CodeTargetAction.COMMIT_TO_SOURCE_BRANCH -> {
                val branchName = "$PAC_TEMPLATE_INSTANCE_BRANCH_PREFIX$templateId-$templateVersion"
                generateBranchVersion(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    branchName = branchName
                )
            }

            CodeTargetAction.COMMIT_TO_BRANCH -> {
                if (targetBranch == null) {
                    throw IllegalArgumentException("targetBranch is null")
                }
                val finalDefaultBranch =
                    defaultBranch ?: getDefaultBranch(projectId = projectId, repoHashId = repoHashId)
                // 如果选择的是默认分支,则应该发布正式版本
                if (targetBranch == finalDefaultBranch) {
                    generateReleaseVersion(
                        projectId = projectId,
                        pipelineId = pipelineId,
                        newModel = newModel,
                        instanceFromTemplate = true,
                        useTemplateSettings = useTemplateSettings
                    )
                } else {
                    generateBranchVersion(
                        projectId = projectId,
                        pipelineId = pipelineId,
                        branchName = targetBranch
                    )
                }
            }

            else -> {
                throw IllegalArgumentException("targetAction is illegal")
            }
        }
    }

    fun batchGenerateInstanceVersion(
        projectId: String,
        templateId: String,
        version: Long,
        useTemplateSettings: Boolean,
        request: PipelineTemplateInstancesRequest
    ): List<PrefetchReleaseResult> {
        val templateResource = pipelineTemplateResourceService.get(
            projectId = projectId,
            templateId = templateId,
            version = version
        )
        with(request) {
            val defaultBranch = targetAction?.takeIf { enablePac && it == CodeTargetAction.COMMIT_TO_BRANCH }?.let {
                getDefaultBranch(projectId = projectId, repoHashId = repoHashId)
            }

            val defaultStageTagId = stageTagService.getDefaultStageTag().data?.id
            return instanceReleaseInfos.map { releaseInfo ->
                val instanceModel = PipelineUtils.instanceModel(
                    templateModel = templateResource.model as Model,
                    pipelineName = releaseInfo.pipelineName,
                    buildNo = releaseInfo.buildNo,
                    param = releaseInfo.param,
                    instanceFromTemplate = true,
                    defaultStageTagId = defaultStageTagId,
                    templateId = templateId
                )
                val resourceOnlyVersion = generateInstanceVersion(
                    projectId = projectId,
                    pipelineId = releaseInfo.pipelineId,
                    newModel = instanceModel,
                    useTemplateSettings = useTemplateSettings,
                    enablePac = enablePac,
                    repoHashId = repoHashId,
                    targetAction = targetAction,
                    targetBranch = targetBranch,
                    defaultBranch = defaultBranch,
                    templateId = templateId,
                    templateVersion = version
                )
                PrefetchReleaseResult(
                    pipelineId = releaseInfo.pipelineId,
                    pipelineName = releaseInfo.pipelineName,
                    version = resourceOnlyVersion.version,
                    newVersionNum = resourceOnlyVersion.versionNum!!,
                    newVersionName = resourceOnlyVersion.versionName!!
                )
            }
        }

    }

    private fun getDefaultBranch(
        projectId: String,
        repoHashId: String?
    ): String? {
        if (repoHashId.isNullOrBlank()) {
            throw IllegalArgumentException("repoHashId is null")
        }
        val serverRepository = client.get(ServiceScmRepositoryApiResource::class).getServerRepositoryById(
            projectId = projectId,
            repositoryType = RepositoryType.ID,
            repoHashIdOrName = repoHashId
        ).data
        if (serverRepository !is GitScmServerRepository) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_NOT_SUPPORT_REPOSITORY_TYPE_ENABLE_PAC
            )
        }
        return serverRepository.defaultBranch
    }

    companion object {
        const val INIT_VERSION = 1
        private const val PAC_TEMPLATE_INSTANCE_BRANCH_PREFIX = "bk-ci-template-instance-"
    }
}
