package com.tencent.devops.process.dao.template

import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.model.process.tables.TPipelineTemplateInfo
import com.tencent.devops.model.process.tables.records.TPipelineTemplateInfoRecord
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.common.pipeline.template.PipelineTemplateInfo
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PipelineTemplateInfoDao {
    fun create(
        dslContext: DSLContext,
        record: PipelineTemplateInfo
    ) {
        with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.insertInto(
                this,
                ID,
                PROJECT_ID,
                NAME,
                DESC,
                MODE,
                CATEGORY,
                TYPE,
                LOGO_URL,
                PAC,
                LASTED_VERSION,
                LASTED_VERSION_NAME,
                LASTED_SETTING_VERSION,
                SOURCE,
                STORE_FLAG,
                SRC_TEMPLATE_ID,
                SRC_TEMPLATE_PROJECT_ID,
                DEBUG_PIPELINE_COUNT,
                INSTANCE_PIPELINE_COUNT,
                CREATOR,
                UPDATER
            ).values(
                record.id,
                record.projectId,
                record.name,
                record.desc,
                record.mode,
                record.category,
                record.type.value,
                record.logoUrl,
                record.enablePac,
                record.lastedVersion,
                record.lastedVersionName,
                record.lastedSettingVersion,
                record.source.value,
                record.storeFlag,
                record.srcTemplateId,
                record.srcTemplateProjectId,
                record.debugPipelineCount,
                record.instancePipelineCount,
                record.creator,
                record.updater
            ).execute()
        }
    }

    fun update(
        dslContext: DSLContext,
        record: TPipelineTemplateInfoRecord
    ) {
        with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            val now = LocalDateTime.now()
            dslContext.update(this)
                .apply {
                    record.name?.let { set(NAME, it) }
                    record.desc?.let { set(DESC, it) }
                    record.mode?.let { set(MODE, it) }
                    record.category?.let { set(CATEGORY, it) }
                    record.type?.let { set(TYPE, it) }
                    record.logoUrl?.let { set(LOGO_URL, it) }
                    record.pac?.let { set(PAC, it) }
                    record.lastedVersion?.let { set(LASTED_VERSION, it) }
                    record.lastedVersionName?.let { set(LASTED_VERSION_NAME, it) }
                    record.lastedSettingVersion?.let { set(LASTED_SETTING_VERSION, it) }
                    record.source?.let { set(SOURCE, it) }
                    record.storeFlag?.let { set(STORE_FLAG, it) }
                    record.srcTemplateId?.let { set(SRC_TEMPLATE_ID, it) }
                    record.srcTemplateProjectId?.let { set(SRC_TEMPLATE_PROJECT_ID, it) }
                    record.debugPipelineCount?.let { set(DEBUG_PIPELINE_COUNT, it) }
                    record.instancePipelineCount?.let { set(INSTANCE_PIPELINE_COUNT, it) }
                }
                .set(UPDATER, record.updater)
                .set(UPDATE_TIME, now)
                .where(PROJECT_ID.eq(record.projectId))
                .and(ID.eq(record.id))
                .execute()
        }
    }

    fun list(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateCommonCondition,
        limit: Int? = null,
        offset: Int? = null
    ): List<PipelineTemplateInfo> {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(commonCondition))
                .let { if (limit != null && offset != null) it.limit(limit).offset(offset) else it }
                .fetch().map { it.convert() }
        }
    }

    fun get(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateCommonCondition
    ): PipelineTemplateInfo? {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(commonCondition))
                .fetchOne()?.convert()
        }
    }

    fun delete(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateCommonCondition
    ) {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.deleteFrom(this)
                .where(buildQueryCondition(commonCondition))
                .execute()
        }
    }

    fun buildQueryCondition(commonCondition: PipelineTemplateCommonCondition): MutableList<Condition> {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            with(commonCondition) {
                val conditions = mutableListOf<Condition>()
                conditions.add(PROJECT_ID.eq(projectId))
                if (name != null) conditions.add(NAME.like("%${name}%"))
                if (type != null) conditions.add(TYPE.eq(type!!.value))
                if (enablePac != null) conditions.add(PAC.eq(enablePac))
                if (lastedVersion != null) conditions.add(LASTED_VERSION.eq(lastedVersion))
                if (lastedVersionName != null) conditions.add(LASTED_VERSION_NAME.eq(lastedVersionName))
                if (lastedSettingVersion != null) conditions.add(LASTED_SETTING_VERSION.eq(lastedSettingVersion))
                if (source != null) conditions.add(SOURCE.eq(source!!.value))
                if (storeFlag != null) conditions.add(STORE_FLAG.eq(storeFlag))
                if (srcTemplateId != null) conditions.add(SRC_TEMPLATE_ID.eq(srcTemplateId))
                if (srcTemplateProjectId != null) conditions.add(SRC_TEMPLATE_PROJECT_ID.eq(srcTemplateProjectId))
                if (debugPipelineCount != null) conditions.add(DEBUG_PIPELINE_COUNT.eq(debugPipelineCount))
                if (instancePipelineCount != null) conditions.add(INSTANCE_PIPELINE_COUNT.eq(instancePipelineCount))
                if (creator != null) conditions.add(CREATOR.eq(creator))
                if (updater != null) conditions.add(UPDATER.eq(updater))
                conditions
            }
        }
    }

    fun TPipelineTemplateInfoRecord.convert(): PipelineTemplateInfo {
        return PipelineTemplateInfo(
            id = this.id,
            projectId = this.projectId,
            name = this.name,
            desc = this.desc,
            mode = this.mode,
            category = this.category,
            type = PipelineTemplateType.get(this.type),
            logoUrl = this.logoUrl,
            enablePac = this.pac,
            lastedVersion = this.lastedVersion,
            lastedVersionName = this.lastedVersionName,
            lastedSettingVersion = this.lastedSettingVersion,
            source = PipelineTemplateSource.get(this.source),
            storeFlag = this.storeFlag,
            srcTemplateId = this.srcTemplateId,
            srcTemplateProjectId = this.srcTemplateProjectId,
            debugPipelineCount = this.debugPipelineCount,
            instancePipelineCount = this.instancePipelineCount,
            creator = this.creator,
            updater = this.updater
        )
    }
}
