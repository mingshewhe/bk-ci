package com.tencent.devops.process.yaml.resource

import com.tencent.devops.common.client.Client
import com.tencent.devops.process.api.service.ServicePipelineYamlViewResource
import com.tencent.devops.process.dao.yaml.PipelineYamlViewDao
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlViewCleanupReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlViewCreateReq
import org.jooq.DSLContext
import org.springframework.stereotype.Service

/**
 * trigger 侧 PAC 流水线组：先查绑定，有待创建目录或已有绑定才调 process。
 */
@Service
class PipelineYamlViewResourceClient(
    private val client: Client,
    private val dslContext: DSLContext,
    private val pipelineYamlViewDao: PipelineYamlViewDao
) {
    fun createYamlViews(
        userId: String,
        projectId: String,
        repoHashId: String,
        aliasName: String,
        directoryList: Set<String>
    ) {
        if (directoryList.isEmpty()) {
            return
        }
        val existedDirectories = pipelineYamlViewDao.listRepoYamlView(
            dslContext = dslContext,
            projectId = projectId,
            repoHashId = repoHashId
        ).map { it.directory }.toSet()
        val addDirectories = directoryList.filterNot { existedDirectories.contains(it) }.toSet()
        if (addDirectories.isEmpty()) {
            return
        }
        client.get(ServicePipelineYamlViewResource::class).createYamlViews(
            userId = userId,
            projectId = projectId,
            request = PipelineYamlViewCreateReq(
                repoHashId = repoHashId,
                aliasName = aliasName,
                directoryList = addDirectories
            )
        )
    }

    fun cleanupYamlView(
        userId: String,
        projectId: String,
        repoHashId: String,
        directory: String,
        pipelineId: String
    ) {
        pipelineYamlViewDao.get(
            dslContext = dslContext,
            projectId = projectId,
            repoHashId = repoHashId,
            directory = directory
        ) ?: return
        client.get(ServicePipelineYamlViewResource::class).cleanupYamlView(
            userId = userId,
            projectId = projectId,
            request = PipelineYamlViewCleanupReq(
                repoHashId = repoHashId,
                directory = directory,
                pipelineId = pipelineId
            )
        )
    }
}
