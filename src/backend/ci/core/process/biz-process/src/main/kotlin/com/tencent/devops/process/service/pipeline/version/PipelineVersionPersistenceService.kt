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

import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.auth.api.AuthResourceType
import com.tencent.devops.common.auth.api.pojo.ResourceAuthorizationDTO
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.process.dao.PipelineSettingDao
import com.tencent.devops.process.dao.PipelineSettingVersionDao
import com.tencent.devops.process.engine.dao.PipelineBuildSummaryDao
import com.tencent.devops.process.engine.dao.PipelineInfoDao
import com.tencent.devops.process.engine.dao.PipelineResourceDao
import com.tencent.devops.process.engine.dao.PipelineResourceVersionDao
import com.tencent.devops.process.permission.PipelineAuthorizationService
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.pojo.pipeline.PipelineBasicInfo
import com.tencent.devops.process.pojo.pipeline.PipelineModelData
import com.tencent.devops.process.pojo.pipeline.PipelineResourceVersion
import com.tencent.devops.process.service.pipeline.version.listener.PipelineVersionCreateListener
import com.tencent.devops.project.api.service.ServiceAllocIdResource
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 负责流水线版本持久化业务逻辑
 */
@Service
class PipelineVersionPersistenceService @Autowired constructor(
    private val client: Client,
    private val dslContext: DSLContext,
    private val pipelineInfoDao: PipelineInfoDao,
    private val pipelineResourceDao: PipelineResourceDao,
    private val pipelineSettingDao: PipelineSettingDao,
    private val pipelineResourceVersionDao: PipelineResourceVersionDao,
    private val pipelineSettingVersionDao: PipelineSettingVersionDao,
    private val pipelineBuildSummaryDao: PipelineBuildSummaryDao,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineAuthorizationService: PipelineAuthorizationService,
    private val versionCreateListeners: List<PipelineVersionCreateListener>
) {

    fun createPipeline(
        userId: String,
        pipelineBasicInfo: PipelineBasicInfo,
        pipelineModelData: PipelineModelData,
        pipelineResourceVersion: PipelineResourceVersion,
        pipelineSetting: PipelineSetting
    ) {
        transactionCreatePipeline(
            userId = userId,
            pipelineBasicInfo = pipelineBasicInfo,
            pipelineModelData = pipelineModelData,
            pipelineResourceVersion = pipelineResourceVersion,
            pipelineSetting = pipelineSetting
        )
        versionCreateListeners.forEach {
            it.onCreate(
                userId = userId,
                pipelineBasicInfo = pipelineBasicInfo,
                pipelineModelData = pipelineModelData,
                pipelineResourceVersion = pipelineResourceVersion,
                pipelineSetting = pipelineSetting
            )
        }
    }

    private fun transactionCreatePipeline(
        userId: String,
        pipelineBasicInfo: PipelineBasicInfo,
        pipelineModelData: PipelineModelData,
        pipelineResourceVersion: PipelineResourceVersion,
        pipelineSetting: PipelineSetting
    ) {
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            createPipelineInfo(
                transactionContext = transactionContext,
                userId = userId,
                pipelineBasicInfo = pipelineBasicInfo,
                pipelineModelData = pipelineModelData,
                version = pipelineResourceVersion.version,
                latestVersionStatus = pipelineResourceVersion.status,
            )
            createPipelineResource(
                transactionContext = transactionContext,
                userId = userId,
                pipelineResourceVersion = pipelineResourceVersion
            )
            pipelineSettingDao.saveSetting(
                dslContext = transactionContext,
                setting = pipelineSetting
            )
            createPipelineResourceVersion(
                transactionContext = transactionContext,
                userId = userId,
                pipelineResourceVersion = pipelineResourceVersion
            )
            createPipelineSettingVersion(
                transactionContext = transactionContext,
                pipelineSetting = pipelineSetting
            )

            with(pipelineBasicInfo) {
                pipelineBuildSummaryDao.create(
                    dslContext = dslContext,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    buildNo = pipelineModelData.buildNo
                )
                pipelinePermissionService.createResource(
                    userId = userId,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    pipelineName = pipelineName
                )
                pipelineAuthorizationService.addResourceAuthorization(
                    projectId = projectId,
                    resourceAuthorizationList = listOf(
                        ResourceAuthorizationDTO(
                            projectCode = projectId,
                            resourceType = AuthResourceType.PIPELINE_DEFAULT.value,
                            resourceCode = pipelineId,
                            resourceName = pipelineName,
                            handoverFrom = userId,
                            handoverTime = LocalDateTime.now().timestampmilli()
                        )
                    )
                )
            }
        }
    }

    fun createReleaseVersion(
        userId: String,
        pipelineBasicInfo: PipelineBasicInfo,
        pipelineModelData: PipelineModelData,
        pipelineResourceVersion: PipelineResourceVersion,
        pipelineSetting: PipelineSetting
    ) {
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            updatePipelineInfo(
                transactionContext = transactionContext,
                userId = userId,
                pipelineBasicInfo = pipelineBasicInfo,
                pipelineModelData = pipelineModelData,
                version = pipelineResourceVersion.version,
                latestVersionStatus = pipelineResourceVersion.status,
            )
            updatePipelineResource(
                transactionContext = transactionContext,
                pipelineResourceVersion = pipelineResourceVersion
            )
            createPipelineResourceVersion(
                transactionContext = transactionContext,
                userId = userId,
                pipelineResourceVersion = pipelineResourceVersion
            )
            createPipelineSettingVersion(
                transactionContext = transactionContext,
                pipelineSetting = pipelineSetting
            )
            with(pipelineBasicInfo) {
                pipelinePermissionService.modifyResource(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    pipelineName = pipelineName
                )
            }
        }
        versionCreateListeners.forEach {
            it.onUpdate(
                userId = userId,
                pipelineBasicInfo = pipelineBasicInfo,
                pipelineModelData = pipelineModelData,
                pipelineResourceVersion = pipelineResourceVersion,
                pipelineSetting = pipelineSetting
            )
        }
    }

    fun createDraftVersion(
        userId: String,
        pipelineResourceVersion: PipelineResourceVersion,
        pipelineSetting: PipelineSetting
    ) {
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            createPipelineResourceVersion(
                transactionContext = transactionContext,
                userId = userId,
                pipelineResourceVersion = pipelineResourceVersion
            )
            createPipelineSettingVersion(
                transactionContext = transactionContext,
                pipelineSetting = pipelineSetting
            )
        }
    }

    fun updateDraftVersion(
        userId: String,
        pipelineResourceVersion: PipelineResourceVersion,
        pipelineSetting: PipelineSetting,
        settingId: Long
    ) {
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            createPipelineResourceVersion(
                transactionContext = transactionContext,
                userId = userId,
                pipelineResourceVersion = pipelineResourceVersion
            )
            pipelineSettingVersionDao.saveSetting(
                dslContext = transactionContext,
                setting = pipelineSetting,
                version = pipelineSetting.version,
                id = settingId
            )
        }
    }

    fun createBranchVersion(
        userId: String,
        pipelineResourceVersion: PipelineResourceVersion,
        pipelineSetting: PipelineSetting
    ) {
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            // 分支版本需要将同分支版本置为无效
            pipelineResourceVersionDao.updateBranchVersion(
                dslContext = transactionContext,
                userId = userId,
                projectId = pipelineResourceVersion.projectId,
                pipelineId = pipelineResourceVersion.pipelineId,
                branchName = pipelineResourceVersion.versionName,
                branchVersionAction = BranchVersionAction.INACTIVE
            )
            createPipelineResourceVersion(
                transactionContext = transactionContext,
                userId = userId,
                pipelineResourceVersion = pipelineResourceVersion
            )
            createPipelineSettingVersion(
                transactionContext = transactionContext,
                pipelineSetting = pipelineSetting
            )
        }
    }

    private fun createPipelineInfo(
        transactionContext: DSLContext,
        userId: String,
        pipelineBasicInfo: PipelineBasicInfo,
        pipelineModelData: PipelineModelData,
        version: Int,
        latestVersionStatus: VersionStatus?,
    ) {
        with(pipelineBasicInfo) {
            pipelineInfoDao.create(
                dslContext = transactionContext,
                pipelineId = pipelineId,
                projectId = projectId,
                version = version,
                pipelineName = pipelineName,
                pipelineDesc = pipelineDesc,
                userId = userId,
                channelCode = channelCode,
                manualStartup = pipelineModelData.canManualStartup,
                canElementSkip = pipelineModelData.canElementSkip,
                taskCount = pipelineModelData.taskCount,
                id = id,
                latestVersionStatus = latestVersionStatus,
                pipelineDisable = pipelineDisable
            )
        }
    }

    private fun updatePipelineInfo(
        transactionContext: DSLContext,
        userId: String,
        pipelineBasicInfo: PipelineBasicInfo,
        pipelineModelData: PipelineModelData,
        version: Int,
        latestVersionStatus: VersionStatus?
    ) {
        with(pipelineBasicInfo) {
            pipelineInfoDao.update(
                dslContext = transactionContext,
                projectId = projectId,
                pipelineId = pipelineId,
                userId = userId,
                version = version,
                pipelineName = null,
                pipelineDesc = null,
                manualStartup = pipelineModelData.canManualStartup,
                canElementSkip = pipelineModelData.canElementSkip,
                taskCount = pipelineModelData.taskCount,
                latestVersion = version,
                latestVersionStatus = latestVersionStatus,
                locked = pipelineDisable
            )
        }
    }

    private fun createPipelineResource(
        transactionContext: DSLContext,
        userId: String,
        pipelineResourceVersion: PipelineResourceVersion
    ) {
        with(pipelineResourceVersion) {
            pipelineResourceDao.create(
                dslContext = transactionContext,
                projectId = projectId,
                pipelineId = pipelineId,
                creator = userId,
                version = version,
                versionName = versionName,
                model = model,
                yamlStr = yaml,
                yamlVersion = yamlVersion,
                versionNum = versionNum,
                pipelineVersion = pipelineVersion,
                triggerVersion = triggerVersion,
                settingVersion = settingVersion
            )
        }
    }

    private fun updatePipelineResource(
        transactionContext: DSLContext,
        pipelineResourceVersion: PipelineResourceVersion
    ) {
        with(pipelineResourceVersion) {
            pipelineResourceDao.updateReleaseVersion(
                dslContext = transactionContext,
                projectId = projectId,
                pipelineId = pipelineId,
                version = version,
                model = model,
                yamlStr = yaml,
                yamlVersion = yamlVersion,
                versionName = versionName,
                versionNum = versionNum,
                pipelineVersion = pipelineVersion,
                triggerVersion = triggerVersion,
                settingVersion = settingVersion
            )
        }
    }

    private fun createPipelineResourceVersion(
        transactionContext: DSLContext,
        userId: String,
        pipelineResourceVersion: PipelineResourceVersion
    ) {
        with(pipelineResourceVersion) {
            pipelineResourceVersionDao.create(
                dslContext = transactionContext,
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                version = version,
                versionName = versionName ?: "",
                model = model,
                baseVersion = baseVersion,
                yamlStr = yaml,
                yamlVersion = yamlVersion,
                versionNum = versionNum,
                pipelineVersion = pipelineVersion,
                triggerVersion = triggerVersion,
                settingVersion = settingVersion,
                versionStatus = status,
                branchAction = branchAction,
                description = description
            )
        }
    }

    private fun createPipelineSettingVersion(
        transactionContext: DSLContext,
        pipelineSetting: PipelineSetting
    ) {
        val id = client.get(ServiceAllocIdResource::class).generateSegmentId(
            PIPELINE_SETTING_VERSION_BIZ_TAG_NAME
        ).data
        pipelineSettingVersionDao.saveSetting(
            dslContext = transactionContext,
            setting = pipelineSetting,
            version = pipelineSetting.version,
            id = id
        )
    }

    companion object {
        private const val PIPELINE_SETTING_VERSION_BIZ_TAG_NAME = "PIPELINE_SETTING_VERSION"
    }
}
