package com.tencent.devops.process.service.template.v2.version.hander

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceOnlyVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.service.template.v2.PipelineTemplateGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelLock
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.PipelineTemplateTransactionService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionContext
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionHandler
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 创建流水线模版分支版本
 */
@Service
class PipelineTemplateCreateBranchHandler @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateTransactionService: PipelineTemplateTransactionService,
    private val pipelineTemplateGenerator: PipelineTemplateGenerator,
    private val redisOperation: RedisOperation
) : PipelineTemplateVersionHandler {
    override fun support(context: PipelineTemplateVersionContext): Boolean {
        return context.versionAction == PipelineVersionAction.CREATE_BRANCH
    }

    override fun handle(context: PipelineTemplateVersionContext): DeployTemplateResult {
        with(context) {
            val lock = PipelineTemplateModelLock(redisOperation = redisOperation, templateId = templateId)
            try {
                lock.lock()
                return doHandle()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun PipelineTemplateVersionContext.doHandle(): DeployTemplateResult {
        if (!enablePac) {
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        if (yamlFileInfo == null) {
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        if (targetBranch == null) {
            throw ErrorCodeException(
                errorCode = ""
            )
        }
        if (pTemplateResourceWithoutVersion.status != VersionStatus.BRANCH) {
            // TEMPLATE_NOT_RELEASED
            throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
        }
        val templateInfo = pipelineTemplateInfoService.getOrNull(
            projectId = projectId,
            templateId = templateId
        )
        val resourceOnlyVersion = if (templateInfo == null) {
            val defaultTemplateVersion = pipelineTemplateGenerator.getDefaultVersion(
                versionStatus = VersionStatus.BRANCH,
                branchName = targetBranch
            )
            pipelineTemplateTransactionService.createTemplate(
                pipelineTemplateInfo = pipelineTemplateInfo,
                pipelineTemplateResource = PipelineTemplateResource(
                    pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
                    pTemplateResourceOnlyVersion = defaultTemplateVersion
                ),
                pipelineTemplateSetting = pipelineTemplateSetting.copy(
                    version = defaultTemplateVersion.settingVersion
                )
            )
            defaultTemplateVersion
        } else {
            createBranchVersion()
        }

        return DeployTemplateResult(
            version = resourceOnlyVersion.version,
            templateId = templateId,
            templateName = pipelineTemplateInfo.name,
            number = resourceOnlyVersion.number,
            versionNum = resourceOnlyVersion.versionNum,
            versionName = resourceOnlyVersion.versionName
        )
    }

    private fun PipelineTemplateVersionContext.createBranchVersion(): PTemplateResourceOnlyVersion {
        val latestResource = pipelineTemplateResourceService.getLatestVersionResource(
            projectId = projectId,
            templateId = templateId
        ) ?: throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
        val resourceOnlyVersion = pipelineTemplateGenerator.generateBranchVersion(
            latestResource = latestResource,
            branchName = targetBranch!!
        )
        val pipelineTemplateResource = PipelineTemplateResource(
            pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
            pTemplateResourceOnlyVersion = resourceOnlyVersion
        )

        pipelineTemplateTransactionService.createBranchVersion(
            templateResource = pipelineTemplateResource,
            templateSetting = pipelineTemplateSetting.copy(
                version = resourceOnlyVersion.settingVersion
            )
        )
        return resourceOnlyVersion
    }
}
