package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.process.dao.template.PipelineTemplateResourceDao
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版资源相关类
 */
@Service
class PipelineTemplateResourceService @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineTemplateResourceDao: PipelineTemplateResourceDao
) {

    fun getTemplateResourceVersion(
        templateId: String,
        version: Long
    ): PipelineTemplateResource? {
        TODO("")
    }

    fun getLatestTemplateResource(templateId: String): PipelineTemplateResource? {
        TODO("")
    }

    fun get(commonCondition: PipelineTemplateResourceCommonCondition): PipelineTemplateResource {
        return pipelineTemplateResourceDao.get(
            commonCondition = commonCondition,
            dslContext = dslContext
        ) ?: throw ErrorCodeException(
            errorCode = ""
        )
    }

    fun count(commonCondition: PipelineTemplateResourceCommonCondition): Int {
        return pipelineTemplateResourceDao.count(
            commonCondition = commonCondition,
            dslContext = dslContext
        )
    }

    fun delete(
        transactionContext: DSLContext? = null,
        commonCondition: PipelineTemplateResourceCommonCondition
    ) {
        pipelineTemplateResourceDao.get(
            dslContext = dslContext,
            commonCondition = commonCondition
        )
    }

    fun list(commonCondition: PipelineTemplateResourceCommonCondition): List<PipelineTemplateResource> {
        return pipelineTemplateResourceDao.list(
            dslContext = dslContext,
            commonCondition = commonCondition
        )
    }

    fun create(
        transactionContext: DSLContext? = null,
        pipelineTemplateResource: PipelineTemplateResource
    ) {
        pipelineTemplateResourceDao.create(
            dslContext = transactionContext ?: dslContext,
            record = pipelineTemplateResource
        )
    }
}
