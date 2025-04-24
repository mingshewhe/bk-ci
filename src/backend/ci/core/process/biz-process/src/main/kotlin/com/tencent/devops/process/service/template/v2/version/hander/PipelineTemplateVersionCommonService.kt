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

package com.tencent.devops.process.service.template.v2.version.hander

import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceOnlyVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.service.template.v2.PipelineTemplateGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplatePersistenceService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionCreateContext
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 流水线模版版本公共逻辑
 */
@Service
class PipelineTemplateVersionCommonService constructor(
    private val pipelineTemplatePersistenceService: PipelineTemplatePersistenceService,
    private val pipelineTemplateGenerator: PipelineTemplateGenerator
) {

    fun initializeTemplate(
        context: PipelineTemplateVersionCreateContext
    ): PTemplateResourceOnlyVersion {
        with(context) {
            val versionStatus = pTemplateResourceWithoutVersion.status
            val defaultTemplateVersion = pipelineTemplateGenerator.getDefaultVersion(
                versionStatus = versionStatus,
                branchName = branchName,
                versionName = fixVersionName
            )

            val branchAction = versionStatus.takeIf {
                it == VersionStatus.BRANCH
            }?.let { BranchVersionAction.ACTIVE }
            val releaseTime = versionStatus.takeIf {
                it == VersionStatus.RELEASED
            }?.let { LocalDateTime.now().timestampmilli() }

            pipelineTemplatePersistenceService.initializeTemplate(
                pipelineTemplateInfo =  pipelineTemplateInfo.copy(
                    releasedVersion = defaultTemplateVersion.version,
                    releasedVersionName = defaultTemplateVersion.versionName,
                    releasedSettingVersion = defaultTemplateVersion.settingVersion,
                    latestVersionStatus = versionStatus
                ),
                pipelineTemplateResource = PipelineTemplateResource(
                    pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
                    pTemplateResourceOnlyVersion = defaultTemplateVersion
                ).copy(
                    branchAction = branchAction,
                    releaseTime = releaseTime
                ),
                pipelineTemplateSetting = pipelineTemplateSetting.copy(
                    version = defaultTemplateVersion.settingVersion
                )
            )
            return defaultTemplateVersion
        }
    }
}
