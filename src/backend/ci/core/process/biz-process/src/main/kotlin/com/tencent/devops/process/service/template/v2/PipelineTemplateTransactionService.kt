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

import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.process.constant.PipelineTemplateConstant
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoUpdateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceUpdateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingUpdateInfo
import com.tencent.devops.store.api.common.ServiceStoreResource
import com.tencent.devops.store.pojo.common.enums.StoreTypeEnum
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 负责流水线模版持久化业务逻辑
 */
@Service
class PipelineTemplateTransactionService @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val pipelineTemplatePermissionService: PipelineTemplatePermissionService,
    private val dslContext: DSLContext,
    private val pipelineTemplateRelatedService: PipelineTemplateRelatedService,
    private val client: Client
) {

    /**
     * 创建流水线模版和权限,用于新建模版
     */
    fun createTemplate(
        pipelineTemplateInfo: PipelineTemplateInfo,
        pipelineTemplateResource: PipelineTemplateResource,
        pipelineTemplateSetting: PipelineSetting
    ) {
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            pipelineTemplateInfoService.create(
                transactionContext = context,
                pipelineTemplateInfo = pipelineTemplateInfo
            )
            pipelineTemplateResourceService.create(
                transactionContext = context,
                pipelineTemplateResource = pipelineTemplateResource
            )
            pipelineTemplateSettingService.create(
                transactionContext = context,
                pipelineTemplateSetting = pipelineTemplateSetting
            )
            pipelineTemplatePermissionService.createResource(
                userId = pipelineTemplateInfo.creator,
                projectId = pipelineTemplateInfo.projectId,
                templateId = pipelineTemplateInfo.id,
                templateName = pipelineTemplateInfo.name
            )
        }
    }

    /**
     * 创建正式版本
     */
    fun createReleaseVersion(
        userId: String,
        templateResource: PipelineTemplateResource,
        templateSetting: PipelineSetting
    ) {
        val pipelineTemplateInfoUpdateInfo = PipelineTemplateInfoUpdateInfo(
            name = templateSetting.pipelineName,
            desc = templateSetting.desc,
            releasedVersion = templateResource.version,
            releasedVersionName = templateResource.versionName,
            releasedSettingVersion = templateResource.settingVersion,
            latestVersionStatus = VersionStatus.RELEASED,
            updater = userId
        )
        val pipelineTemplateCommonCondition = PipelineTemplateCommonCondition(
            projectId = templateResource.projectId,
            templateId = templateResource.templateId
        )
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            pipelineTemplateInfoService.update(
                transactionContext = context,
                record = pipelineTemplateInfoUpdateInfo,
                commonCondition = pipelineTemplateCommonCondition
            )
            pipelineTemplateResourceService.create(
                transactionContext = context,
                pipelineTemplateResource = templateResource.copy(
                    releaseTime = LocalDateTime.now().timestampmilli()
                )
            )
            pipelineTemplateSettingService.create(
                transactionContext = context,
                pipelineTemplateSetting = templateSetting
            )
            pipelineTemplatePermissionService.createResource(
                userId = userId,
                projectId = templateResource.projectId,
                templateId = templateResource.templateId,
                templateName = templateSetting.pipelineName
            )
        }
    }

    fun createDraftVersion(
        templateResource: PipelineTemplateResource,
        templateSetting: PipelineSetting
    ) {
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            pipelineTemplateResourceService.create(
                transactionContext = transactionContext,
                pipelineTemplateResource = templateResource
            )
            pipelineTemplateSettingService.create(
                transactionContext = transactionContext,
                pipelineTemplateSetting = templateSetting
            )
        }
    }

    fun createBranchVersion(
        templateResource: PipelineTemplateResource,
        templateSetting: PipelineSetting
    ) {
        val inactiveBranchUpdateInfo = PipelineTemplateResourceUpdateInfo(
            branchAction = BranchVersionAction.INACTIVE
        )
        val inactiveBranchCondition = PipelineTemplateResourceCommonCondition(
            projectId = templateResource.projectId,
            templateId = templateResource.templateId,
            versionName = templateResource.versionName,
            branchAction = BranchVersionAction.ACTIVE
        )
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            // 创建分支版本,需要把原来的活跃的分支置为非活跃
            pipelineTemplateResourceService.update(
                transactionContext = transactionContext,
                record = inactiveBranchUpdateInfo,
                commonCondition = inactiveBranchCondition
            )
            pipelineTemplateResourceService.create(
                transactionContext = transactionContext,
                pipelineTemplateResource = templateResource.copy(
                    branchAction = BranchVersionAction.ACTIVE
                )
            )
            pipelineTemplateSettingService.create(
                transactionContext = transactionContext,
                pipelineTemplateSetting = templateSetting
            )
        }
    }

    fun updateDraftVersion(
        userId: String,
        templateResource: PipelineTemplateResource,
        templateSetting: PipelineSetting
    ) {
        val templateResourceUpdateInfo = PipelineTemplateResourceUpdateInfo(
            params = templateResource.params,
            model = templateResource.model,
            yaml = templateResource.yaml,
            updater = userId,
            sortWeight = PipelineTemplateConstant.COMMITTING_STATUS_VERSION_SORT_WIGHT
        )
        val templateResourceCondition = PipelineTemplateResourceCommonCondition(
            projectId = templateResource.projectId,
            templateId = templateResource.templateId,
            version = templateResource.version
        )
        val templateSettingUpdateInfo = PipelineTemplateSettingUpdateInfo(
            userId = userId,
            pipelineSetting = templateSetting
        )
        val templateSettingCondition = PipelineTemplateSettingCommonCondition(
            projectId = templateResource.projectId,
            templateId = templateResource.templateId,
            settingVersion = templateResource.settingVersion
        )
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            pipelineTemplateResourceService.update(
                transactionContext = context,
                record = templateResourceUpdateInfo,
                commonCondition = templateResourceCondition
            )
            pipelineTemplateSettingService.update(
                transactionContext = context,
                record = templateSettingUpdateInfo,
                commonCondition = templateSettingCondition
            )
        }
    }

    /**
     * 将草稿版本发布为正式版本
     */
    fun releaseDraft2ReleaseVersion(
        userId: String,
        templateResource: PipelineTemplateResource,
        templateSetting: PipelineSetting
    ) {
        val pipelineTemplateInfoUpdateInfo = PipelineTemplateInfoUpdateInfo(
            name = templateSetting.pipelineName,
            desc = templateSetting.desc,
            releasedVersion = templateResource.version,
            releasedVersionName = templateResource.versionName,
            releasedSettingVersion = templateResource.settingVersion,
            latestVersionStatus = VersionStatus.RELEASED,
            updater = userId
        )
        val pipelineTemplateCommonCondition = PipelineTemplateCommonCondition(
            projectId = templateResource.projectId,
            templateId = templateResource.templateId
        )
        val templateResourceUpdateInfo = PipelineTemplateResourceUpdateInfo(
            versionName = templateResource.versionName,
            settingVersionNum = templateResource.settingVersionNum,
            versionNum = templateResource.versionNum,
            pipelineVersion = templateResource.pipelineVersion,
            triggerVersion = templateResource.triggerVersion,
            releaseTime = LocalDateTime.now(),
            status = VersionStatus.RELEASED,
            sortWeight = PipelineTemplateConstant.OTHER_STATUS_VERSION_SORT_WIGHT,
            updater = userId
        )
        val templateResourceCondition = PipelineTemplateResourceCommonCondition(
            projectId = templateResource.projectId,
            templateId = templateResource.templateId,
            version = templateResource.version
        )
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            pipelineTemplateInfoService.update(
                transactionContext = context,
                record = pipelineTemplateInfoUpdateInfo,
                commonCondition = pipelineTemplateCommonCondition
            )
            pipelineTemplateResourceService.update(
                transactionContext = context,
                record = templateResourceUpdateInfo,
                commonCondition = templateResourceCondition
            )
            pipelineTemplatePermissionService.createResource(
                userId = userId,
                projectId = templateResource.projectId,
                templateId = templateResource.templateId,
                templateName = templateSetting.pipelineName
            )
        }
    }

    fun releaseDraft2BranchVersion(
        userId: String,
        projectId: String,
        templateId: String,
        version: Long,
        versionName: String,
    ) {
        val inactiveBranchUpdateInfo = PipelineTemplateResourceUpdateInfo(
            branchAction = BranchVersionAction.INACTIVE
        )
        val inactiveBranchCondition = PipelineTemplateResourceCommonCondition(
            projectId = projectId,
            templateId = templateId,
            versionName = versionName,
            branchAction = BranchVersionAction.ACTIVE
        )
        val templateResourceUpdateInfo = PipelineTemplateResourceUpdateInfo(
            versionName = versionName,
            status = VersionStatus.BRANCH,
            updater = userId,
            sortWeight = PipelineTemplateConstant.OTHER_STATUS_VERSION_SORT_WIGHT
        )
        val templateResourceCondition = PipelineTemplateResourceCommonCondition(
            projectId = projectId,
            templateId = templateId,
            version = version
        )
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            // 创建分支版本,需要把原来的活跃的分支置为非活跃
            pipelineTemplateResourceService.update(
                transactionContext = transactionContext,
                record = inactiveBranchUpdateInfo,
                commonCondition = inactiveBranchCondition
            )
            pipelineTemplateResourceService.update(
                transactionContext = transactionContext,
                record = templateResourceUpdateInfo,
                commonCondition = templateResourceCondition
            )
        }
    }

    fun deleteTemplate(
        projectId: String,
        templateId: String
    ) {
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            val templateInfo = pipelineTemplateInfoService.get(
                projectId = projectId,
                templateId = templateId
            )
            pipelineTemplateRelatedService.delete(
                transactionContext = context,
                condition = PipelineTemplateRelatedCommonCondition(
                    projectId = projectId,
                    templateId = templateId
                )
            )
            pipelineTemplateInfoService.delete(
                transactionContext = context,
                commonCondition = PipelineTemplateCommonCondition(
                    projectId = projectId,
                    templateId = templateId
                )
            )
            pipelineTemplateResourceService.delete(
                transactionContext = context,
                commonCondition = PipelineTemplateResourceCommonCondition(
                    projectId = projectId,
                    templateId = templateId
                )
            )
            pipelineTemplateSettingService.delete(
                transactionContext = context,
                commonCondition = PipelineTemplateSettingCommonCondition(
                    projectId = projectId,
                    templateId = templateId
                )
            )
            if (templateInfo.mode == TemplateType.CONSTRAINT.name) {
                client.get(ServiceStoreResource::class).uninstall(
                    storeCode = templateInfo.srcTemplateId!!,
                    storeType = StoreTypeEnum.TEMPLATE,
                    projectCode = templateInfo.projectId
                )
            }
            pipelineTemplatePermissionService.deleteResource(
                projectId = projectId,
                templateId = templateId
            )
        }
    }
}
