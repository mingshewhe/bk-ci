package com.tencent.devops.process.dao.template

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.model.process.tables.TPipelineTemplateResourceVersion
import com.tencent.devops.model.process.tables.records.TPipelineTemplateResourceVersionRecord
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
                TYPE,
                SETTING_VERSION,
                VERSION,
                NUMBER,
                VERSION_NAME,
                VERSION_NUM,
                MODEL_VERSION,
                TRIGGER_VERSION,
                DRAFT_SOURCE_VERSION,
                PARAMS,
                ORIGINAL_MODEL,
                MODEL,
                YAML,
                STATUS,
                BRANCH_ACTION,
                RELEASE_COMMENT,
                CREATOR,
                UPDATER,
                RELEASE_TIME
            ).values(
                record.projectId,
                record.templateId,
                record.type.value,
                record.settingVersion,
                record.version,
                record.number,
                record.versionName,
                record.versionNum,
                record.modelVersion,
                record.triggerVersion,
                record.draftSourceVersion,
                record.params?.let { JsonUtil.toJson(it) },
                JsonUtil.toJson(record.originalModel),
                JsonUtil.toJson(record.model),
                record.yaml,
                record.status.name,
                record.branchAction?.name,
                record.releaseComment,
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
                    record.version?.let { set(VERSION, it) }
                    record.number?.let { set(NUMBER, it) }
                    record.versionName?.let { set(VERSION_NAME, it) }
                    record.versionNum?.let { set(VERSION_NUM, it) }
                    record.modelVersion?.let { set(MODEL_VERSION, it) }
                    record.triggerVersion?.let { set(TRIGGER_VERSION, it) }
                    record.draftSourceVersion?.let { set(DRAFT_SOURCE_VERSION, it) }
                    record.params?.let { set(PARAMS, JsonUtil.toJson(it)) }
                    record.originalModel?.let { set(ORIGINAL_MODEL, JsonUtil.toJson(it)) }
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

    fun getLatestRecord(
        dslContext: DSLContext,
        projectId: String,
        templateId: String
    ): PipelineTemplateResource? {
        return with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.selectFrom(this)
                .where(PROJECT_ID.eq(projectId))
                .and(TEMPLATE_ID.eq(templateId))
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
                if (type != null) conditions.add(TYPE.eq(type!!.value))
                if (settingVersion != null) conditions.add(SETTING_VERSION.eq(settingVersion))
                if (version != null) conditions.add(VERSION.eq(version))
                if (versionName != null) conditions.add(VERSION_NAME.eq(versionName))
                if (versionNum != null) conditions.add(VERSION_NUM.eq(versionNum))
                if (modelVersion != null) conditions.add(MODEL_VERSION.eq(modelVersion))
                if (triggerVersion != null) conditions.add(TRIGGER_VERSION.eq(triggerVersion))
                if (draftSourceVersion != null) conditions.add(DRAFT_SOURCE_VERSION.eq(draftSourceVersion))
                if (status != null) conditions.add(STATUS.eq(status!!.name))
                if (branchAction != null) conditions.add(BRANCH_ACTION.eq(branchAction!!.name))
                if (creator != null) conditions.add(CREATOR.eq(creator))
                if (updater != null) conditions.add(UPDATER.eq(updater))
                if (releaseTime != null) conditions.add(RELEASE_TIME.eq(releaseTime))
                return conditions
            }
        }
    }

    fun TPipelineTemplateResourceVersionRecord.convert(): PipelineTemplateResource {
        return PipelineTemplateResource(
            projectId = this.projectId,
            templateId = this.templateId,
            type = PipelineTemplateType.get(this.type),
            settingVersion = this.settingVersion,
            version = this.version,
            number = this.number,
            versionName = this.versionName,
            versionNum = this.versionNum,
            modelVersion = this.modelVersion,
            triggerVersion = this.triggerVersion,
            draftSourceVersion = this.draftSourceVersion,
            params = JsonUtil.to(this.params, object : TypeReference<List<BuildFormProperty>>() {}),
            originalModel = JsonUtil.to(this.originalModel, ITemplateModel::class.java),
            model = JsonUtil.to(this.model, ITemplateModel::class.java),
            yaml = this.yaml,
            status = VersionStatus.get(this.status),
            branchAction = this.branchAction?.let { BranchVersionAction.get(it) },
            releaseComment = this.releaseComment,
            creator = this.creator,
            updater = this.updater,
            releaseTime = this.releaseTime
        )
    }
}
