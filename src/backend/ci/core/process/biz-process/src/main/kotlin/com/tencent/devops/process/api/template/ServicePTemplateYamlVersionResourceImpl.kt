package com.tencent.devops.process.api.template

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.pojo.pipeline.DeployPipelineResult
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlBranchActionReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlDeployReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlPullRequestReq
import com.tencent.devops.process.yaml.resource.PTemplateYamlResourceService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ServicePTemplateYamlVersionResourceImpl @Autowired constructor(
    private val pTemplateYamlResourceService: PTemplateYamlResourceService
) : ServicePTemplateYamlVersionResource {
    override fun createYamlVersion(
        userId: String,
        projectId: String,
        request: PipelineYamlDeployReq
    ): Result<DeployPipelineResult> {
        return Result(pTemplateYamlResourceService.createYamlPipeline(userId, projectId, request))
    }

    override fun updateYamlVersion(
        userId: String,
        projectId: String,
        templateId: String,
        request: PipelineYamlDeployReq
    ): Result<DeployPipelineResult> {
        return Result(
            pTemplateYamlResourceService.updateYamlPipeline(userId, projectId, templateId, request)
        )
    }

    override fun updateYamlBranchAction(
        userId: String,
        projectId: String,
        templateId: String,
        request: PipelineYamlBranchActionReq
    ): Result<Boolean> {
        pTemplateYamlResourceService.updateBranchAction(
            userId = userId,
            projectId = projectId,
            pipelineId = templateId,
            request = request
        )
        return Result(true)
    }

    override fun deleteYamlVersion(
        userId: String,
        projectId: String,
        templateId: String
    ): Result<Boolean> {
        pTemplateYamlResourceService.deletePipeline(userId, projectId, templateId)
        return Result(true)
    }

    override fun completeYamlPullRequest(
        userId: String,
        projectId: String,
        templateId: String,
        request: PipelineYamlPullRequestReq
    ): Result<Boolean> {
        pTemplateYamlResourceService.completePullRequest(
            userId = userId,
            projectId = projectId,
            pipelineId = templateId,
            request = request
        )
        return Result(true)
    }

    override fun getYamlVersionName(
        headerProjectId: String,
        projectId: String,
        templateId: String
    ): Result<String?> {
        return Result(pTemplateYamlResourceService.getPipelineName(projectId, templateId))
    }

    override fun existsYamlReleaseVersion(
        headerProjectId: String,
        projectId: String,
        templateId: String
    ): Result<Boolean> {
        return Result(pTemplateYamlResourceService.existsReleaseVersion(projectId, templateId))
    }
}
