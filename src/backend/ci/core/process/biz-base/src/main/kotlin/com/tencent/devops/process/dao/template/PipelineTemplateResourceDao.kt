package com.tencent.devops.process.dao.template

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.model.process.tables.TPipelineTemplateResourceVersion
import com.tencent.devops.model.process.tables.records.TPipelineTemplateResourceVersionRecord
import com.tencent.devops.process.pojo.setting.PipelineVersionSimple
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceUpdateInfo
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PipelineTemplateResourceDao {
    fun create(
        dslContext: DSLContext,
        record: PipelineTemplateResource
    ) {
        with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.insertInto(
                this,
                PROJECT_ID,
                TEMPLATE_ID,
                NAME,
                DESC,
                TYPE,
                SETTING_VERSION,
                VERSION,
                NUMBER,
                VERSION_NAME,
                VERSION_NUM,
                SRC_TEMPLATE_PROJECT_ID,
                SRC_TEMPLATE_ID,
                SRC_TEMPLATE_VERSION,
                MODEL_VERSION,
                TRIGGER_VERSION,
                BASE_VERSION,
                PARAMS,
                MODEL,
                YAML,
                STATUS,
                BRANCH_ACTION,
                RELEASE_COMMENT,
                SORT_WEIGHT,
                CREATOR,
                UPDATER,
                RELEASE_TIME
            ).values(
                record.projectId,
                record.templateId,
                record.name,
                record.desc,
                record.type.value,
                record.settingVersion,
                record.version,
                record.number,
                record.versionName,
                record.versionNum,
                record.srcTemplateProjectId,
                record.srcTemplateId,
                record.srcTemplateVersion,
                record.modelVersion,
                record.triggerVersion,
                record.baseVersion,
                record.params?.let { JsonUtil.toJson(it) },
                record.model?.let { JsonUtil.toJson(it) },
                record.yaml,
                record.status.name,
                record.branchAction?.name,
                record.releaseComment,
                record.sortWeight,
                record.creator,
                record.updater,
                record.releaseTime
            ).execute()
        }
    }

    fun update(
        dslContext: DSLContext,
        record: PipelineTemplateResourceUpdateInfo,
        commonCondition: PipelineTemplateResourceCommonCondition
    ) {
        with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            val now = LocalDateTime.now()
            dslContext.update(this)
                .apply {
                    record.name?.let { set(NAME, it) }
                    record.version?.let { set(VERSION, it) }
                    record.number?.let { set(NUMBER, it) }
                    record.versionName?.let { set(VERSION_NAME, it) }
                    record.versionNum?.let { set(VERSION_NUM, it) }
                    record.modelVersion?.let { set(MODEL_VERSION, it) }
                    record.triggerVersion?.let { set(TRIGGER_VERSION, it) }
                    record.baseVersion?.let { set(BASE_VERSION, it) }
                    record.params?.let { set(PARAMS, JsonUtil.toJson(it)) }
                    record.model?.let { set(MODEL, JsonUtil.toJson(it)) }
                    record.yaml?.let { set(YAML, it) }
                    record.status?.let { set(STATUS, it.name) }
                    record.branchAction?.let { set(BRANCH_ACTION, it.name) }
                    record.releaseComment?.let { set(RELEASE_COMMENT, it) }
                    record.releaseTime?.let { set(RELEASE_TIME, it) }
                }
                .set(UPDATER, record.updater)
                .set(UPDATE_TIME, now)
                .where(buildQueryCondition(commonCondition))
                .execute()
        }
    }

    fun list(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateResourceCommonCondition
    ): List<PipelineTemplateResource> {
        return with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(commonCondition))
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
        commonCondition: PipelineTemplateResourceCommonCondition
    ): Int {
        return with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.selectCount().from(this)
                .where(buildQueryCondition(commonCondition))
                .fetchOne(0, Int::class.java)!!
        }
    }

    fun get(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateResourceCommonCondition
    ): PipelineTemplateResource? {
        return with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(commonCondition))
                .fetchOne()?.convert()
        }
    }

    fun get(
        dslContext: DSLContext,
        templateId: String,
        version: Long
    ): PipelineTemplateResource? {
        return with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.selectFrom(this)
                .where(TEMPLATE_ID.eq(templateId))
                .and(VERSION.eq(version))
                .fetchOne()?.convert()
        }
    }

    fun getVersions(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateResourceCommonCondition
    ): List<PipelineVersionSimple> {
        return with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.select(
                TEMPLATE_ID,
                SETTING_VERSION,
                VERSION,
                VERSION_NAME,
                VERSION_NUM,
                MODEL_VERSION,
                TRIGGER_VERSION,
                BASE_VERSION,
                STATUS,
                RELEASE_COMMENT,
                CREATOR,
                UPDATER,
                CREATED_TIME,
                UPDATE_TIME
            ).from(this)
                .where(buildQueryCondition(commonCondition))
                .orderBy(SORT_WEIGHT.desc())
                .fetch()
                .map {
                    PipelineVersionSimple(
                        pipelineId = it.value1(),
                        settingVersion = it.value2(),
                        version = it.value3().toInt(),
                        versionName = it.value4() ?: "",
                        versionNum = it.value5(),
                        pipelineVersion = it.value6(),
                        triggerVersion = it.value7(),
                        baseVersion = it.value8()?.toInt(),
                        status = VersionStatus.get(it.value9()),
                        description = it.value10(),
                        creator = it.value11(),
                        updater = it.value12(),
                        createTime = it.value13().timestampmilli(),
                        updateTime = it.value14().timestampmilli(),
                        yamlVersion = null
                    )
                }
        }
    }

    fun getLatestRecord(
        dslContext: DSLContext,
        projectId: String,
        templateId: String,
        status: VersionStatus
    ): PipelineTemplateResource? {
        return with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.selectFrom(this)
                .where(PROJECT_ID.eq(projectId))
                .and(TEMPLATE_ID.eq(templateId))
                .and(STATUS.eq(status.name))
                .orderBy(NUMBER.desc())
                .fetchOne()?.convert()
        }
    }

    fun delete(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateResourceCommonCondition
    ) {
        with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.deleteFrom(this)
                .where(buildQueryCondition(commonCondition))
                .execute()
        }
    }

    fun buildQueryCondition(commonCondition: PipelineTemplateResourceCommonCondition): MutableList<Condition> {
        with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            with(commonCondition) {
                val conditions = mutableListOf<Condition>()
                conditions.add(PROJECT_ID.eq(projectId))
                if (templateId != null) conditions.add(TEMPLATE_ID.eq(templateId))
                if (name != null && name!!.isNotBlank()) conditions.add(NAME.eq(name))
                if (type != null) conditions.add(TYPE.eq(type!!.value))
                if (settingVersion != null) conditions.add(SETTING_VERSION.eq(settingVersion))
                if (version != null) conditions.add(VERSION.eq(version))
                if (versionName != null && versionName!!.isNotBlank()) conditions.add(VERSION_NAME.eq(versionName))
                if (versionNum != null) conditions.add(VERSION_NUM.eq(versionNum))
                if (modelVersion != null) conditions.add(MODEL_VERSION.eq(modelVersion))
                if (triggerVersion != null) conditions.add(TRIGGER_VERSION.eq(triggerVersion))
                if (baseVersion != null) conditions.add(BASE_VERSION.eq(baseVersion))
                if (status != null) conditions.add(STATUS.eq(status!!.name))
                if (branchAction != null) conditions.add(BRANCH_ACTION.eq(branchAction!!.name))
                if (creator != null) conditions.add(CREATOR.eq(creator))
                if (updater != null) conditions.add(UPDATER.eq(updater))
                if (releaseTime != null) conditions.add(RELEASE_TIME.eq(releaseTime))
                if (srcTemplateProjectId != null) conditions.add(SRC_TEMPLATE_PROJECT_ID.eq(srcTemplateProjectId))
                if (srcTemplateId != null) conditions.add(SRC_TEMPLATE_ID.eq(srcTemplateId))
                if (srcTemplateVersion != null) conditions.add(SRC_TEMPLATE_VERSION.eq(srcTemplateVersion))
                if (releaseComment != null) conditions.add(RELEASE_COMMENT.like("%$releaseComment%"))
                if (includeDraft == false) conditions.add(STATUS.notEqual(VersionStatus.COMMITTING.name))
                return conditions
            }
        }
    }

    fun TPipelineTemplateResourceVersionRecord.convert(): PipelineTemplateResource {
        return PipelineTemplateResource(
            projectId = this.projectId,
            templateId = this.templateId,
            name = this.name,
            desc = this.name,
            type = PipelineTemplateType.get(this.type),
            settingVersion = this.settingVersion,
            version = this.version,
            number = this.number,
            versionName = this.versionName,
            versionNum = this.versionNum,
            modelVersion = this.modelVersion,
            triggerVersion = this.triggerVersion,
            srcTemplateProjectId = this.srcTemplateProjectId,
            srcTemplateId = this.srcTemplateId,
            srcTemplateVersion = this.srcTemplateVersion,
            baseVersion = this.baseVersion,
            params = this.params?.let { JsonUtil.to(it, object : TypeReference<List<BuildFormProperty>>() {}) },
            model = this.model?.let { JsonUtil.to(this.model, ITemplateModel::class.java) },
            yaml = this.yaml,
            status = VersionStatus.get(this.status),
            branchAction = this.branchAction?.let { BranchVersionAction.get(it) },
            releaseComment = this.releaseComment,
            creator = this.creator,
            updater = this.updater,
            releaseTime = this.releaseTime,
            sortWeight = this.sortWeight
        )
    }
}
