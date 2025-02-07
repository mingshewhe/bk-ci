package com.tencent.devops.process.dao.template

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.pipeline.enums.BranchVersionAction
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.model.process.tables.TPipelineTemplateResourceVersion
import com.tencent.devops.model.process.tables.records.TPipelineTemplateResourceVersionRecord
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
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
                DESCRIPTION,
                CREATOR,
                UPDATER,
                RELEASE_TIME
            ).values(
                record.projectId,
                record.templateId,
                record.type.value,
                record.version,
                record.number,
                record.versionName,
                record.versionNum,
                record.modelVersion,
                record.triggerVersion,
                record.draftSourceVersion,
                JsonUtil.toJson(record.params),
                JsonUtil.toJson(record.originalModel),
                JsonUtil.toJson(record.model),
                record.yaml,
                record.status.name,
                record.branchAction?.name,
                record.description,
                record.creator,
                record.updater,
                record.releaseTime
            ).execute()
        }
    }

    fun update(
        dslContext: DSLContext,
        record: TPipelineTemplateResourceVersionRecord
    ) {
        with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            val now = LocalDateTime.now()
            dslContext.update(this)
                .apply {
                    record.type?.let { set(TYPE, it) }
                    record.version?.let { set(VERSION, it) }
                    record.versionName?.let { set(VERSION_NAME, it) }
                    record.versionNum?.let { set(VERSION_NUM, it) }
                    record.modelVersion?.let { set(MODEL_VERSION, it) }
                    record.triggerVersion?.let { set(TRIGGER_VERSION, it) }
                    record.draftSourceVersion?.let { set(DRAFT_SOURCE_VERSION, it) }
                    record.model?.let { set(MODEL, it) }
                    record.yaml?.let { set(YAML, it) }
                    record.status?.let { set(STATUS, it) }
                    record.branchAction?.let { set(BRANCH_ACTION, it) }
                    record.description?.let { set(DESCRIPTION, it) }
                    record.releaseTime?.let { set(RELEASE_TIME, it) }
                    record.originalModel?.let { set(ORIGINAL_MODEL, it) }
                    record.params?.let { set(PARAMS, it) }
                }
                .set(UPDATER, record.updater)
                .set(UPDATE_TIME, now)
                .where(PROJECT_ID.eq(record.projectId))
                .and(TEMPLATE_ID.eq(record.templateId))
                .execute()
        }
    }

    fun list(
        dslContext: DSLContext,
        commonCondition: PipelineTemplateResourceCommonCondition,
        limit: Int? = null,
        offset: Int? = null
    ): List<PipelineTemplateResource> {
        return with(TPipelineTemplateResourceVersion.T_PIPELINE_TEMPLATE_RESOURCE_VERSION) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(commonCondition))
                .limit(limit)
                .offset(offset)
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
            description = this.description,
            creator = this.creator,
            updater = this.updater,
            releaseTime = this.releaseTime
        )
    }
}
