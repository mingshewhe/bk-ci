/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.service.template.v2

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.BuildNo
import com.tencent.devops.common.pipeline.pojo.element.atom.PipelineCheckFailedErrors
import com.tencent.devops.common.pipeline.pojo.element.atom.PipelineCheckFailedMsg
import com.tencent.devops.common.redis.RedisLock
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.Profile
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.model.process.tables.records.TTemplateInstanceBaseRecord
import com.tencent.devops.model.process.tables.records.TTemplateInstanceItemRecord
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_PIPELINE_ELEMENT_CHECK_FAILED
import com.tencent.devops.process.engine.dao.template.TemplateInstanceBaseDao
import com.tencent.devops.process.engine.dao.template.TemplateInstanceItemDao
import com.tencent.devops.process.pojo.template.TemplateInstanceStatus
import com.tencent.devops.process.pojo.template.TemplateInstanceUpdate
import com.tencent.devops.process.util.TempNotifyTemplateUtils
import com.tencent.devops.project.api.service.ServiceProjectTagResource
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.text.MessageFormat

@Suppress("ALL")
@Service
@RefreshScope
class PipelineTemplateInstanceCronService @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val templateInstanceBaseDao: TemplateInstanceBaseDao,
    private val templateInstanceItemDao: TemplateInstanceItemDao,
    private val redisOperation: RedisOperation,
    private val client: Client,
    private val pipelineTemplateInstanceFacadeService: PipelineTemplateInstanceFacadeService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateInstanceCronService::class.java)
        private const val LOCK_KEY = "templateInstanceItemLock"
        private const val PAGE_SIZE = 100
    }

    @Value("\${template.instanceListUrl}")
    private val instanceListUrl: String = ""

    @Value("\${template.maxErrorReasonLength:200}")
    private val maxErrorReasonLength: Int = 200

    // todo 新版本上线后，应该停止老版本的代码
    @Scheduled(cron = "0 0/1 * * * ?")
    fun cronUpdateTemplateInstances() {
        val lock = getLock()
        try {
            if (!lock.tryLock()) {
                logger.info("get  template Instance cron lock failed, skip")
                return
            }
            val statusList = listOf(TemplateInstanceStatus.INIT.name, TemplateInstanceStatus.INSTANCING.name)
            val templateInstanceBaseList = templateInstanceBaseDao.getTemplateInstanceBaseList(
                dslContext = dslContext,
                statusList = statusList,
                descFlag = false,
                page = 1,
                pageSize = 10
            )
            templateInstanceBaseList?.forEach { templateInstanceBase ->
                processProjectInstancesUpdate(templateInstanceBase)
            }
        } catch (ignored: Throwable) {
            logger.error("BKSystemErrorMonitor|templateInstance|error=${ignored.message}", ignored)
        } finally {
            lock.unlock()
        }
    }

    private fun getLock(): RedisLock {
        val profile = SpringContextUtil.getBean(Profile::class.java)
        val activeProfiles = profile.getActiveProfiles()
        val key = if (activeProfiles.size > 1) {
            val sb = StringBuilder()
            activeProfiles.forEach { activeProfile ->
                sb.append("$activeProfile:")
            }
            sb.append(LOCK_KEY).toString()
        } else {
            LOCK_KEY
        }
        return RedisLock(redisOperation, key, 3000)
    }

    private fun processProjectInstancesUpdate(templateInstanceBase: TTemplateInstanceBaseRecord) {
        val baseId = templateInstanceBase.id
        val projectId = templateInstanceBase.projectId
        val templateId = templateInstanceBase.templateId
        val templateVersion = templateInstanceBase.templateVersion.toLong()
        val successPipelines = mutableListOf<String>()
        val failurePipelines = mutableListOf<String>()
        val projectRouterTagCheck = client.get(ServiceProjectTagResource::class).checkProjectRouter(projectId).data

        if (!projectRouterTagCheck!!) {
            logger.info("project $projectId router tag is not this cluster")
            return
        }
        val templateInstanceItemCount = templateInstanceItemDao.getTemplateInstanceItemCountByBaseId(
            dslContext = dslContext,
            projectId = projectId,
            baseId = baseId
        )
        if (templateInstanceItemCount < 1) {
            templateInstanceBaseDao.deleteByBaseId(dslContext, projectId, baseId)
            return
        }
        val template = try {
            pipelineTemplateResourceService.get(
                projectId = projectId,
                templateId = templateId,
                version = templateVersion
            )
        } catch (e: ErrorCodeException) {
            if (e.errorCode == ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS) {
                // 模板版本记录如果已经被删，则无需执行更新任务并把任务记录删除
                logger.warn("the version[$templateVersion] of template[$templateId] is not exist,skip the task")
                deleteTemplateInstanceTaskRecord(projectId, baseId)
                return
            } else {
                throw e
            }
        }
        // 把模板批量更新记录状态置为”实例化中“
        templateInstanceBaseDao.updateTemplateInstanceBase(
            dslContext = dslContext,
            projectId = projectId,
            baseId = baseId,
            status = TemplateInstanceStatus.INSTANCING.name,
            userId = "system"
        )

        val templateModel = template.model as Model
        val totalPages = PageUtil.calTotalPage(PAGE_SIZE, templateInstanceItemCount)
        // 分页切片处理当前批次的待处理任务
        for (page in 1..totalPages) {
            val templateInstanceItemList = templateInstanceItemDao.getTemplateInstanceItemListByBaseId(
                dslContext = dslContext,
                projectId = projectId,
                baseId = baseId,
                descFlag = false,
                page = page,
                pageSize = PAGE_SIZE
            )
            templateInstanceItemList?.forEach { templateInstanceItem ->
                processInstanceUpdate(
                    templateId = templateId,
                    templateInstanceItem = templateInstanceItem,
                    useTemplateSettingsFlag = templateInstanceBase.useTemplateSettingsFlag,
                    templateModel = templateModel,
                    settingVersion = template.settingVersion!!,
                    successPipelines = successPipelines,
                    failurePipelines = failurePipelines
                )
            }
        }
        // 删除模板更新任务记录
        deleteTemplateInstanceTaskRecord(projectId, baseId)
        // 发送执行任务结果通知
        TempNotifyTemplateUtils.sendUpdateTemplateInstanceNotify(
            client = client,
            projectId = projectId,
            receivers = mutableSetOf(templateInstanceBase.creator),
            instanceListUrl = MessageFormat(instanceListUrl).format(arrayOf(projectId, template.templateId)),
            successPipelines = successPipelines,
            failurePipelines = failurePipelines
        )
    }

    private fun processInstanceUpdate(
        templateId: String,
        templateInstanceItem: TTemplateInstanceItemRecord,
        useTemplateSettingsFlag: Boolean,
        templateModel: Model,
        settingVersion: Int,
        successPipelines: MutableList<String>,
        failurePipelines: MutableList<String>
    ) {
        val userId = templateInstanceItem.creator
        val pipelineName = templateInstanceItem.pipelineName
        val projectId = templateInstanceItem.projectId
        val param = templateInstanceItem.param?.let {
            JsonUtil.to(it, object : TypeReference<List<BuildFormProperty>?>() {})
        }

        try {
            pipelineTemplateInstanceFacadeService.updateTemplateInstance(
                projectId = templateInstanceItem.projectId,
                userId = userId,
                templateId = templateId,
                useTemplateSettings = useTemplateSettingsFlag,
                templateInstanceUpdate = TemplateInstanceUpdate(
                    pipelineId = templateInstanceItem.pipelineId,
                    pipelineName = templateInstanceItem.pipelineName,
                    buildNo = JsonUtil.toOrNull(templateInstanceItem.buildNoInfo, BuildNo::class.java),
                    param = param
                ),
                templateModel = templateModel,
                settingVersion = settingVersion
            )
            successPipelines.add(pipelineName)
        } catch (exception: ErrorCodeException) {
            logger.info(
                "Fail to update the pipeline|$projectId|${templateInstanceItem.pipelineId}|" +
                    "$userId|${exception.message}"
            )
            val message = I18nUtil.generateResponseDataObject(
                messageCode = exception.errorCode,
                params = exception.params,
                data = null,
                defaultMessage = exception.defaultMessage
            ).message ?: exception.defaultMessage ?: "unknown!"
            // ERROR_PIPELINE_ELEMENT_CHECK_FAILED输出的是一个json,需要格式化输出
            val reason = if (exception.errorCode == ERROR_PIPELINE_ELEMENT_CHECK_FAILED) {
                JsonUtil.to(message, PipelineCheckFailedErrors::class.java)
            } else {
                PipelineCheckFailedMsg(message)
            }
            pipelineTemplateInstanceFacadeService.updateInstanceErrorInfo(
                projectId = projectId,
                pipelineId = templateInstanceItem.pipelineId,
                errorInfo = JsonUtil.toJson(reason, false)
            )
            failurePipelines.add("【$pipelineName】reason：${reason.message}")
        } catch (ignored: Throwable) {
            logger.warn("Fail to update the pipeline|$pipelineName|$projectId|$userId|$ignored")
            val message =
                if (!ignored.message.isNullOrBlank() && ignored.message!!.length > maxErrorReasonLength)
                    ignored.message!!.substring(0, maxErrorReasonLength) + "......"
                else ignored.message
            message?.let {
                pipelineTemplateInstanceFacadeService.updateInstanceErrorInfo(
                    projectId = projectId,
                    pipelineId = templateInstanceItem.pipelineId,
                    errorInfo = JsonUtil.toJson(PipelineCheckFailedMsg(it), false)
                )
            }
            failurePipelines.add("【$pipelineName】reason: $message")
        }
    }

    private fun deleteTemplateInstanceTaskRecord(projectId: String, baseId: String) {
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            templateInstanceItemDao.deleteByBaseId(context, projectId, baseId)
            templateInstanceBaseDao.deleteByBaseId(context, projectId, baseId)
        }
    }
}
