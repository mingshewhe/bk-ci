package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.process.pojo.enums.PipelineTemplateSource
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "流水线模板商店导入创建请求体")
data class PipelineTemplateRepositoryCreateReq(
    @get:Schema(title = "项目ID", required = true)
    override val projectId: String,
    @get:Schema(title = "创建人", required = true)
    override val creator: String,
    @get:Schema(title = "来源", required = true)
    override val source: PipelineTemplateSource,
    @get:Schema(title = "类型", required = true)
    override val type: PipelineTemplateType,
    @get:Schema(title = "代码库哈希Id", required = true)
    val repoHashId: String,
    @get:Schema(title = "默认分支", required = true)
    val branch: String,
    @get:Schema(title = "模板文件名称列表", required = true)
    val fileNames: List<String>
) : PipelineTemplateBasicCreateReq(
    projectId = projectId,
    creator = creator,
    source = source,
    type = type
) {
    companion object {
        const val SOURCE = "REPOSITORY"
    }
}
