package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.process.pojo.enums.PipelineTemplateType
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
import com.tencent.devops.process.dao.template.PipelineTemplateInfoDao
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoUpdateInfo
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
            errorCode = ERROR_TEMPLATE_NOT_EXISTS
        )
    }

    fun get(templateId: String): PipelineTemplateInfo {
        return pipelineTemplateInfoDao.get(
            dslContext = dslContext,
            templateId = templateId
        ) ?: throw ErrorCodeException(
            errorCode = ERROR_TEMPLATE_NOT_EXISTS
        )
    }

    fun get(
        projectId: String,
        templateId: String
    ): PipelineTemplateInfo {
        return pipelineTemplateInfoDao.get(
            dslContext = dslContext,
            commonCondition = PipelineTemplateCommonCondition(
                projectId = projectId,
                templateId = templateId
            )
        ) ?: throw ErrorCodeException(
            errorCode = ERROR_TEMPLATE_NOT_EXISTS
        )
    }

    fun getOrNull(
        projectId: String,
        templateId: String
    ): PipelineTemplateInfo? {
        return pipelineTemplateInfoDao.get(
            dslContext = dslContext,
            commonCondition = PipelineTemplateCommonCondition(
                projectId = projectId,
                templateId = templateId
            )
        )
    }

    fun getType2Count(projectId: String): Map<String, Int> {
        val type2Count = pipelineTemplateInfoDao.getType2Count(
            dslContext = dslContext,
            projectId = projectId
        ).toMutableMap()
        val totalCount = count(PipelineTemplateCommonCondition(projectId = projectId))
        type2Count[PipelineTemplateType.All.value] = totalCount
        return type2Count
    }

    fun count(commonCondition: PipelineTemplateCommonCondition): Int {
        return pipelineTemplateInfoDao.count(
            dslContext = dslContext,
            commonCondition = commonCondition
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

    fun update(
        transactionContext: DSLContext? = null,
        record: PipelineTemplateInfoUpdateInfo,
        commonCondition: PipelineTemplateCommonCondition
    ) {
        pipelineTemplateInfoDao.update(
            dslContext = transactionContext ?: dslContext,
            commonCondition = commonCondition,
            record = record
        )
    }
}
