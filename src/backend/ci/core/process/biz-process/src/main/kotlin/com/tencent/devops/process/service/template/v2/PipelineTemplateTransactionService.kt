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

import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplatePermission
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

    fun createTemplateAndPermission(
        pipelineTemplateInfo: PipelineTemplateInfo? = null,
        pipelineTemplateResource: PipelineTemplateResource? = null,
        pipelineTemplateSetting: PipelineSetting? = null,
        pipelineTemplatePermission: PipelineTemplatePermission? = null
    ) {
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            pipelineTemplateInfo?.let {
                pipelineTemplateInfoService.create(
                    transactionContext = context,
                    pipelineTemplateInfo = pipelineTemplateInfo
                )
            }
            pipelineTemplateResource?.let {
                pipelineTemplateResourceService.create(
                    transactionContext = context,
                    pipelineTemplateResource = pipelineTemplateResource
                )
            }
            pipelineTemplateSetting?.let {
                pipelineTemplateSettingService.create(
                    transactionContext = context,
                    pipelineTemplateSetting = pipelineTemplateSetting
                )
            }
            pipelineTemplatePermission?.let {
                pipelineTemplatePermissionService.createResource(
                    userId = pipelineTemplatePermission.creator,
                    projectId = pipelineTemplatePermission.projectId,
                    templateId = pipelineTemplatePermission.id,
                    templateName = pipelineTemplatePermission.name
                )
            }
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

    fun updateDraftVersion(
        templateResourceUpdateInfo: PipelineTemplateResourceUpdateInfo,
        templateResourceCondition: PipelineTemplateResourceCommonCondition,
        templateSettingUpdateInfo: PipelineTemplateSettingUpdateInfo,
        templateSettingCondition: PipelineTemplateSettingCommonCondition
    ) {
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
                pipelineTemplateResource = templateResource
            )
            pipelineTemplateSettingService.create(
                transactionContext = transactionContext,
                pipelineTemplateSetting = templateSetting
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
