package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.template.PipelineTemplateResource
import com.tencent.devops.common.pipeline.template.PipelineTemplateSetting
import com.tencent.devops.process.dao.template.PipelineTemplateSettingDao
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版设置相关类
 */
@Service
class PipelineTemplateSettingService @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineTemplateSettingDao: PipelineTemplateSettingDao
) {
    fun get(commonCondition: PipelineTemplateSettingCommonCondition): PipelineTemplateSetting {
        return pipelineTemplateSettingDao.get(
            commonCondition = commonCondition,
            dslContext = dslContext
        ) ?: throw ErrorCodeException(
            errorCode = ""
        )
    }

    fun delete(commonCondition: PipelineTemplateSettingCommonCondition) {
        pipelineTemplateSettingDao.get(
            dslContext = dslContext,
            commonCondition = commonCondition
        )
    }

    fun list(commonCondition: PipelineTemplateSettingCommonCondition): List<PipelineTemplateSetting> {
        return pipelineTemplateSettingDao.list(
            dslContext = dslContext,
            commonCondition = commonCondition
        )
    }

    fun create(
        transactionContext: DSLContext? = null,
        pipelineTemplateSetting: PipelineTemplateSetting
    ) {
        pipelineTemplateSettingDao.create(
            dslContext = transactionContext ?: dslContext,
            record = pipelineTemplateSetting
        )
    }
}
