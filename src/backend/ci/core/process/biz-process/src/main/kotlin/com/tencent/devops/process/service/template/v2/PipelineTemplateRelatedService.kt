package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.enums.PipelineInstanceTypeEnum
import com.tencent.devops.process.dao.template.PipelineTemplateRelatedDao
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelated
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateRelatedUpdateInfo
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版基础信息类
 */
@Service
class PipelineTemplateRelatedService @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineTemplateRelatedDao: PipelineTemplateRelatedDao
) {
    fun create(
        transactionContext: DSLContext? = null,
        pipelineTemplateRelated: PipelineTemplateRelated
    ) {
        pipelineTemplateRelatedDao.create(
            dslContext = transactionContext ?: dslContext,
            record = pipelineTemplateRelated
        )
    }

    fun get(condition: PipelineTemplateRelatedCommonCondition): PipelineTemplateRelated {
        return pipelineTemplateRelatedDao.get(
            dslContext = dslContext,
            condition = condition
        ) ?: throw ErrorCodeException(
            errorCode = ""
        )
    }

    fun count(condition: PipelineTemplateRelatedCommonCondition): Int {
        return pipelineTemplateRelatedDao.count(
            dslContext = dslContext,
            condition = condition
        )
    }

    fun delete(
        transactionContext: DSLContext? = null,
        condition: PipelineTemplateRelatedCommonCondition
    ) {
        pipelineTemplateRelatedDao.delete(
            dslContext = transactionContext ?: dslContext,
            condition = condition
        )
    }

    fun list(condition: PipelineTemplateRelatedCommonCondition): List<PipelineTemplateRelated> {
        return pipelineTemplateRelatedDao.list(
            dslContext = dslContext,
            condition = condition
        )
    }

    fun update(
        transactionContext: DSLContext? = null,
        updateInfo: PipelineTemplateRelatedUpdateInfo,
        condition: PipelineTemplateRelatedCommonCondition
    ) {
        pipelineTemplateRelatedDao.update(
            dslContext = transactionContext ?: dslContext,
            condition = condition,
            updateInfo = updateInfo
        )
    }

    fun isTemplateExistInstances(
        projectId: String,
        templateId: String
    ): Boolean {
        return count(
            condition = PipelineTemplateRelatedCommonCondition(
                projectId = projectId,
                templateId = templateId,
                instanceType = PipelineInstanceTypeEnum.CONSTRAINT
            )
        ) > 0
    }
}
