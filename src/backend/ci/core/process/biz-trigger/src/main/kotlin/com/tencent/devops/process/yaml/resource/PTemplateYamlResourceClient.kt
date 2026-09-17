package com.tencent.devops.process.yaml.resource

import com.tencent.devops.common.client.Client
import com.tencent.devops.process.api.template.ServicePTemplateYamlVersionResource
import com.tencent.devops.process.pojo.pipeline.DeployPipelineResult
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlBranchActionReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlDeployReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlPullRequestReq
import org.springframework.stereotype.Service

@Service
class PTemplateYamlResourceClient(
    private val client: Client
) : IPipelineYamlResourceService {
    override fun createYamlPipeline(
        userId: String,
        projectId: String,
        request: PipelineYamlDeployReq
    ): DeployPipelineResult {
        return client.get(ServicePTemplateYamlVersionResource::class).createYamlVersion(
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
        return client.get(ServicePTemplateYamlVersionResource::class).updateYamlVersion(
            userId = userId,
            projectId = projectId,
            templateId = pipelineId,
            request = request
        ).data!!
    }

    override fun updateBranchAction(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlBranchActionReq
    ) {
        client.get(ServicePTemplateYamlVersionResource::class).updateYamlBranchAction(
            userId = userId,
            projectId = projectId,
            templateId = pipelineId,
            request = request
        )
    }

    override fun deletePipeline(userId: String, projectId: String, pipelineId: String) {
        client.get(ServicePTemplateYamlVersionResource::class).deleteYamlVersion(
            userId = userId,
            projectId = projectId,
            templateId = pipelineId
        )
    }

    override fun getPipelineName(projectId: String, pipelineId: String): String? {
        return client.get(ServicePTemplateYamlVersionResource::class).getYamlVersionName(
            headerProjectId = projectId,
            projectId = projectId,
            templateId = pipelineId
        ).data
    }

    override fun existsReleaseVersion(projectId: String, pipelineId: String): Boolean {
        return client.get(ServicePTemplateYamlVersionResource::class).existsYamlReleaseVersion(
            headerProjectId = projectId,
            projectId = projectId,
            templateId = pipelineId
        ).data ?: false
    }

    override fun completePullRequest(
        userId: String,
        projectId: String,
        pipelineId: String,
        request: PipelineYamlPullRequestReq
    ) {
        client.get(ServicePTemplateYamlVersionResource::class).completeYamlPullRequest(
            userId = userId,
            projectId = projectId,
            templateId = pipelineId,
            request = request
        )
    }
}
