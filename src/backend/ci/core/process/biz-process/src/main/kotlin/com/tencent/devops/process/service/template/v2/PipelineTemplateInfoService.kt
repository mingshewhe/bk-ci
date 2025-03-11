package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.process.dao.template.PipelineTemplateInfoDao
import com.tencent.devops.common.pipeline.template.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版基础信息类
 */
@Service
class PipelineTemplateInfoService @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineTemplateInfoDao: PipelineTemplateInfoDao
) {
    fun create(
        transactionContext: DSLContext? = null,
        pipelineTemplateInfo: PipelineTemplateInfo
    ) {
        pipelineTemplateInfoDao.create(
            dslContext = transactionContext ?: dslContext,
            record = pipelineTemplateInfo
        )
    }

    fun get(commonCondition: PipelineTemplateCommonCondition): PipelineTemplateInfo {
        return pipelineTemplateInfoDao.get(
            dslContext = dslContext,
            commonCondition = commonCondition
        ) ?: throw ErrorCodeException(
            errorCode = ""
        )
    }

    fun delete(
        transactionContext: DSLContext? = null,
        commonCondition: PipelineTemplateCommonCondition
    ) {
        pipelineTemplateInfoDao.delete(
            dslContext = transactionContext ?: dslContext,
            commonCondition = commonCondition
        )
    }

    fun list(commonCondition: PipelineTemplateCommonCondition): List<PipelineTemplateInfo> {
        return pipelineTemplateInfoDao.list(
            dslContext = dslContext,
            commonCondition = commonCondition
        )
    }
}
