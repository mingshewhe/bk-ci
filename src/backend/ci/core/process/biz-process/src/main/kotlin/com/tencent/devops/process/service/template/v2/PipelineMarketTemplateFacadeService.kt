package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.container.VMBuildContainer
import com.tencent.devops.common.pipeline.type.StoreDispatchType
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.dao.PipelineSettingDao
import com.tencent.devops.process.engine.dao.template.TemplateDao
import com.tencent.devops.process.pojo.template.MarketTemplateRequest
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoUpdateInfo
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingCommonCondition
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSettingUpdateInfo
import com.tencent.devops.store.api.image.ServiceStoreImageResource
import com.tencent.devops.store.pojo.image.enums.ImageStatusEnum
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.concurrent.Executors

/**
 * 流水线市场模版门面类
 */
@Service
class PipelineMarketTemplateFacadeService @Autowired constructor(
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val dslContext: DSLContext,
    private val templateDao: TemplateDao,
    private val pipelineSettingDao: PipelineSettingDao,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val client: Client
) {

    fun updateMarketTemplateReference(
        userId: String,
        projectId: String,
        updateMarketTemplateRequest: MarketTemplateRequest
    ): Boolean {
        logger.info("update market template reference:$userId|$projectId|$updateMarketTemplateRequest")
        with(updateMarketTemplateRequest) {
            val srcTemplateId = templateCode
            val category = JsonUtil.toJson(categoryCodeList ?: emptyList<String>(), false)
            // todo 待上线稳定后，从新表中获取
            val projectId2TemplateIdOfReference = templateDao.listTemplateReferenceId(
                dslContext = dslContext,
                templateId = srcTemplateId
            )
            val referenceList = projectId2TemplateIdOfReference.keys.toList()
            if (referenceList.isEmpty()) return true
            dslContext.transaction { configuration ->
                val transactionContext = DSL.using(configuration)
                // 修改老表
                pipelineSettingDao.updateSettingName(
                    dslContext = transactionContext,
                    pipelineIdList = referenceList,
                    name = templateName
                )
                templateDao.updateTemplateReference(
                    dslContext = transactionContext,
                    srcTemplateId = srcTemplateId,
                    name = templateName,
                    category = category,
                    logoUrl = logoUrl
                )
            }
            // 同步新表，将关联的数据表，进行批量刷数据
            updateMarketTemplateExecutorService.execute {
                projectId2TemplateIdOfReference.forEach { (projectId, templateId) ->
                    pipelineTemplateInfoService.update(
                        record = PipelineTemplateInfoUpdateInfo(
                            name = templateName,
                            category = category,
                            logoUrl = logoUrl
                        ),
                        commonCondition = PipelineTemplateCommonCondition(
                            projectId = projectId,
                            templateId = templateId
                        )
                    )
                    pipelineTemplateSettingService.update(
                        record = PipelineTemplateSettingUpdateInfo(
                            name = templateName
                        ),
                        commonCondition = PipelineTemplateSettingCommonCondition(
                            projectId = projectId,
                            templateId = templateId
                        )
                    )
                }
            }
        }
        return true
    }

    fun updateTemplateStoreFlag(
        userId: String,
        projectId: String,
        templateId: String,
        storeFlag: Boolean
    ): Boolean {
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            templateDao.updateStoreFlag(
                dslContext = dslContext,
                userId = userId,
                projectId = projectId,
                templateId = templateId,
                storeFlag = storeFlag
            )
            pipelineTemplateInfoService.update(
                transactionContext = context,
                record = PipelineTemplateInfoUpdateInfo(
                    storeFlag = storeFlag,
                    updater = userId
                ),
                commonCondition = PipelineTemplateCommonCondition(
                    projectId = projectId,
                    templateId = templateId,
                    storeFlag = storeFlag
                )
            )
        }
        return true
    }

    fun checkImageReleaseStatus(
        userId: String,
        projectId: String,
        templateId: String
    ): Result<String?> {
        logger.info("start checkImageReleaseStatus templateCode is:$projectId|$templateId")
        val templateModel = (pipelineTemplateResourceService.getLatestReleasedResource(
            projectId = projectId,
            templateId = templateId
        )?.model ?: return I18nUtil.generateResponseDataObject(
            CommonMessageCode.SYSTEM_ERROR,
            language = I18nUtil.getLanguage(userId)
        )) as Model
        var code: String? = null
        val images = mutableSetOf<String>()
        run releaseStatus@{
            templateModel.stages.forEach { stage ->
                stage.containers.forEach imageInfo@{ container ->
                    if (container is VMBuildContainer && container.dispatchType is StoreDispatchType) {
                        val imageCode = (container.dispatchType as StoreDispatchType).imageCode
                        val imageVersion = (container.dispatchType as StoreDispatchType).imageVersion
                        val image = imageCode + imageVersion
                        if (imageCode.isNullOrBlank() || imageVersion.isNullOrBlank()) {
                            return@imageInfo
                        } else {
                            if (images.contains(image)) {
                                return@imageInfo
                            } else {
                                images.add(image)
                            }
                            if (!isRelease(imageCode, imageVersion)) {
                                code = imageCode
                            }
                            return@releaseStatus
                        }
                    } else {
                        return@imageInfo
                    }
                }
            }
        }
        return Result(code)
    }

    private fun isRelease(imageCode: String, imageVersion: String): Boolean {
        val imageStatus = client.get(ServiceStoreImageResource::class)
            .getImageStatusByCodeAndVersion(imageCode, imageVersion).data
        return ImageStatusEnum.RELEASED.name == imageStatus
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineMarketTemplateFacadeService::class.java)
        private val updateMarketTemplateExecutorService = Executors.newFixedThreadPool(3)
    }
}
