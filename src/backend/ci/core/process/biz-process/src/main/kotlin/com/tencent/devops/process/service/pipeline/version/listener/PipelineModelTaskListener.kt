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

package com.tencent.devops.process.service.pipeline.version.listener

import com.tencent.devops.common.api.util.AESUtil
import com.tencent.devops.common.pipeline.event.PipelineCallbackEvent
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.process.dao.PipelineCallbackDao
import com.tencent.devops.process.engine.dao.PipelineModelTaskDao
import com.tencent.devops.process.engine.service.SubPipelineTaskService
import com.tencent.devops.process.pojo.pipeline.PipelineBasicInfo
import com.tencent.devops.process.pojo.pipeline.PipelineModelData
import com.tencent.devops.process.pojo.pipeline.PipelineResourceVersion
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 流水线版本创建时,处理流水线插件监听器
 */
@Service
class PipelineModelTaskListener @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineModelTaskDao: PipelineModelTaskDao,
    private val pipelineCallbackDao: PipelineCallbackDao,
    private val subPipelineTaskService: SubPipelineTaskService,
) : PipelineVersionCreateListener {
    @Value("\${project.callback.secretParam.aes-key:project_callback_aes_key}")
    private val aesKey = ""

    override fun onCreate(
        userId: String,
        pipelineBasicInfo: PipelineBasicInfo,
        pipelineModelData: PipelineModelData,
        pipelineResourceVersion: PipelineResourceVersion,
        pipelineSetting: PipelineSetting
    ) {
        with(pipelineBasicInfo) {
            dslContext.transaction { configuration ->
                val transactionContext = DSL.using(configuration)
                pipelineModelTaskDao.batchSave(
                    dslContext = transactionContext,
                    modelTasks = pipelineModelData.modelTasks
                )
                // 初始化流水线单体回调
                savePipelineCallback(
                    dslContext = transactionContext,
                    userId = userId,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    events = pipelineModelData.events,
                )
                // 初始化子流水线关联关系
                subPipelineTaskService.batchAdd(
                    dslContext = transactionContext,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    model = pipelineResourceVersion.model,
                    channel = channelCode.name,
                    modelTasks = pipelineModelData.modelTasks
                )
            }
        }
    }

    override fun onUpdate(
        userId: String,
        pipelineBasicInfo: PipelineBasicInfo,
        pipelineModelData: PipelineModelData,
        pipelineResourceVersion: PipelineResourceVersion,
        pipelineSetting: PipelineSetting
    ) {
        with(pipelineBasicInfo) {
            dslContext.transaction { configuration ->
                val transactionContext = DSL.using(configuration)
                pipelineModelTaskDao.deletePipelineTasks(
                    dslContext = transactionContext,
                    projectId = projectId,
                    pipelineId = pipelineId
                )
                pipelineModelTaskDao.batchSave(
                    dslContext = transactionContext,
                    modelTasks = pipelineModelData.modelTasks
                )
                // 保存流水线单体回调记录
                savePipelineCallback(
                    dslContext = transactionContext,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    userId = userId,
                    events = pipelineModelData.events
                )
                subPipelineTaskService.batchAdd(
                    dslContext = transactionContext,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    model = pipelineResourceVersion.model,
                    channel = channelCode.name,
                    modelTasks = pipelineModelData.modelTasks
                )
            }
        }
    }

    /**
     * 保存流水线单体回调记录
     */
    private fun savePipelineCallback(
        events: Map<String, PipelineCallbackEvent>?,
        pipelineId: String,
        projectId: String,
        dslContext: DSLContext,
        userId: String
    ) {
        if (events.isNullOrEmpty()) return
        val existEventNames = pipelineCallbackDao.list(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = pipelineId
        ).map { it.name }.toSet()
        if (existEventNames.isNotEmpty()) {
            val needDelNames = existEventNames.subtract(events.keys).toSet()
            pipelineCallbackDao.delete(
                dslContext = dslContext,
                projectId = projectId,
                pipelineId = pipelineId,
                names = needDelNames
            )
        }
        // 保存回调事件
        pipelineCallbackDao.save(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = pipelineId,
            userId = userId,
            list = events.map { (key, value) ->
                value.copy(secretToken = value.secretToken?.let { AESUtil.encrypt(aesKey, it) })
            }
        )
    }
}
