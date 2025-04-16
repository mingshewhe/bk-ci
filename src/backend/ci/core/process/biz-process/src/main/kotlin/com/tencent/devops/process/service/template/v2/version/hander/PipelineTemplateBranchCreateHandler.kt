package com.tencent.devops.process.service.template.v2.version.hander

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.enums.OperationLogType
import com.tencent.devops.process.pojo.pipeline.DeployTemplateResult
import com.tencent.devops.process.pojo.template.v2.PTemplateResourceOnlyVersion
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.service.PipelineOperationLogService
import com.tencent.devops.process.service.template.v2.PipelineTemplateGenerator
import com.tencent.devops.process.service.template.v2.PipelineTemplateInfoService
import com.tencent.devops.process.service.template.v2.PipelineTemplateModelLock
import com.tencent.devops.process.service.template.v2.PipelineTemplateResourceService
import com.tencent.devops.process.service.template.v2.PipelineTemplatePersistenceService
import com.tencent.devops.process.service.template.v2.version.PipelineTemplateVersionCreateContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 创建流水线模版分支版本
 */
@Service
class PipelineTemplateBranchCreateHandler @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplatePersistenceService: PipelineTemplatePersistenceService,
    private val pipelineTemplateGenerator: PipelineTemplateGenerator,
    private val redisOperation: RedisOperation,
    private val operationLogService: PipelineOperationLogService
) : PipelineTemplateVersionCreateHandler {
    override fun support(context: PipelineTemplateVersionCreateContext): Boolean {
        return context.versionAction == PipelineVersionAction.CREATE_BRANCH
    }

    override fun handle(context: PipelineTemplateVersionCreateContext): DeployTemplateResult {
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

    private fun PipelineTemplateVersionCreateContext.doHandle(): DeployTemplateResult {
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
        if (branchName == null) {
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
                branchName = branchName
            )
            pipelineTemplatePersistenceService.createTemplate(
                pipelineTemplateInfo = pipelineTemplateInfo,
                pipelineTemplateResource = PipelineTemplateResource(
                    pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
                    pTemplateResourceOnlyVersion = defaultTemplateVersion
                ).copy(
                    branchAction = BranchVersionAction.ACTIVE
                ),
                pipelineTemplateSetting = pipelineTemplateSetting.copy(
                    version = defaultTemplateVersion.settingVersion
                )
            )
            defaultTemplateVersion
        } else {
            createBranchVersion()
        }
        operationLogService.addOperationLog(
            userId = userId,
            projectId = projectId,
            pipelineId = templateId,
            version = resourceOnlyVersion.version.toInt(),
            operationLogType = OperationLogType.CREATE_BRANCH_VERSION,
            params = resourceOnlyVersion.versionName ?: "",
            description = null
        )
        return DeployTemplateResult(
            version = resourceOnlyVersion.version,
            templateId = templateId,
            templateName = pipelineTemplateInfo.name,
            number = resourceOnlyVersion.number,
            versionNum = resourceOnlyVersion.versionNum,
            versionName = resourceOnlyVersion.versionName
        )
    }

    private fun PipelineTemplateVersionCreateContext.createBranchVersion(): PTemplateResourceOnlyVersion {
        val latestResource = pipelineTemplateResourceService.getLatestVersionResource(
            projectId = projectId,
            templateId = templateId
        ) ?: throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
        val resourceOnlyVersion = pipelineTemplateGenerator.generateBranchVersion(
            latestResource = latestResource,
            branchName = branchName!!
        )
        val pipelineTemplateResource = PipelineTemplateResource(
            pTemplateResourceWithoutVersion = pTemplateResourceWithoutVersion,
            pTemplateResourceOnlyVersion = resourceOnlyVersion
        )

        pipelineTemplatePersistenceService.createBranchVersion(
            templateResource = pipelineTemplateResource,
            templateSetting = pipelineTemplateSetting.copy(
                version = resourceOnlyVersion.settingVersion
            )
        )
        return resourceOnlyVersion
    }
}
