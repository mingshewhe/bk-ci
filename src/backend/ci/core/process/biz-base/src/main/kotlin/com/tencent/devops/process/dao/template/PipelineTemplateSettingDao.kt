package com.tencent.devops.process.dao.template

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.pipeline.pojo.setting.PipelineRunLockType
import com.tencent.devops.common.pipeline.pojo.setting.Subscription
import com.tencent.devops.common.pipeline.template.PipelineTemplateSetting
import com.tencent.devops.model.process.tables.TPipelineTemplateSettingVersion
import com.tencent.devops.model.process.tables.records.TPipelineTemplateSettingVersionRecord
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PipelineTemplateSettingDao {
    fun create(
        dslContext: DSLContext,
        record: PipelineTemplateSetting
    ) {
        val successSubscriptionList = record.successSubscriptionList ?: emptyList()
        val failSubscriptionList = record.failSubscriptionList ?: emptyList()
        with(TPipelineTemplateSettingVersion.T_PIPELINE_TEMPLATE_SETTING_VERSION) {
            dslContext.insertInto(
                this,
                PROJECT_ID,
                TEMPLATE_ID,
                NAME,
                DESC,
                VERSION,
                VERSION_NAME,
                LABELS,
                WAIT_QUEUE_TIME_SECOND,
                MAX_QUEUE_SIZE,
                BUILD_NUM_RULE,
                CONCURRENCY_GROUP,
                CONCURRENCY_CANCEL_IN_PROGRESS,
                PIPELINE_AS_CODE_SETTINGS,
                SUCCESS_SUBSCRIPTION,
                FAILURE_SUBSCRIPTION,
                RUN_LOCK_TYPE,
                MAX_CON_RUNNING_QUEUE_SIZE,
                CREATOR,
                UPDATER
            ).values(
                record.projectId,
                record.templateId,
                record.name,
                record.desc,
                record.version,
                record.versionName,
                record.labels?.let { self -> JsonUtil.toJson(self) },
                DateTimeUtil.minuteToSecond(record.waitQueueTimeMinute),
                record.maxQueueSize,
                record.buildNumRule,
                record.concurrencyGroup,
                record.concurrencyCancelInProgress,
                record.pipelineAsCodeSettings?.let { self -> JsonUtil.toJson(self) },
                JsonUtil.toJson(successSubscriptionList),
                JsonUtil.toJson(failSubscriptionList),
                PipelineRunLockType.toValue(record.runLockType),
                record.maxConRunningQueueSize ?: -1,
                record.creator,
                record.updater
            )
        }
    }

    fun update(
        dslContext: DSLContext,
        record: TPipelineTemplateSettingVersionRecord
    ) {
        with(TPipelineTemplateSettingVersion.T_PIPELINE_TEMPLATE_SETTING_VERSION) {
            val now = LocalDateTime.now()
            dslContext.update(this)
                .apply {
                    record.name?.let { set(NAME, it) }
                    record.desc?.let { set(DESC, it) }
                    record.version?.let { set(VERSION, it) }
                    record.versionName?.let { set(VERSION_NAME, it) }
                    record.labels?.let { set(LABELS, it) }
                    record.waitQueueTimeSecond?.let { set(WAIT_QUEUE_TIME_SECOND, it) }
                    record.maxQueueSize?.let { set(MAX_QUEUE_SIZE, it) }
                    record.buildNumRule?.let { set(BUILD_NUM_RULE, it) }
                    record.concurrencyGroup?.let { set(CONCURRENCY_GROUP, it) }
                    record.concurrencyCancelInProgress?.let { set(CONCURRENCY_CANCEL_IN_PROGRESS, it) }
                    record.pipelineAsCodeSettings?.let { set(PIPELINE_AS_CODE_SETTINGS, it) }
                    record.successSubscription?.let { set(SUCCESS_SUBSCRIPTION, it) }
                    record.failureSubscription?.let { set(FAILURE_SUBSCRIPTION, it) }
                    record.runLockType?.let { set(RUN_LOCK_TYPE, it) }
                    record.maxConRunningQueueSize?.let { set(MAX_CON_RUNNING_QUEUE_SIZE, it) }
                }
                .set(UPDATE_TIME, now)
                .where(PROJECT_ID.eq(record.projectId))
                .and(TEMPLATE_ID.eq(record.templateId))
        }
    }

    fun list(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateSettingCommonCondition,
        limit: Int? = null,
        offset: Int? = null
    ): List<PipelineTemplateSetting> {
        return with(TPipelineTemplateSettingVersion.T_PIPELINE_TEMPLATE_SETTING_VERSION) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(commonCondition))
                .let { if (limit != null && offset != null) it.limit(limit).offset(offset) else it }
                .fetch().map { it.convert() }
        }
    }

    fun get(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateSettingCommonCondition,
    ): PipelineTemplateSetting? {
        return with(TPipelineTemplateSettingVersion.T_PIPELINE_TEMPLATE_SETTING_VERSION) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(commonCondition))
                .fetchOne()?.convert()
        }
    }

    fun delete(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateSettingCommonCondition,
    ) {
        return with(TPipelineTemplateSettingVersion.T_PIPELINE_TEMPLATE_SETTING_VERSION) {
            dslContext.deleteFrom(this)
                .where(buildQueryCondition(commonCondition))
                .execute()
        }
    }

    fun buildQueryCondition(
        commonCondition: PipelineTemplateSettingCommonCondition
    ): MutableList<Condition> {
        return with(TPipelineTemplateSettingVersion.T_PIPELINE_TEMPLATE_SETTING_VERSION) {
            with(commonCondition) {
                val conditions = mutableListOf<Condition>()
                conditions.add(PROJECT_ID.eq(projectId))
                if (templateId != null) conditions.add(TEMPLATE_ID.eq(templateId))
                if (name != null) conditions.add(NAME.like("%$name%"))
                if (version != null) conditions.add(VERSION.eq(version))
                if (versionName != null) conditions.add(VERSION_NAME.eq(versionName))
                if (creator != null) conditions.add(CREATOR.eq(creator))
                if (updater != null) conditions.add(UPDATER.eq(updater))
                conditions
            }
        }
    }

    fun TPipelineTemplateSettingVersionRecord.convert(): PipelineTemplateSetting {
        val successSubscriptionList = this.successSubscription?.let {
            JsonUtil.to(it, object : TypeReference<List<Subscription>>() {})
        }
        val failSubscriptionList = this.failureSubscription?.let {
            JsonUtil.to(it, object : TypeReference<List<Subscription>>() {})
        }
        return PipelineTemplateSetting(
            projectId = this.projectId,
            templateId = this.templateId,
            name = this.name,
            desc = this.desc,
            runLockType = this.runLockType?.let { PipelineRunLockType.valueOf(it) },
            successSubscriptionList = successSubscriptionList,
            failSubscriptionList = failSubscriptionList,
            version = this.version,
            versionName = this.versionName,
            labels = this.labels?.let {
                JsonUtil.to(it, object : TypeReference<List<String>>() {})
            },
            waitQueueTimeMinute = DateTimeUtil.secondToMinute(this.waitQueueTimeSecond ?: 600000),
            maxQueueSize = this.maxQueueSize,
            buildNumRule = this.buildNumRule,
            concurrencyCancelInProgress = this.concurrencyCancelInProgress,
            concurrencyGroup = this.concurrencyGroup,
            maxConRunningQueueSize = this.maxConRunningQueueSize,
            pipelineAsCodeSettings = this.pipelineAsCodeSettings?.let { self ->
                JsonUtil.to(self, PipelineAsCodeSettings::class.java)
            },
            creator = creator,
            updater = updater
        )
    }
}
