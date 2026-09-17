package com.tencent.devops.process.api.template

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.process.pojo.pipeline.DeployPipelineResult
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlBranchActionReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlDeployReq
import com.tencent.devops.process.pojo.pipeline.yaml.PipelineYamlPullRequestReq
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Tag(name = "SERVICE_PAC_TEMPLATE_VERSION", description = "服务-PAC模板版本")
@Path("/service/templates/yaml/version")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ServicePTemplateYamlVersionResource {

    @Operation(summary = "PAC webhook 创建模板")
    @POST
    @Path("/projects/{projectId}/create")
    fun createYamlVersion(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "部署请求", required = true)
        request: PipelineYamlDeployReq
    ): Result<DeployPipelineResult>

    @Operation(summary = "PAC webhook 更新模板")
    @POST
    @Path("/projects/{projectId}/templates/{templateId}/update")
    fun updateYamlVersion(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String,
        @Parameter(description = "部署请求", required = true)
        request: PipelineYamlDeployReq
    ): Result<DeployPipelineResult>

    @Operation(summary = "PAC 更新模板分支版本有效性")
    @POST
    @Path("/projects/{projectId}/templates/{templateId}/branchAction")
    fun updateYamlBranchAction(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String,
        @Parameter(description = "分支动作请求", required = true)
        request: PipelineYamlBranchActionReq
    ): Result<Boolean>

    @Operation(summary = "PAC 删除模板")
    @POST
    @Path("/projects/{projectId}/templates/{templateId}/delete")
    fun deleteYamlVersion(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String
    ): Result<Boolean>

    @Operation(summary = "PAC 模板合并请求完成")
    @POST
    @Path("/projects/{projectId}/templates/{templateId}/completePullRequest")
    fun completeYamlPullRequest(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String,
        @Parameter(description = "合并请求完成请求", required = true)
        request: PipelineYamlPullRequestReq
    ): Result<Boolean>

    @Operation(summary = "查询 PAC 模板名称")
    @GET
    @Path("/projects/{projectId}/templates/{templateId}/name")
    fun getYamlVersionName(
        @Parameter(description = "项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        headerProjectId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String
    ): Result<String?>

    @Operation(summary = "判断 PAC 模板是否已有正式版本")
    @GET
    @Path("/projects/{projectId}/templates/{templateId}/existsReleaseVersion")
    fun existsYamlReleaseVersion(
        @Parameter(description = "项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        headerProjectId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String
    ): Result<Boolean>
}
