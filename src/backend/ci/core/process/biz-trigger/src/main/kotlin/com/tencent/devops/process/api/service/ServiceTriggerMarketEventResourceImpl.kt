package com.tencent.devops.process.api.service

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.pojo.BuildId
import com.tencent.devops.process.pojo.trigger.GenericEventStartRequest
import com.tencent.devops.process.trigger.market.MarketEventTriggerBuildService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ServiceTriggerMarketEventResourceImpl @Autowired constructor(
    private val marketEventTriggerBuildService: MarketEventTriggerBuildService
) : ServiceTriggerMarketEventResource {
    override fun start(
        userId: String,
        projectId: String,
        pipelineId: String,
        eventCode: String,
        request: GenericEventStartRequest
    ): Result<BuildId> {
        return Result(
            marketEventTriggerBuildService.genericEventTrigger(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                eventCode = eventCode,
                request = request
            )
        )
    }
}
