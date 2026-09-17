package com.tencent.devops.process.yaml.resource

import com.tencent.devops.common.client.Client
import com.tencent.devops.process.api.service.ServicePipelineYamlVersionResource
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.pojo.pipeline.DeployPipelineResult
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlBranchActionReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlDeployReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlPullRequestReq
import org.springframework.stereotype.Service

@Service
class PipelineYamlResourceClient(
    private val client: Client,
    private val pipelineRepositoryService: PipelineRepositoryService
) : IPipelineYamlResourceService {
    override fun createYamlPipeline(
        userId: String,
        projectId: String,
        request: PipelineYamlDeployReq
    ): DeployPipelineResult {
        return client.get(ServicePipelineYamlVersionResource::class).createYamlVersion(
            userId = userId,
            projectId = projectId,
            request = request
        ).data!!
    }

    override fun updateYamlPipeline(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlDeployReq
    ): DeployPipelineResult {
        return client.get(ServicePipelineYamlVersionResource::class).updateYamlVersion(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = request
        ).data!!
    }

    override fun updateBranchAction(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlBranchActionReq
    ) {
        client.get(ServicePipelineYamlVersionResource::class).updateYamlBranchAction(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = request
        )
    }

    override fun deletePipeline(userId: String, projectId: String, pipelineId: String) {
        client.get(ServicePipelineYamlVersionResource::class).deleteYamlVersion(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId
        )
    }

    override fun getPipelineName(projectId: String, pipelineId: String): String? {
        return pipelineRepositoryService.getPipelineInfo(
            projectId = projectId,
            pipelineId = pipelineId
        )?.pipelineName
    }

    override fun existsReleaseVersion(projectId: String, pipelineId: String): Boolean {
        return pipelineRepositoryService.getReleaseVersionRecord(
            projectId = projectId, pipelineId = pipelineId
        ) != null
    }

    override fun completePullRequest(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlPullRequestReq
    ) {
        client.get(ServicePipelineYamlVersionResource::class).completeYamlPullRequest(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            request = request
        )
    }
}
