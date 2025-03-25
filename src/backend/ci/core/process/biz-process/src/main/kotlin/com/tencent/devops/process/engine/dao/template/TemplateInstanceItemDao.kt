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

package com.tencent.devops.process.engine.dao.template

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.BuildNo
import com.tencent.devops.model.process.tables.TTemplateInstanceItem
import com.tencent.devops.model.process.tables.records.TTemplateInstanceItemRecord
import com.tencent.devops.process.pojo.pipeline.PipelineYamlVo
import com.tencent.devops.process.pojo.template.TemplateInstanceStatus
import com.tencent.devops.process.pojo.template.TemplateInstanceUpdate
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstanceItem
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstanceReleaseInfo
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Suppress("LongParameterList")
@Repository
class TemplateInstanceItemDao {

    fun createTemplateInstanceItem(
        dslContext: DSLContext,
        projectId: String,
        baseId: String,
        instances: List<TemplateInstanceUpdate>,
        status: String,
        userId: String
    ) {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            instances.map {
                val buildNo = it.buildNo
                val param = it.param
                dslContext.insertInto(
                    this,
                    ID,
                    PROJECT_ID,
                    PIPELINE_ID,
                    PIPELINE_NAME,
                    BUILD_NO_INFO,
                    STATUS,
                    PARAM,
                    BASE_ID,
                    CREATOR,
                    MODIFIER
                )
                    .values(
                        UUIDUtil.generate(),
                        projectId,
                        it.pipelineId,
                        it.pipelineName,
                        buildNo?.let { self -> JsonUtil.toJson(self, formatted = false) },
                        status,
                        param?.let { self -> JsonUtil.toJson(self, formatted = false) },
                        baseId,
                        userId,
                        userId
                    )
                    .onDuplicateKeyUpdate()
                    .set(PIPELINE_NAME, it.pipelineName)
                    .set(BUILD_NO_INFO, buildNo?.let { self -> JsonUtil.toJson(self, formatted = false) })
                    .set(STATUS, status)
                    .set(PARAM, param?.let { self -> JsonUtil.toJson(self, formatted = false) })
                    .set(BASE_ID, baseId)
                    .set(CREATOR, userId)
                    .set(MODIFIER, userId)
                    .execute()
            }
        }
    }

    fun createTemplateInstanceItemsV2(
        dslContext: DSLContext,
        projectId: String,
        baseId: String,
        instances: List<PipelineTemplateInstanceReleaseInfo>,
        status: String,
        userId: String
    ) {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            instances.map {
                val buildNo = it.buildNo
                val param = it.param
                dslContext.insertInto(
                    this,
                    ID,
                    PROJECT_ID,
                    PIPELINE_ID,
                    PIPELINE_NAME,
                    BUILD_NO_INFO,
                    STATUS,
                    PARAM,
                    BASE_ID,
                    CREATOR,
                    MODIFIER,
                    FILE_PATH
                ).values(
                    UUIDUtil.generate(),
                    projectId,
                    it.pipelineId,
                    it.pipelineName,
                    buildNo?.let { self -> JsonUtil.toJson(self, formatted = false) },
                    status,
                    param?.let { self -> JsonUtil.toJson(self, formatted = false) },
                    baseId,
                    userId,
                    userId,
                    it.filePath
                ).onDuplicateKeyUpdate()
                    .set(PIPELINE_NAME, it.pipelineName)
                    .set(BUILD_NO_INFO, buildNo?.let { self -> JsonUtil.toJson(self, formatted = false) })
                    .set(STATUS, status)
                    .set(PARAM, param?.let { self -> JsonUtil.toJson(self, formatted = false) })
                    .set(BASE_ID, baseId)
                    .set(CREATOR, userId)
                    .set(MODIFIER, userId)
                    .set(FILE_PATH, it.filePath)
                    .execute()
            }
        }
    }

    fun getTemplateInstanceItemList(
        dslContext: DSLContext,
        status: String,
        descFlag: Boolean,
        page: Int,
        pageSize: Int
    ): Result<TTemplateInstanceItemRecord>? {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            val baseStep = dslContext.selectFrom(this).where(STATUS.eq(status))
            if (descFlag) {
                baseStep.orderBy(CREATE_TIME.desc())
            } else {
                baseStep.orderBy(CREATE_TIME.asc())
            }
            return baseStep.limit((page - 1) * pageSize, pageSize).fetch()
        }
    }

    fun getTemplateInstanceItemListByBaseId(
        dslContext: DSLContext,
        projectId: String,
        baseId: String,
        descFlag: Boolean,
        page: Int,
        pageSize: Int
    ): Result<TTemplateInstanceItemRecord>? {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            val baseStep = dslContext.selectFrom(this).where(BASE_ID.eq(baseId).and(PROJECT_ID.eq(projectId)))
            if (descFlag) {
                baseStep.orderBy(CREATE_TIME.desc())
            } else {
                baseStep.orderBy(CREATE_TIME.asc())
            }
            return baseStep.limit((page - 1) * pageSize, pageSize).fetch()
        }
    }

    fun listTemplateInstanceItemByBaseIds(
        dslContext: DSLContext,
        projectId: String,
        baseIds: List<String>,
        statusList: List<String>? = emptyList(),
        excludeStatusList: List<String>? = emptyList(),
        page: Int? = null,
        pageSize: Int? = null
    ): List<PipelineTemplateInstanceItem> {
        return with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            dslContext.selectFrom(this)
                .where(BASE_ID.`in`(baseIds))
                .and(PROJECT_ID.eq(projectId))
                .let { if (!statusList.isNullOrEmpty()) it.and(STATUS.`in`(statusList)) else it }
                .let { if (!excludeStatusList.isNullOrEmpty()) it.and(STATUS.notIn(excludeStatusList)) else it }
                .let { if (page != null && pageSize != null) it.limit((page - 1) * pageSize, pageSize) else it }
                .fetch()
                .map { it.convert() }
        }
    }

    fun getTemplateInstanceItemCountByBaseId(
        dslContext: DSLContext,
        projectId: String,
        baseId: String,
        statusList: List<String>? = emptyList(),
        excludeStatusList: List<String>? = emptyList(),
    ): Long {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            return dslContext.selectCount().from(this)
                .where(BASE_ID.eq(baseId).and(PROJECT_ID.eq(projectId)))
                .let { if (!statusList.isNullOrEmpty()) it.and(STATUS.`in`(statusList)) else it }
                .let { if (!excludeStatusList.isNullOrEmpty()) it.and(STATUS.notIn(excludeStatusList)) else it }
                .fetchOne(0, Long::class.java)!!
        }
    }

    fun getTemplateInstanceItemListByPipelineIds(
        dslContext: DSLContext,
        projectId: String,
        pipelineIds: Collection<String>
    ): Result<TTemplateInstanceItemRecord>? {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            return dslContext.selectFrom(this)
                .where(PIPELINE_ID.`in`(pipelineIds).and(PROJECT_ID.eq(projectId))).fetch()
        }
    }

    fun deleteByBaseId(dslContext: DSLContext, projectId: String, baseId: String) {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            dslContext.deleteFrom(this)
                .where(BASE_ID.eq(baseId).and(PROJECT_ID.eq(projectId)))
                .execute()
        }
    }

    fun updateErrorMessage(
        dslContext: DSLContext,
        projectId: String,
        baseId: String,
        pipelineId: String,
        errorMessage: String
    ) {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            dslContext.update(this)
                .set(ERROR_MESSGAE, errorMessage)
                .set(STATUS, TemplateInstanceStatus.FAILED.name)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(BASE_ID.eq(baseId))
                .and(PROJECT_ID.eq(projectId))
                .and(PIPELINE_ID.eq(pipelineId))
                .execute()
        }
    }

    fun updateStatus(
        dslContext: DSLContext,
        projectId: String,
        baseId: String,
        pipelineIds: List<String>,
        status: TemplateInstanceStatus
    ) {
        with(TTemplateInstanceItem.T_TEMPLATE_INSTANCE_ITEM) {
            dslContext.update(this)
                .set(STATUS, status.name)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(BASE_ID.eq(baseId))
                .and(PROJECT_ID.eq(projectId))
                .and(PIPELINE_ID.`in`(pipelineIds))
                .execute()
        }
    }

    fun TTemplateInstanceItemRecord.convert(): PipelineTemplateInstanceItem {
        val params = param?.let {
            JsonUtil.to(it, object : TypeReference<List<BuildFormProperty>>() {})
        }
        val buildNo = buildNoInfo?.let {
            JsonUtil.to(it, object : TypeReference<BuildNo>() {})
        }
        return PipelineTemplateInstanceItem(
            id = id,
            baseId = baseId,
            projectId = projectId,
            pipelineId = pipelineId,
            pipelineName = pipelineName,
            buildNo = buildNo,
            status = TemplateInstanceStatus.valueOf(status),
            params = params,
            filePath = filePath,
            errorMessage = errorMessgae,
            creator = creator,
            modifier = modifier,
            createTime = createTime.timestampmilli(),
            updateTime = updateTime.timestampmilli()
        )
    }
}
