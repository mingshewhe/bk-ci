package com.tencent.devops.process.api.template.v2

import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID_DEFAULT_VALUE
import com.tencent.devops.common.api.model.SQLPage
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.process.pojo.setting.PipelineVersionSimple
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateBasicCreateReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCompareResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDetailsResponse
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateDraftSaveReq
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoWithPermission
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Tag(name = "USER_PIPELINE_TEMPLATE_V2", description = "用户-流水线-模板")
@Path("/user/pipeline/template/v2/{projectId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserPipelineTemplateV2Resource {
    @Operation(summary = "新建流水线模板")
    @POST
    @Path("/create")
    fun create(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "请求体", required = true)
        request: PipelineTemplateBasicCreateReq
    ): Result<String>

    @Operation(summary = "删除流水线模板")
    @DELETE
    @Path("/{templateId}/delete/")
    fun delete(
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

    @Operation(summary = "保存流水线模板草稿")
    @PUT
    @Path("/{templateId}/saveDraft")
    fun saveDraft(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String,
        @Parameter(description = "请求体", required = true)
        request: PipelineTemplateDraftSaveReq
    ): Result<Boolean>

    @Operation(summary = "获取模板列表")
    @POST
    @Path("/list/")
    fun listTemplateInfos(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "请求体", required = true)
        request: PipelineTemplateCommonCondition
    ): Result<SQLPage<PipelineTemplateInfoWithPermission>>

    @Operation(summary = "查看模板详情")
    @GET
    @Path("/{templateId}/details/")
    fun getTemplateDetails(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String,
        @Parameter(description = "版本", required = false)
        @QueryParam("version")
        version: Long?
    ): Result<PipelineTemplateDetailsResponse>

    @Operation(summary = "查看模板基本信息")
    @GET
    @Path("/{templateId}/info/")
    fun getTemplateInfo(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String
    ): Result<PipelineTemplateInfo>

    @Operation(summary = "查看模板的版本历史")
    @POST
    @Path("/{templateId}/versions/")
    fun getTemplateVersions(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String,
        @Parameter(description = "请求体", required = false)
        request: PipelineTemplateResourceCommonCondition
    ): Result<Page<PipelineVersionSimple>>

    @Operation(summary = "版本对比")
    @GET
    @Path("/{templateId}/compare/")
    fun compare(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "模板ID", required = true)
        @PathParam("templateId")
        templateId: String,
        @Parameter(description = "基准版本", required = false)
        @QueryParam("baseVersion")
        baseVersion: Long?,
        @Parameter(description = "比较版本", required = false)
        @QueryParam("comparedVersion")
        comparedVersion: Long
    ): Result<PipelineTemplateCompareResponse>

    @Operation(summary = "复制")
    @POST
    @Path("/{srcTemplateId}/copy/")
    fun copy(
        @Parameter(description = "用户ID", required = true, example = AUTH_HEADER_USER_ID_DEFAULT_VALUE)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "父模板ID", required = true)
        @PathParam("srcTemplateId")
        srcTemplateId: String,
        @Parameter(description = "是否同步配置", required = false)
        @QueryParam("copySetting")
        copySetting: Boolean
    ): Result<String>
}
