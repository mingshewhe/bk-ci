package com.tencent.devops.process.api.service

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.pojo.pipeline.DeployPipelineResult
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlBranchActionReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlDeployReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlPullRequestReq
import com.tencent.devops.process.yaml.resource.PipelineYamlResourceService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ServicePipelineYamlVersionResourceImpl @Autowired constructor(
    private val pipelineYamlResourceService: PipelineYamlResourceService
) : ServicePipelineYamlVersionResource {
    override fun createYamlVersion(
        userId: String,
        projectId: String,
        request: PipelineYamlDeployReq
    ): Result<DeployPipelineResult> {
        return Result(pipelineYamlResourceService.createYamlPipeline(userId, projectId, request))
    }

    override fun updateYamlVersion(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlDeployReq
    ): Result<DeployPipelineResult> {
        return Result(
            pipelineYamlResourceService.updateYamlPipeline(userId, projectId, pipelineId, request)
        )
    }

    override fun updateYamlBranchAction(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlBranchActionReq
    ): Result<Boolean> {
        pipelineYamlResourceService.updateBranchAction(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = request
        )
        return Result(true)
    }

    override fun deleteYamlVersion(
        userId: String,
        projectId: String,
        pipelineId: String
    ): Result<Boolean> {
        pipelineYamlResourceService.deletePipeline(userId, projectId, pipelineId)
        return Result(true)
    }

    override fun completeYamlPullRequest(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlPullRequestReq
    ): Result<Boolean> {
        pipelineYamlResourceService.completePullRequest(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = request
        )
        return Result(true)
    }

    override fun getYamlVersionName(
        headerProjectId: String,
        projectId: String,
        pipelineId: String
    ): Result<String?> {
        return Result(pipelineYamlResourceService.getPipelineName(projectId, pipelineId))
    }

    override fun existsYamlReleaseVersion(
        headerProjectId: String,
        projectId: String,
        pipelineId: String
    ): Result<Boolean> {
        return Result(pipelineYamlResourceService.existsReleaseVersion(projectId, pipelineId))
    }
}
