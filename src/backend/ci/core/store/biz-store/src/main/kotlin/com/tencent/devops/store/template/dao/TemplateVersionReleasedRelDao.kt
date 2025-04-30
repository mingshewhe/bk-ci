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

package com.tencent.devops.store.template.dao

import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.api.util.toLocalDateTimeOrDefault
import com.tencent.devops.model.store.tables.TTemplateVersionReleasedRel
import com.tencent.devops.store.pojo.template.TemplateVersionRelationInfo
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Suppress("ALL")
@Repository
class TemplateVersionReleasedRelDao {
    fun createOrUpdate(
        dslContext: DSLContext,
        record: TemplateVersionRelationInfo
    ) {
        with(TTemplateVersionReleasedRel.T_TEMPLATE_VERSION_RELEASED_REL) {
            dslContext.insertInto(
                this,
                PROJECT_CODE,
                TEMPLATE_ID,
                TEMPLATE_CODE,
                VERSION,
                NUMBER,
                VERSION_NAME,
                PUBLISHED,
                CREATOR,
                UPDATER,
                CREATE_TIME,
                UPDATE_TIME
            ).values(
                record.projectCode,
                record.templateId,
                record.templateCode,
                record.version,
                record.number,
                record.versionName,
                record.published,
                record.creator,
                record.updater,
                record.createTime.toLocalDateTimeOrDefault(),
                record.updateTime.toLocalDateTimeOrDefault(),
            ).onDuplicateKeyUpdate()
                .set(PUBLISHED, record.published)
                .set(UPDATER, record.updater)
                .set(UPDATE_TIME, record.updateTime.toLocalDateTimeOrDefault())
                .execute()
        }
    }

    fun getLatestReleasedVersion(
        dslContext: DSLContext,
        templateId: String
    ): TemplateVersionRelationInfo? {
        return with(TTemplateVersionReleasedRel.T_TEMPLATE_VERSION_RELEASED_REL) {
            dslContext.selectFrom(this)
                .where(TEMPLATE_ID.eq(templateId))
                .and(PUBLISHED.eq(true))
                .orderBy(NUMBER.desc())
                .limit(1)
                .fetchOne()?.let {
                    TemplateVersionRelationInfo(
                        projectCode = it.projectCode,
                        templateId = it.templateId,
                        templateCode = it.templateCode,
                        version = it.version,
                        number = it.number,
                        versionName = it.versionName,
                        published = it.published,
                        createTime = it.createTime.timestampmilli(),
                        updateTime = it.updateTime.timestampmilli(),
                        creator = it.creator,
                        updater = it.updater
                    )
                }
        }
    }

    fun listLatestPublishedVersions(
        dslContext: DSLContext,
        templateIds: List<String>
    ): List<TemplateVersionRelationInfo> {
        return with(TTemplateVersionReleasedRel.T_TEMPLATE_VERSION_RELEASED_REL) {
            // 子查询获取每个模板的最大NUMBER
            val maxNumbers = dslContext.select(TEMPLATE_ID, DSL.max(NUMBER).`as`("max_number"))
                .from(this)
                .where(TEMPLATE_ID.`in`(templateIds))
                .groupBy(TEMPLATE_ID)
                .asTable("m")
            // 主查询关联获取VERSION
            dslContext.select()
                .from(this)
                .join(maxNumbers)
                .on(
                    TEMPLATE_ID.eq(maxNumbers.field(TEMPLATE_ID)),
                    NUMBER.eq(maxNumbers.field("max_number", Int::class.java))
                )
                .fetch().map {
                    TemplateVersionRelationInfo(
                        projectCode = it[PROJECT_CODE],
                        templateId = it[TEMPLATE_ID],
                        templateCode = it[TEMPLATE_CODE],
                        version = it[VERSION],
                        number = it[NUMBER],
                        versionName = it[VERSION_NAME],
                        published = it[PUBLISHED],
                        createTime = it[CREATE_TIME].timestampmilli(),
                        updateTime = it[UPDATE_TIME]?.timestampmilli(),
                        creator = it[CREATOR],
                        updater = it[UPDATER]
                    )
                }
        }
    }
}
