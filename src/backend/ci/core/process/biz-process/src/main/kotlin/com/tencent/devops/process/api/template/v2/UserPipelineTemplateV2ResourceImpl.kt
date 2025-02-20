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

package com.tencent.devops.process.api.template.v2

import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateBasicCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCompareResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDetailsResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftSaveReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoWithPermission
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateVersionInfo
import com.tencent.devops.process.service.template.v2.PipelineTemplateFacadeService
import org.slf4j.LoggerFactory

@RestResource
class UserPipelineTemplateV2ResourceImpl(
    private val permissionService: PipelineTemplatePermissionService,
    private val templateFacadeService: PipelineTemplateFacadeService
) : UserPipelineTemplateV2Resource {
    override fun create(
        userId: String,
        projectId: String,
        request: PipelineTemplateBasicCreateReq
    ): Result<String> {
        logger.info("create template {}|{}|{}", userId, projectId, request)
        permissionService.checkPipelineTemplatePermissionWithMessage(
            userId = userId,
            projectId = projectId,
            permission = AuthPermission.CREATE
        )
        return Result(templateFacadeService.createTemplate(request))
    }

    override fun delete(
        userId: String,
        projectId: String,
        templateId: String
    ): Result<Boolean> {
        logger.info("delete template {}|{}|{}", userId, projectId, templateId)
        permissionService.checkPipelineTemplatePermissionWithMessage(
            userId = userId,
            projectId = projectId,
            permission = AuthPermission.DELETE,
            templateId = templateId
        )
        return Result(templateFacadeService.deleteTemplate(projectId = projectId, templateId = templateId))
    }

    override fun saveDraft(
        userId: String,
        projectId: String,
        templateId: String,
        request: PipelineTemplateDraftSaveReq
    ): Result<Boolean> {
        logger.info("save template draft {}|{}|{}|{}", userId, projectId, templateId, request)
        permissionService.checkPipelineTemplatePermissionWithMessage(
            userId = userId,
            projectId = projectId,
            permission = AuthPermission.EDIT,
            templateId = templateId
        )
        return Result(templateFacadeService.saveDraft(userId, request))
    }

    override fun listTemplateInfos(
        userId: String,
        projectId: String,
        request: PipelineTemplateCommonCondition
    ): Result<SQLPage<PipelineTemplateInfoWithPermission>> {
        logger.info("list template infos {}|{}|{}", userId, projectId, request)
        return Result(templateFacadeService.listTemplateInfos(userId, request))
    }

    override fun getTemplateDetails(
        userId: String,
        projectId: String,
        templateId: String,
        version: Long?
    ): Result<PipelineTemplateDetailsResponse> {
        logger.info("get template details {}|{}|{}|{}", userId, projectId, templateId, version)
        permissionService.checkPipelineTemplatePermissionWithMessage(
            userId = userId,
            projectId = projectId,
            permission = AuthPermission.VIEW,
            templateId = templateId
        )
        return Result(
            templateFacadeService.getTemplateDetails(
                projectId = projectId,
                templateId = templateId,
                version = version
            )
        )
    }

    override fun getTemplateVersions(
        userId: String,
        projectId: String,
        templateId: String,
        request: PipelineTemplateResourceCommonCondition
    ): Result<List<PipelineTemplateVersionInfo>> {
        logger.info("get template versions {}|{}|{}|{}", userId, projectId, templateId, request)
        permissionService.checkPipelineTemplatePermissionWithMessage(
            userId = userId,
            projectId = projectId,
            permission = AuthPermission.VIEW,
            templateId = templateId
        )
        return Result(templateFacadeService.getTemplateVersions(request))
    }

    override fun compare(
        userId: String,
        projectId: String,
        templateId: String,
        baseVersion: Long?,
        comparedVersion: Long
    ): Result<PipelineTemplateCompareResponse> {
        logger.info("compare template {}|{}|{}|{}|{}", userId, projectId, templateId, baseVersion, comparedVersion)
        permissionService.checkPipelineTemplatePermissionWithMessage(
            userId = userId,
            projectId = projectId,
            permission = AuthPermission.VIEW,
            templateId = templateId
        )
        return Result(
            templateFacadeService.compare(
                projectId = projectId,
                templateId = templateId,
                baseVersion = baseVersion,
                comparedVersion = comparedVersion
            )
        )
    }

    override fun copy(
        userId: String,
        projectId: String,
        srcTemplateId: String,
        copySetting: Boolean
    ): Result<String> {
        logger.info("copy template {}|{}|{}|{}", userId, projectId, srcTemplateId, copySetting)
        permissionService.checkPipelineTemplatePermissionWithMessage(
            userId = userId,
            projectId = projectId,
            permission = AuthPermission.EDIT,
            templateId = srcTemplateId
        )
        return Result(
            templateFacadeService.copy(
                userId = userId,
                projectId = projectId,
                srcTemplateId = srcTemplateId,
                copySetting = copySetting
            )
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserPipelineTemplateV2ResourceImpl::class.java)
    }
}
