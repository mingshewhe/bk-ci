package com.tencent.devops.process.dao.template

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.BuildNo
import com.tencent.devops.model.process.tables.TTemplatePipeline
import com.tencent.devops.model.process.tables.records.TTemplatePipelineRecord
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelated
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedSample
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedUpdateInfo
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PipelineTemplateRelatedDao {
    fun create(
        dslContext: DSLContext,
        record: PipelineTemplateRelated
    ) {
        with(record) {
            with(TTemplatePipeline.T_TEMPLATE_PIPELINE) {
                val now = LocalDateTime.now()
                dslContext.insertInto(
                    this,
                    PROJECT_ID,
                    PIPELINE_ID,
                    INSTANCE_TYPE,
                    ROOT_TEMPLATE_ID,
                    VERSION,
                    VERSION_NAME,
                    TEMPLATE_ID,
                    CREATOR,
                    UPDATOR,
                    CREATED_TIME,
                    UPDATED_TIME,
                    BUILD_NO,
                    PARAM
                ).values(
                    projectId,
                    pipelineId,
                    instanceType.type,
                    rootTemplateId,
                    version,
                    versionName,
                    templateId,
                    creator,
                    updater,
                    now,
                    now,
                    buildNo?.let { JsonUtil.toJson(it) } ?: "",
                    params?.let { JsonUtil.toJson(it) } ?: ""
                ).execute()
            }
        }
    }

    fun list(
        dslContext: DSLContext,
        condition: PipelineTemplateRelatedCommonCondition
    ): List<PipelineTemplateRelated> {
        return with(TTemplatePipeline.T_TEMPLATE_PIPELINE) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(condition))
                .fetch().map { it.convert() }
        }
    }

    fun listSample(
        dslContext: DSLContext,
        condition: PipelineTemplateRelatedCommonCondition
    ): List<PipelineTemplateRelatedSample> {
        return with(TTemplatePipeline.T_TEMPLATE_PIPELINE) {
            dslContext.select(
                PROJECT_ID,
                TEMPLATE_ID,
                VERSION,
                VERSION_NAME,
                PIPELINE_ID,
                INSTANCE_TYPE
            ).from(this)
                .where(buildQueryCondition(condition))
                .fetch().map {
                    PipelineTemplateRelatedSample(
                        projectId = it.value1(),
                        templateId = it.value2(),
                        version = it.value3(),
                        versionName = it.value4(),
                        pipelineId = it.value5(),
                        instanceType = PipelineInstanceTypeEnum.get(it.value6())
                    )
                }
        }
    }

    fun delete(
        dslContext: DSLContext,
        condition: PipelineTemplateRelatedCommonCondition
    ) {
        return with(TTemplatePipeline.T_TEMPLATE_PIPELINE) {
            dslContext.deleteFrom(this)
                .where(buildQueryCondition(condition))
                .execute()
        }
    }

    fun get(
        dslContext: DSLContext,
        condition: PipelineTemplateRelatedCommonCondition
    ): PipelineTemplateRelated? {
        return with(TTemplatePipeline.T_TEMPLATE_PIPELINE) {
            dslContext.selectFrom(this)
                .where(buildQueryCondition(condition))
                .fetchOne()?.convert()
        }
    }

    fun count(
        dslContext: DSLContext,
        condition: PipelineTemplateRelatedCommonCondition
    ): Int {
        return with(TTemplatePipeline.T_TEMPLATE_PIPELINE) {
            dslContext.selectCount().from(this)
                .where(buildQueryCondition(condition))
                .fetchOne(0, Int::class.java)!!
        }
    }

    fun buildQueryCondition(condition: PipelineTemplateRelatedCommonCondition): List<Condition> {
        return with(TTemplatePipeline.T_TEMPLATE_PIPELINE) {
            with(condition) {
                val conditions = mutableListOf<Condition>()
                conditions.add(PROJECT_ID.eq(projectId))
                if (templateId != null) conditions.add(TEMPLATE_ID.eq(templateId))
                if (pipelineId != null) conditions.add(PIPELINE_ID.eq(pipelineId))
                if (version != null) conditions.add(VERSION.eq(version))
                if (versionName != null) conditions.add(VERSION_NAME.eq(versionName))
                if (instanceType != null) conditions.add(INSTANCE_TYPE.eq(instanceType!!.type))
                if (rootTemplateId != null) conditions.add(ROOT_TEMPLATE_ID.eq(rootTemplateId))
                if (deleted != null) conditions.add(DELETED.eq(deleted))
                if (creator != null) conditions.add(CREATOR.eq(creator))
                if (updater != null) conditions.add(UPDATOR.eq(updater))
                conditions
            }
        }
    }

    fun update(
        dslContext: DSLContext,
        updateInfo: PipelineTemplateRelatedUpdateInfo,
        condition: PipelineTemplateRelatedCommonCondition
    ) {
        with(TTemplatePipeline.T_TEMPLATE_PIPELINE) {
            dslContext.update(this)
                .apply {
                    if (!updateInfo.params.isNullOrEmpty()) set(PARAM, JsonUtil.toJson(updateInfo.params!!))
                    updateInfo.buildNo?.let { set(BUILD_NO, JsonUtil.toJson(it)) }
                    updateInfo.deleted?.let { set(DELETED, it) }
                    updateInfo.instanceErrorInfo?.let { set(INSTANCE_ERROR_INFO, it) }
                    updateInfo.updater?.let { set(UPDATOR, it) }
                }
                .set(UPDATED_TIME, LocalDateTime.now())
                .where(buildQueryCondition(condition))
                .execute()
        }
    }

    fun TTemplatePipelineRecord.convert(): PipelineTemplateRelated {
        return PipelineTemplateRelated(
            projectId = projectId,
            templateId = templateId,
            pipelineId = pipelineId,
            version = version,
            versionName = versionName,
            buildNo = buildNo?.let { JsonUtil.to(it, object : TypeReference<BuildNo>() {}) },
            params = param?.let { JsonUtil.to(it, object : TypeReference<List<BuildFormProperty>>() {}) },
            instanceType = PipelineInstanceTypeEnum.get(instanceType),
            rootTemplateId = rootTemplateId,
            deleted = deleted,
            instanceErrorInfo = instanceErrorInfo,
            createdTime = createdTime.timestampmilli(),
            updatedTime = updatedTime.timestampmilli(),
            creator = creator,
            updater = updator
        )
    }
}
