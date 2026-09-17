package com.tencent.devops.process.api.service

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlViewCleanupReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlViewCreateReq
import com.tencent.devops.process.yaml.PipelineYamlViewService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ServicePipelineYamlViewResourceImpl @Autowired constructor(
    private val pipelineYamlViewService: PipelineYamlViewService
) : ServicePipelineYamlViewResource {
    override fun createYamlViews(
        userId: String,
        projectId: String,
        request: PipelineYamlViewCreateReq
    ): Result<Boolean> {
        pipelineYamlViewService.createYamlViews(
            userId = userId,
            projectId = projectId,
            repoHashId = request.repoHashId,
            aliasName = request.aliasName,
            directoryList = request.directoryList
        )
        return Result(true)
    }

    override fun cleanupYamlView(
        userId: String,
        projectId: String,
        request: PipelineYamlViewCleanupReq
    ): Result<Boolean> {
        pipelineYamlViewService.cleanupYamlView(
            userId = userId,
            projectId = projectId,
            repoHashId = request.repoHashId,
            directory = request.directory,
            pipelineId = request.pipelineId
        )
        return Result(true)
    }
}
