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
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.enums.CodeTargetAction
import com.tencent.devops.model.process.tables.TTemplateInstanceBase
import com.tencent.devops.model.process.tables.records.TTemplateInstanceBaseRecord
import com.tencent.devops.process.pojo.template.TemplateInstanceStatus
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInstanceBase
import com.tencent.devops.process.pojo.template.v2.TemplateInstanceType
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Suppress("ALL")
@Repository
class TemplateInstanceBaseDao {

    fun createTemplateInstanceBase(
        dslContext: DSLContext,
        baseId: String,
        templateId: String,
        templateVersion: String,
        useTemplateSettingsFlag: Boolean,
        projectId: String,
        totalItemNum: Int,
        status: String,
        userId: String,
        pac: Boolean? = null,
        targetAction: String? = null,
        type: String? = TemplateInstanceType.UPDATE.name,
        labels: String? = null,
        staticViews: String? = null
    ) {
        with(TTemplateInstanceBase.T_TEMPLATE_INSTANCE_BASE) {
            setOf(
                dslContext.insertInto(
                    this,
                    ID,
                    TEMPLATE_ID,
                    TEMPLATE_VERSION,
                    USE_TEMPLATE_SETTINGS_FLAG,
                    PROJECT_ID,
                    TOTAL_ITEM_NUM,
                    STATUS,
                    CREATOR,
                    MODIFIER,
                    PAC,
                    TARGET_ACTION,
                    TYPE,
                    LABELS,
                    STATIC_VIEWS
                ).values(
                    baseId,
                    templateId,
                    templateVersion,
                    useTemplateSettingsFlag,
                    projectId,
                    totalItemNum,
                    status,
                    userId,
                    userId,
                    pac,
                    targetAction,
                    type,
                    labels,
                    staticViews
                )
                    .onDuplicateKeyUpdate()
                    .set(TEMPLATE_ID, templateId)
                    .set(TEMPLATE_VERSION, templateVersion)
                    .set(USE_TEMPLATE_SETTINGS_FLAG, useTemplateSettingsFlag)
                    .set(TOTAL_ITEM_NUM, totalItemNum)
                    .set(STATUS, status)
                    .set(CREATOR, userId)
                    .set(MODIFIER, userId)
                    .set(PAC, pac)
                    .set(TARGET_ACTION, targetAction)
                    .set(TYPE, type)
                    .set(LABELS, labels)
                    .set(STATIC_VIEWS, staticViews)
                    .execute()
            )
        }
    }

    fun updateTemplateInstanceBase(
        dslContext: DSLContext,
        projectId: String,
        baseId: String,
        successItemNum: Int? = null,
        failItemNum: Int? = null,
        status: String? = null,
        userId: String
    ) {
        with(TTemplateInstanceBase.T_TEMPLATE_INSTANCE_BASE) {
            val baseStep = dslContext.update(this)
            if (successItemNum != null) {
                baseStep.set(SUCCESS_ITEM_NUM, successItemNum)
            }
            if (failItemNum != null) {
                baseStep.set(FAIL_ITEM_NUM, failItemNum)
            }
            if (status != null) {
                baseStep.set(STATUS, status)
            }
            baseStep.set(UPDATE_TIME, LocalDateTime.now())
                .set(MODIFIER, userId)
                .where(ID.eq(baseId).and(PROJECT_ID.eq(projectId)))
                .execute()
        }
    }

    fun getTemplateInstanceBase(
        dslContext: DSLContext,
        projectId: String,
        baseId: String
    ): PipelineTemplateInstanceBase? {
        return with(TTemplateInstanceBase.T_TEMPLATE_INSTANCE_BASE) {
            dslContext.selectFrom(this)
                .where(ID.eq(baseId).and(PROJECT_ID.eq(projectId)))
                .fetchOne()?.convert()
        }
    }

    fun updateStatus(
        dslContext: DSLContext,
        projectId: String,
        baseId: String,
        status: TemplateInstanceStatus
    ) {
        return with(TTemplateInstanceBase.T_TEMPLATE_INSTANCE_BASE) {
            dslContext.update(this)
                .set(STATUS, status.name)
                .where(ID.eq(baseId).and(PROJECT_ID.eq(projectId)))
                .execute()
        }
    }

    fun getTemplateInstanceBaseList(
        dslContext: DSLContext,
        statusList: List<String>,
        descFlag: Boolean,
        page: Int,
        pageSize: Int
    ): Result<TTemplateInstanceBaseRecord>? {
        with(TTemplateInstanceBase.T_TEMPLATE_INSTANCE_BASE) {
            val baseStep = dslContext.selectFrom(this).where(STATUS.`in`(statusList))
            if (descFlag) {
                baseStep.orderBy(CREATE_TIME.desc())
            } else {
                baseStep.orderBy(CREATE_TIME.asc())
            }
            return baseStep.limit((page - 1) * pageSize, pageSize).fetch()
        }
    }

    fun deleteByBaseId(dslContext: DSLContext, projectId: String, baseId: String) {
        with(TTemplateInstanceBase.T_TEMPLATE_INSTANCE_BASE) {
            dslContext.deleteFrom(this)
                .where(ID.eq(baseId).and(PROJECT_ID.eq(projectId)))
                .execute()
        }
    }

    fun list(
        dslContext: DSLContext,
        projectId: String,
        excludeStatusList: List<String>,
        type: TemplateInstanceType
    ): List<PipelineTemplateInstanceBase> {
        return with(TTemplateInstanceBase.T_TEMPLATE_INSTANCE_BASE) {
            dslContext.selectFrom(this)
                .where(PROJECT_ID.eq(projectId))
                .and(STATUS.notIn(excludeStatusList))
                .and(TYPE.eq(type.name))
                .fetch().map { it.convert() }
        }
    }

    private fun TTemplateInstanceBaseRecord.convert(): PipelineTemplateInstanceBase {
        val labels = labels?.let {
            JsonUtil.to(it, object : TypeReference<List<String>>() {})
        }
        val staticViews = staticViews?.let {
            JsonUtil.to(it, object : TypeReference<List<String>>() {})
        }
        return PipelineTemplateInstanceBase(
            baseId = id,
            projectId = projectId,
            templateId = templateId,
            templateVersion = templateVersion.toInt(),
            useTemplateSetting = useTemplateSettingsFlag,
            totalItemNum = totalItemNum,
            successItemNum = successItemNum,
            failItemNum = failItemNum,
            description = description,
            status = TemplateInstanceStatus.valueOf(status),
            pac = pac,
            targetAction = CodeTargetAction.valueOf(targetAction),
            type = TemplateInstanceType.valueOf(type),
            labels = labels,
            staticViews = staticViews,
            creator = creator,
            modifier = modifier,
            createTime = createTime.timestampmilli(),
            updateTime = updateTime.timestampmilli()
        )
    }
}
