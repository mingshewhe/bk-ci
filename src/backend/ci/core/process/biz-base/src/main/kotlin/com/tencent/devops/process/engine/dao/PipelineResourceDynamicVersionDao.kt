package com.tencent.devops.process.engine.dao

import com.tencent.devops.model.process.Tables.T_PIPELINE_RESOURCE_DYNAMIC_VERSION
import com.tencent.devops.model.process.Tables.T_PIPELINE_RESOURCE_VERSION
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PipelineResourceDynamicVersionDao {

    fun exists(
        dslContext: DSLContext,
        projectId: String,
        dynamicVersion: String
    ): Boolean {
        with(T_PIPELINE_RESOURCE_DYNAMIC_VERSION) {
            return dslContext.fetchExists(
                dslContext.selectOne().from(this)
                    .where(DYNAMIC_VERSION.eq(dynamicVersion))
                    .and(PROJECT_ID.eq(projectId))
            )
        }
    }

    /**
     * 动态版本号由模型内容决定,并发写入时以已存在的记录为准
     */
    fun createIgnore(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String,
        version: Int,
        dynamicVersion: String,
        model: String
    ): Int {
        with(T_PIPELINE_RESOURCE_DYNAMIC_VERSION) {
            return dslContext.insertInto(
                this,
                DYNAMIC_VERSION,
                PROJECT_ID,
                PIPELINE_ID,
                VERSION,
                MODEL,
                CREATE_TIME
            ).values(
                dynamicVersion,
                projectId,
                pipelineId,
                version,
                model,
                LocalDateTime.now()
            ).onDuplicateKeyIgnore()
                .execute()
        }
    }

    fun getModelString(
        dslContext: DSLContext,
        projectId: String,
        dynamicVersion: String
    ): String? {
        with(T_PIPELINE_RESOURCE_DYNAMIC_VERSION) {
            return dslContext.select(MODEL).from(this)
                .where(DYNAMIC_VERSION.eq(dynamicVersion))
                .and(PROJECT_ID.eq(projectId))
                .fetchOne(0, String::class.java)
        }
    }

    /**
     * 清理流水线版本已被清理掉的动态版本,仍被构建引用的流水线版本保留
     */
    fun deleteEarlyVersion(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String,
        currentVersion: Int,
        maxPipelineResNum: Int
    ): Int {
        val dynamic = T_PIPELINE_RESOURCE_DYNAMIC_VERSION
        val resource = T_PIPELINE_RESOURCE_VERSION
        return dslContext.deleteFrom(dynamic)
            .where(dynamic.PROJECT_ID.eq(projectId))
            .and(dynamic.PIPELINE_ID.eq(pipelineId))
            .and(dynamic.VERSION.le(currentVersion - maxPipelineResNum))
            .andNotExists(
                DSL.selectOne().from(resource)
                    .where(resource.PROJECT_ID.eq(projectId))
                    .and(resource.PIPELINE_ID.eq(pipelineId))
                    .and(resource.VERSION.eq(dynamic.VERSION))
            )
            .execute()
    }

    fun deleteAllVersion(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String
    ): Int {
        with(T_PIPELINE_RESOURCE_DYNAMIC_VERSION) {
            return dslContext.deleteFrom(this)
                .where(PROJECT_ID.eq(projectId))
                .and(PIPELINE_ID.eq(pipelineId))
                .execute()
        }
    }
}
