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

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.enums.CodeTargetAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.dao.PipelineSettingVersionDao
import com.tencent.devops.process.engine.dao.PipelineResourceDao
import com.tencent.devops.process.engine.dao.PipelineResourceVersionDao
import com.tencent.devops.process.pojo.pipeline.PipelineResourceOnlyVersion
import com.tencent.devops.process.pojo.pipeline.PipelineResourceVersion
import com.tencent.devops.process.pojo.pipeline.PipelineResourceWithoutVersion
import com.tencent.devops.process.pojo.setting.PipelineSettingVersion
import com.tencent.devops.process.utils.PipelineVersionUtils
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
     */
    fun generateReleaseVersion(
        projectId: String,
        pipelineId: String,
        draftResource: PipelineResourceVersion? = null,
        draftSetting: PipelineSettingVersion? = null,
        newResource: PipelineResourceWithoutVersion,
        newSetting: PipelineSettingVersion
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
        val latestReleaseSetting = latestReleaseResource?.let {
            pipelineSettingVersionDao.getSettingVersion(
                dslContext = dslContext,
                projectId = projectId,
                pipelineId = pipelineId,
                version = latestReleaseResource.settingVersion ?: latestReleaseResource.version
            )
        }
        val (version, settingVersion) = if (draftResource == null) {
            Pair(latestResource.version + 1, latestSetting.version + 1)
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
                newModel = newResource.model
            )
            val triggerVersion = PipelineVersionUtils.getTriggerVersion(
                currVersion = latestReleaseResource.triggerVersion ?: 0,
                originModel = latestReleaseResource.model,
                newModel = newResource.model
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
            return when (targetAction) {
                CodeTargetAction.COMMIT_TO_MASTER -> {
                    Pair(VersionStatus.RELEASED, null)
                }

                CodeTargetAction.CHECKOUT_BRANCH_AND_REQUEST_MERGE,
                CodeTargetAction.COMMIT_TO_SOURCE_BRANCH -> {
                    val branchName = "$PAC_TEMPLATE_INSTANCE_BRANCH_PREFIX$templateId-$templateVersion"
                    Pair(VersionStatus.BRANCH, branchName)
                }

                // TODO 需要判断是否为默认分支
                CodeTargetAction.COMMIT_TO_BRANCH -> {
                    Pair(VersionStatus.BRANCH, targetBranch)
                }

                else -> {
                    Pair(VersionStatus.RELEASED, null)
                }
            }
        } else {
            Pair(VersionStatus.RELEASED, null)
        }
    }

    /**
     * 生成模版实例化版本
     */
    fun generateInstanceVersion(
        projectId: String,
        pipelineId: String,
        newResource: PipelineResourceWithoutVersion,
        newSetting: PipelineSettingVersion,
        enablePac: Boolean,
        repoHashId: String?,
        targetAction: CodeTargetAction?,
        targetBranch: String? = null,
        templateId: String,
        templateVersion: Long
    ): PipelineResourceOnlyVersion {
        return if (enablePac) {
            generateInstanceVersionWithPac(
                projectId = projectId,
                pipelineId = pipelineId,
                newResource = newResource,
                newSetting = newSetting,
                targetAction = targetAction,
                targetBranch = targetBranch,
                templateId = templateId,
                templateVersion = templateVersion
            )
        } else {
            val resourceOnlyVersion = generateReleaseVersion(
                projectId = projectId,
                pipelineId = pipelineId,
                newResource = newResource,
                newSetting = newSetting,
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
        newResource: PipelineResourceWithoutVersion,
        newSetting: PipelineSettingVersion,
        targetAction: CodeTargetAction?,
        targetBranch: String? = null,
        templateId: String,
        templateVersion: Long
    ): PipelineResourceOnlyVersion {
        return when (targetAction) {
            CodeTargetAction.COMMIT_TO_MASTER -> {
                generateReleaseVersion(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    newResource = newResource,
                    newSetting = newSetting,
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
                // TODO 需要判断是否为默认分支
                generateBranchVersion(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    branchName = targetBranch
                )
            }

            else -> {
                throw IllegalArgumentException("targetAction is illegal")
            }
        }
    }

    companion object {
        const val INIT_VERSION = 1
        private const val PAC_TEMPLATE_INSTANCE_BRANCH_PREFIX = "bk-ci-template-instance-"
    }
}
