package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.dao.template.PipelineTemplateResourceDao
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceUpdateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateVersionInfo
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
        return pipelineTemplateResourceDao.get(
            dslContext = dslContext,
            templateId = templateId,
            version = version
        ) ?: throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
    }

    fun getLatestTemplateResource(
        projectId: String,
        templateId: String
    ): PipelineTemplateResource {
        return pipelineTemplateResourceDao.getLatestRecord(
            dslContext = dslContext,
            projectId = projectId,
            templateId = templateId
        ) ?: throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
    }

    fun getTemplateVersions(
        commonCondition: PipelineTemplateResourceCommonCondition
    ): List<PipelineTemplateVersionInfo> {
        return pipelineTemplateResourceDao.getVersions(
            dslContext = dslContext,
            commonCondition = commonCondition
        )
    }

    fun get(commonCondition: PipelineTemplateResourceCommonCondition): PipelineTemplateResource {
        return pipelineTemplateResourceDao.get(
            commonCondition = commonCondition,
            dslContext = dslContext
        ) ?: throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
    }

    fun get(
        projectId: String,
        templateId: String,
        version: Long
    ): PipelineTemplateResource {
        return pipelineTemplateResourceDao.get(
            commonCondition = PipelineTemplateResourceCommonCondition(
                projectId = projectId,
                templateId = templateId,
                version = version
            ),
            dslContext = dslContext
        ) ?: throw ErrorCodeException(errorCode = ERROR_TEMPLATE_NOT_EXISTS)
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

    fun update(
        transactionContext: DSLContext? = null,
        record: PipelineTemplateResourceUpdateInfo,
        commonCondition: PipelineTemplateResourceCommonCondition
    ) {
        pipelineTemplateResourceDao.update(
            dslContext = transactionContext ?: dslContext,
            record = record,
            commonCondition = commonCondition
        )
    }
}
