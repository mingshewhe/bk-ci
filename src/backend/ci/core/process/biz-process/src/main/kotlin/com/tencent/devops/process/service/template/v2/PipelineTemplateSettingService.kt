package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingVersion
import com.tencent.devops.process.dao.template.PipelineTemplateSettingDao
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingUpdateInfo
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
    fun get(commonCondition: PipelineTemplateSettingCommonCondition): PipelineTemplateSettingVersion {
        return pipelineTemplateSettingDao.get(
            commonCondition = commonCondition,
            dslContext = dslContext
        ) ?: throw ErrorCodeException(
            errorCode = ""
        )
    }

    fun get(
        projectId: String,
        templateId: String,
        settingVersion: Int
    ): PipelineTemplateSettingVersion? {
        return pipelineTemplateSettingDao.get(
            commonCondition = PipelineTemplateSettingCommonCondition(
                projectId = projectId,
                templateId = templateId,
                settingVersion = settingVersion
            ),
            dslContext = dslContext
        )
    }

    fun delete(
        transactionContext: DSLContext? = null,
        commonCondition: PipelineTemplateSettingCommonCondition
    ) {
        pipelineTemplateSettingDao.get(
            dslContext = dslContext,
            commonCondition = commonCondition
        )
    }

    fun list(commonCondition: PipelineTemplateSettingCommonCondition): List<PipelineTemplateSettingVersion> {
        return pipelineTemplateSettingDao.list(
            dslContext = dslContext,
            commonCondition = commonCondition
        )
    }

    fun create(
        transactionContext: DSLContext? = null,
        pipelineTemplateSettingVersion: PipelineTemplateSettingVersion
    ) {
        pipelineTemplateSettingDao.create(
            dslContext = transactionContext ?: dslContext,
            record = pipelineTemplateSettingVersion
        )
    }

    fun update(
        transactionContext: DSLContext? = null,
        record: PipelineTemplateSettingUpdateInfo,
        commonCondition: PipelineTemplateSettingCommonCondition
    ) {
        pipelineTemplateSettingDao.update(
            dslContext = transactionContext ?: dslContext,
            record = record,
            commonCondition = commonCondition
        )
    }
}
