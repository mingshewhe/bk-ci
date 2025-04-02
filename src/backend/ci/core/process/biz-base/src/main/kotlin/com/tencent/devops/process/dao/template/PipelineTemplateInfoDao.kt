package com.tencent.devops.process.dao.template

import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.model.process.tables.TPipelineTemplateInfo
import com.tencent.devops.model.process.tables.records.TPipelineTemplateInfoRecord
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.process.pojo.enums.UpgradeStrategyEnum
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoUpdateInfo
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
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
                UPGRADE_STRATEGY,
                SETTING_SYNC_STRATEGY,
                CATEGORY,
                TYPE,
                LOGO_URL,
                PAC,
                RELEASED_VERSION,
                RELEASED_VERSION_NAME,
                RELEASED_SETTING_VERSION,
                LATEST_VERSION_STATUS,
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
                record.mode.name,
                record.upgradeStrategy?.name,
                record.settingSyncStrategy?.name,
                record.category,
                record.type.name,
                record.logoUrl,
                record.enablePac,
                record.releasedVersion,
                record.releasedVersionName,
                record.releasedSettingVersion,
                record.latestVersionStatus.name,
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
        record: PipelineTemplateInfoUpdateInfo,
        commonCondition: PipelineTemplateCommonCondition
    ) {
        with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            val now = LocalDateTime.now()
            dslContext.update(this)
                .apply {
                    record.name?.let { set(NAME, it) }
                    record.desc?.let { set(DESC, it) }
                    record.category?.let { set(CATEGORY, it) }
                    record.logoUrl?.let { set(LOGO_URL, it) }
                    record.enablePac?.let { set(PAC, it) }
                    record.releasedVersion?.let { set(RELEASED_VERSION, it) }
                    record.releasedVersionName?.let { set(RELEASED_VERSION_NAME, it) }
                    record.releasedSettingVersion?.let { set(RELEASED_SETTING_VERSION, it) }
                    record.storeFlag?.let { set(STORE_FLAG, it) }
                    record.debugPipelineCount?.let { set(DEBUG_PIPELINE_COUNT, it) }
                    record.instancePipelineCount?.let { set(INSTANCE_PIPELINE_COUNT, it) }
                    record.latestVersionStatus?.let { set(LATEST_VERSION_STATUS, it.name) }
                    record.upgradeStrategy?.let { set(UPGRADE_STRATEGY, it.name) }
                    record.settingSyncStrategy?.let { set(SETTING_SYNC_STRATEGY, it.name) }
                }
                .set(UPDATER, record.updater)
                .set(UPDATE_TIME, now)
                .where(buildQueryCondition(commonCondition))
                .execute()
        }
    }

    fun list(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateCommonCondition
    ): List<PipelineTemplateInfo> {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(commonCondition))
                .orderBy(UPDATE_TIME.desc())
                .let {
                    if (commonCondition.page != null && commonCondition.pageSize != null) {
                        it.offset((commonCondition.page!! - 1) * commonCondition.pageSize!!)
                            .limit(commonCondition.pageSize)
                    } else {
                        it
                    }
                }
                .fetch().map { it.convert() }
        }
    }

    fun count(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateCommonCondition
    ): Int {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.selectCount().from(this)
                .where(buildQueryCondition(commonCondition))
                .fetchOne(0, Int::class.java)!!
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

    fun getType2Count(
        dslContext: DSLContext,
        projectId: String
    ): Map<String, Int> {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.select(TYPE, DSL.count())
                .from(this)
                .where(PROJECT_ID.eq(projectId))
                .groupBy(TYPE)
                .fetch().map { Pair(it.value1(), it.value2()) }.toMap()
        }
    }

    fun getSource2count(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateCommonCondition
    ): Map<String, Int> {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.select(MODE, DSL.count())
                .from(this)
                .where(buildQueryCondition(commonCondition))
                .groupBy(MODE)
                .fetch().map { Pair(it.value1(), it.value2()) }.toMap()
        }
    }

    fun get(
        dslContext: DSLContext,
        templateId: String
    ): PipelineTemplateInfo? {
        return with(TPipelineTemplateInfo.T_PIPELINE_TEMPLATE_INFO) {
            dslContext.selectFrom(this)
                .where(ID.eq(templateId))
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
            commonCondition.checkAllFieldsAreNull()
            with(commonCondition) {
                val conditions = mutableListOf<Condition>()
                if (projectId != null) conditions.add(PROJECT_ID.eq(projectId))
                if (templateId != null) conditions.add(ID.eq(templateId))
                if (fuzzySearchName != null && fuzzySearchName!!.isNotBlank()) {
                    conditions.add(NAME.like("%$fuzzySearchName%"))
                }
                if (mode != null) conditions.add(MODE.eq(mode!!.name))
                if (desc != null && desc!!.isNotBlank()) conditions.add(DESC.like("%$desc%"))
                if (exactSearchName != null && exactSearchName!!.isNotBlank()) conditions.add(NAME.eq(exactSearchName))
                if (type != null) conditions.add(TYPE.eq(type!!.value))
                if (enablePac != null) conditions.add(PAC.eq(enablePac))
                if (releasedVersion != null) conditions.add(RELEASED_VERSION.eq(releasedVersion))
                if (releasedVersionName != null) conditions.add(RELEASED_VERSION_NAME.eq(releasedVersionName))
                if (releasedSettingVersion != null) conditions.add(RELEASED_SETTING_VERSION.eq(releasedSettingVersion))
                if (storeFlag != null) conditions.add(STORE_FLAG.eq(storeFlag))
                if (srcTemplateId != null) conditions.add(SRC_TEMPLATE_ID.eq(srcTemplateId))
                if (srcTemplateProjectId != null) conditions.add(SRC_TEMPLATE_PROJECT_ID.eq(srcTemplateProjectId))
                if (debugPipelineCount != null) conditions.add(DEBUG_PIPELINE_COUNT.eq(debugPipelineCount))
                if (instancePipelineCount != null) conditions.add(INSTANCE_PIPELINE_COUNT.eq(instancePipelineCount))
                if (creator != null) conditions.add(CREATOR.eq(creator))
                if (updater != null) conditions.add(UPDATER.eq(updater))
                if (!filterTemplateIds.isNullOrEmpty()) conditions.add(ID.`in`(filterTemplateIds))
                if (latestVersionStatus != null) conditions.add(LATEST_VERSION_STATUS.eq(latestVersionStatus!!.name))
                conditions
            }
        }
    }

    fun TPipelineTemplateInfoRecord.convert(): PipelineTemplateInfo {
        val mode = TemplateType.valueOf(this.mode)
        return PipelineTemplateInfo(
            id = this.id,
            projectId = this.projectId,
            name = this.name,
            desc = this.desc,
            mode = mode,
            upgradeStrategy = this.upgradeStrategy?.let { UpgradeStrategyEnum.valueOf(it) },
            settingSyncStrategy = this.settingSyncStrategy?.let { UpgradeStrategyEnum.valueOf(it) },
            sourceName = TemplateType.getDisplayName(mode),
            category = this.category,
            type = PipelineTemplateType.valueOf(this.type),
            logoUrl = this.logoUrl,
            enablePac = this.pac,
            releasedVersion = this.releasedVersion,
            releasedVersionName = releasedVersionName,
            releasedSettingVersion = this.releasedSettingVersion,
            latestVersionStatus = VersionStatus.get(this.latestVersionStatus),
            storeFlag = this.storeFlag,
            srcTemplateId = this.srcTemplateId,
            srcTemplateProjectId = this.srcTemplateProjectId,
            debugPipelineCount = this.debugPipelineCount,
            instancePipelineCount = this.instancePipelineCount,
            creator = this.creator,
            updater = this.updater,
            createdTime = this.createdTime.timestampmilli(),
            updateTime = this.updateTime.timestampmilli()
        )
    }
}
