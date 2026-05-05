package com.tencent.devops.process.service.pipeline.version.convert

import com.fasterxml.jackson.core.JsonParseException
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.exception.PipelineAlreadyExistException
import com.tencent.devops.common.pipeline.enums.PipelineVersionAction
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.extend.ModelCheckPlugin
import com.tencent.devops.common.pipeline.pojo.PipelineModelAndSetting
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ILLEGAL_PIPELINE_MODEL_JSON
import com.tencent.devops.process.engine.cfg.PipelineIdGenerator
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.pojo.pipeline.version.PipelineCopyCreateReq
import com.tencent.devops.process.pojo.pipeline.version.PipelineVersionCreateReq
import com.tencent.devops.process.service.pipeline.PipelineSettingFacadeService
import com.tencent.devops.process.service.pipeline.version.PipelineVersionCreateContext
import com.tencent.devops.process.service.pipeline.version.PipelineVersionGenerator
import jakarta.ws.rs.core.Response
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineCopyCreateReqConverter @Autowired constructor(
    private val pipelineIdGenerator: PipelineIdGenerator,
    private val pipelineVersionGenerator: PipelineVersionGenerator,
    private val pipelineVersionCreateContextFactory: PipelineVersionCreateContextFactory,
    private val pipelineRepositoryService: PipelineRepositoryService,
    private val pipelineSettingFacadeService: PipelineSettingFacadeService,
    private val modelCheckPlugin: ModelCheckPlugin
) : PipelineVersionCreateReqConverter {
    override fun support(request: PipelineVersionCreateReq): Boolean {
        return request is PipelineCopyCreateReq
    }

    override fun convert(
        userId: String,
        projectId: String,
        pipelineId: String?,
        version: Int?,
        request: PipelineVersionCreateReq
    ): PipelineVersionCreateContext {
        request as PipelineCopyCreateReq
        return try {
            val sourcePipelineId = request.pipelineId
            val sourcePipeline = pipelineRepositoryService.getPipelineInfo(projectId, sourcePipelineId)
                ?: throw ErrorCodeException(
                    statusCode = Response.Status.NOT_FOUND.statusCode,
                    errorCode = ProcessMessageCode.ERROR_PIPELINE_NOT_EXISTS
                )

            logger.info("Start to convert pipeline copy request|$projectId|$sourcePipelineId")
            if (sourcePipeline.channelCode != request.channelCode) {
                throw ErrorCodeException(
                    statusCode = Response.Status.NOT_FOUND.statusCode,
                    errorCode = ProcessMessageCode.ERROR_PIPELINE_CHANNEL_CODE,
                    params = arrayOf(sourcePipeline.channelCode.name)
                )
            }

            val sourceModel = pipelineRepositoryService.getPipelineResourceVersion(projectId, sourcePipelineId)?.model
                ?: throw ErrorCodeException(
                    statusCode = Response.Status.NOT_FOUND.statusCode,
                    errorCode = ProcessMessageCode.ERROR_PIPELINE_MODEL_NOT_EXISTS
                )
            val newPipelineId = pipelineIdGenerator.getNextId()
            val model = sourceModel.copy(
                name = request.pipelineCopy.name,
                desc = request.pipelineCopy.desc ?: sourceModel.desc,
                labels = request.pipelineCopy.labels,
                staticViews = request.pipelineCopy.staticViews,
                instanceFromTemplate = false,
                srcTemplateId = null,
                templateId = null,
                template = null,
                overrideTemplateField = null
            )
            modelCheckPlugin.clearUpModel(model)

            val sourceSetting = pipelineSettingFacadeService.getSettingInfo(projectId, sourcePipelineId)

            val setting = sourceSetting?.copy(
                pipelineId = newPipelineId,
                pipelineName = request.pipelineCopy.name,
                desc = model.desc ?: request.pipelineCopy.name,
                labels = request.pipelineCopy.labels
            ) ?: PipelineSetting.defaultSetting(
                projectId = projectId,
                pipelineId = newPipelineId,
                pipelineName = request.pipelineCopy.name,
                desc = model.desc,
                creator = userId,
                updater = userId
            ).copy(labels = request.pipelineCopy.labels)
            val yaml = pipelineVersionGenerator.model2yaml(
                userId = userId,
                projectId = projectId,
                pipelineId = newPipelineId,
                modelAndSetting = PipelineModelAndSetting(
                    model = model,
                    setting = setting
                ),
                oldYaml = null
            )
            pipelineVersionCreateContextFactory.create(
                userId = userId,
                projectId = projectId,
                pipelineId = newPipelineId,
                channelCode = request.channelCode,
                version = version,
                model = model,
                yaml = yaml?.yamlStr,
                pipelineSettingWithoutVersion = setting,
                versionStatus = VersionStatus.RELEASED,
                versionAction = PipelineVersionAction.CREATE_RELEASE
            )
        } catch (e: JsonParseException) {
            logger.error("Parse process(${request.pipelineId}) fail", e)
            throw ErrorCodeException(
                statusCode = Response.Status.NOT_FOUND.statusCode,
                errorCode = ILLEGAL_PIPELINE_MODEL_JSON
            )
        } catch (e: PipelineAlreadyExistException) {
            throw ErrorCodeException(
                statusCode = Response.Status.CONFLICT.statusCode,
                errorCode = ProcessMessageCode.ERROR_PIPELINE_NAME_EXISTS
            )
        } catch (e: ErrorCodeException) {
            throw e
        } catch (e: Exception) {
            logger.warn(
                "Fail to get the pipeline(${request.pipelineId}) definition of project($projectId)",
                e
            )
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.OPERATE_PIPELINE_FAIL,
                params = arrayOf(e.message ?: "")
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineCopyCreateReqConverter::class.java)
    }
}
