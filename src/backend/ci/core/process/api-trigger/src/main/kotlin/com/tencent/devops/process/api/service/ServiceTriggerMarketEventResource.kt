package com.tencent.devops.process.api.service

import com.tencent.devops.common.api.annotation.ServiceInterface
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.process.pojo.BuildId
import com.tencent.devops.process.pojo.trigger.GenericEventStartRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.HeaderParam
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Tag(name = "SERVICE_TRIGGER_MARKET_EVENT", description = "服务-Trigger-研发商店事件")
@Path("/service/trigger/market/event")
@ServiceInterface("trigger")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface ServiceTriggerMarketEventResource {

    @Operation(summary = "通过触发器插件启动流水线")
    @POST
    @Path("projects/{projectId}/pipelines/{pipelineId}/{eventCode}/start")
    fun start(
        @Parameter(description = "用户ID", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @Parameter(description = "项目ID", required = true)
        @PathParam("projectId")
        projectId: String,
        @Parameter(description = "流水线ID", required = true)
        @PathParam("pipelineId")
        pipelineId: String,
        @Parameter(description = "事件编码", required = true)
        @PathParam("eventCode")
        eventCode: String,
        @Parameter(description = "启动请求", required = true)
        request: GenericEventStartRequest
    ): Result<BuildId>
}
