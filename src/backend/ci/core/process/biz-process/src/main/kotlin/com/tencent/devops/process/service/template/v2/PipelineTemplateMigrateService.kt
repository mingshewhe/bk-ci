/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.auth.api.pojo.ProjectConditionDTO
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.PipelineStorageType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.template.PipelineTemplateType
import com.tencent.devops.common.pipeline.template.UpgradeStrategyEnum
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.trace.TraceTag
import com.tencent.devops.model.process.tables.records.TTemplateRecord
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.dao.PipelineSettingDao
import com.tencent.devops.process.engine.dao.template.TemplateDao
import com.tencent.devops.process.engine.dao.template.TemplatePipelineDao
import com.tencent.devops.process.pojo.template.TemplateType
import com.tencent.devops.process.pojo.template.TemplateVersion
import com.tencent.devops.process.pojo.template.v2.PTemplateModelTransferResult
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateInfoV2
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResourceCommonCondition
import com.tencent.devops.process.service.template.TemplateFacadeService
import com.tencent.devops.process.utils.PipelineVersionUtils
import com.tencent.devops.project.api.service.ServiceProjectResource
import com.tencent.devops.store.api.template.ServiceTemplateResource
import com.tencent.devops.store.pojo.template.TemplateVersionInstallHistoryInfo
import com.tencent.devops.store.pojo.template.TemplateVersionRelationInfo
import com.tencent.devops.store.pojo.template.enums.TemplateStatusEnum
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import java.util.concurrent.Executors

@Service
class PipelineTemplateMigrateService(
    val templateDao: TemplateDao,
    val dslContext: DSLContext,
    val templateFacadeService: TemplateFacadeService,
    val pipelineTemplatePersistenceService: PipelineTemplatePersistenceService,
    val pipelineSettingDao: PipelineSettingDao,
    val pipelineTemplateGenerator: PipelineTemplateGenerator,
    val pipelineTemplateResourceService: PipelineTemplateResourceService,
    val pipelineTemplateInfoService: PipelineTemplateInfoService,
    val templatePipelineDao: TemplatePipelineDao,
    val redisOperation: RedisOperation,
    val client: Client
) {
    fun migrateTemplatesByCondition(projectConditionDTO: ProjectConditionDTO) {
        logger.info("start to migrate Templates by condition|$projectConditionDTO")
        val traceId = MDC.get(TraceTag.BIZID)
        var offset = 0
        val limit = PageUtil.MAX_PAGE_SIZE / 2
        do {
            val projectCodes = client.get(ServiceProjectResource::class).listProjectsByCondition(
                projectConditionDTO = projectConditionDTO,
                limit = limit,
                offset = offset
            ).data ?: break
            projectCodes.forEach {
                migrateProjectTemplateExecutorService.execute {
                    MDC.put(TraceTag.BIZID, traceId)
                    migrateTemplates(it.englishName)
                }
            }
            offset += limit
        } while (projectCodes.size == limit)
    }


    fun migrateTemplates(projectId: String) {
        logger.info("start to migrate project templates,{}", projectId)
        var offset = 0
        val limit = PageUtil.MAX_PAGE_SIZE / 2
        val v1AllTemplateIds = mutableListOf<String>()
        do {
            val templateIds = templateDao.list(
                dslContext = dslContext,
                projectId = projectId,
                limit = limit,
                offset = offset
            )
            logger.info("migrate project templates->{}", templateIds)
            templateIds.forEach { templateId ->
                try {
                    migrateTemplate(
                        templateId = templateId,
                        projectId = projectId
                    )
                } catch (ex: Exception) {
                    logger.warn("migrate template failed $projectId|$templateId|$ex")
                }
            }
            v1AllTemplateIds.addAll(templateIds)
            offset += limit
        } while (templateIds.size == limit)
        migratePostProcess(
            projectId = projectId,
            v1AllTemplateIds = v1AllTemplateIds
        )
    }

    private fun migratePostProcess(
        projectId: String,
        v1AllTemplateIds: List<String>
    ) {
        val v2AllTemplateIds = pipelineTemplateInfoService.listAllIds(projectId)
        val deleteRecords = v2AllTemplateIds.filterNot { it in v1AllTemplateIds }
        deleteRecords.forEach {
            pipelineTemplatePersistenceService.deleteTemplateAllVersions(
                projectId = projectId,
                templateId = it
            )
        }
    }

    fun asyncMigrateTemplate(templateId: String, projectId: String) {
        migrateTemplateExecutorService.execute {
            migrateTemplate(
                projectId = projectId,
                templateId = templateId
            )
        }
    }

    fun migrateTemplate(templateId: String, projectId: String) {
        val lock = PipelineTemplateModelLock(redisOperation = redisOperation, templateId = templateId)
        try {
            lock.lock()
            logger.info("migrate template,{}|{}", projectId, templateId)
            val latestTemplate = templateDao.getLatestTemplate(
                dslContext = dslContext,
                projectId = projectId,
                templateId = templateId
            )
            logger.debug("migrate template latestTemplate {}", latestTemplate)
            val setting = pipelineSettingDao.getSetting(
                dslContext = dslContext,
                projectId = projectId,
                pipelineId = latestTemplate.id
            ) ?: throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
            )
            logger.debug("migrate template setting {}", setting)

            val (srcTemplateProjectId, templateVersionInfos) = getTemplateVersions(latestTemplate = latestTemplate)
            logger.info(
                "migrate template srcTemplateProjectId {},templateVersionInfos{}",
                srcTemplateProjectId, templateVersionInfos
            )
            val marketTemplateStatus = client.get(ServiceTemplateResource::class).getMarketTemplateStatus(
                templateCode = templateId
            ).data!!

            var versionSequence = 0
            var pipelineVersion = 0
            var triggerVersion = 0

            templateVersionInfos.forEachIndexed { index, templateVersionInfo ->
                versionSequence += 1
                val currentSetting = setting.copy(
                    version = versionSequence,
                    creator = templateVersionInfo.creator,
                    createdTime = latestTemplate.createdTime.timestampmilli(),
                    updateTime = latestTemplate.updateTime.timestampmilli()
                )
                // 当前实际模板，可能为当前模板的版本或父模板版本
                val currentProjectId = srcTemplateProjectId ?: projectId
                val currentTemplate = templateDao.getTemplate(
                    dslContext = dslContext,
                    projectId = currentProjectId,
                    version = templateVersionInfo.version
                ) ?: throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
                )

                val currentTemplateModel = JsonUtil.to(currentTemplate.template, Model::class.java)
                val currentTemplateParams = currentTemplateModel.getTriggerContainer().params

                // 计算获取获取版本信息
                if (index == 0) {
                    pipelineVersion = 1
                    triggerVersion = 1
                } else {
                    // 上一个版本的模板
                    val previousVersionTemplate = templateDao.getTemplate(
                        dslContext = dslContext,
                        projectId = currentProjectId,
                        version = templateVersionInfos[index - 1].version
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
                    )

                    val previousVersionTemplateModel = JsonUtil.to(previousVersionTemplate.template, Model::class.java)
                    val previousVersionTemplateParams = previousVersionTemplateModel.getTriggerContainer().params

                    pipelineVersion = PipelineVersionUtils.getPipelineVersion(
                        currVersion = pipelineVersion,
                        originTemplateModel = previousVersionTemplateModel,
                        newTemplateModel = currentTemplateModel,
                        originParams = previousVersionTemplateParams,
                        newParams = currentTemplateParams
                    )

                    triggerVersion = PipelineVersionUtils.getTriggerVersion(
                        currVersion = triggerVersion,
                        originModel = previousVersionTemplateModel,
                        newModel = currentTemplateModel
                    )
                }

                logger.debug("model Transfer model: {} ", JsonUtil.toJson(currentTemplateModel))
                logger.debug("model Transfer setting: {}", JsonUtil.toJson(currentSetting))
                val modelTransferResult = try {
                    pipelineTemplateGenerator.transfer(
                        userId = latestTemplate.creator,
                        projectId = latestTemplate.projectId,
                        storageType = PipelineStorageType.MODEL,
                        templateType = PipelineTemplateType.PIPELINE,
                        templateModel = currentTemplateModel,
                        templateSetting = currentSetting,
                        params = currentTemplateParams,
                        yaml = null
                    )
                } catch (ex: Exception) {
                    logger.warn("model Transfer failed:{}", ex.toString())
                    PTemplateModelTransferResult(
                        templateType = PipelineTemplateType.PIPELINE,
                        templateModel = currentTemplateModel,
                        templateSetting = currentSetting,
                        yamlWithVersion = null
                    )
                }

                val pipelineTemplateResource = createPipelineTemplateResource(
                    latestTemplate = latestTemplate,
                    currentTemplate = currentTemplate,
                    seq = versionSequence,
                    pipelineVersion = pipelineVersion,
                    triggerVersion = triggerVersion,
                    params = currentTemplateParams,
                    modelTransferResult = modelTransferResult,
                    marketTemplateStatus = marketTemplateStatus
                )

                pipelineTemplatePersistenceService.createReleaseVersion(
                    userId = templateVersionInfo.creator,
                    templateResource = pipelineTemplateResource,
                    templateSetting = currentSetting,
                    syncPermission = false
                )

                // 如果该模板是从研发商店安装的，需要记录其安装的版本历史
                if (latestTemplate.type == TemplateType.CONSTRAINT.name) {
                    val srcTemplateResource = templateDao.getTemplate(
                        dslContext = dslContext,
                        projectId = pipelineTemplateResource.srcTemplateProjectId!!,
                        version = pipelineTemplateResource.srcTemplateVersion!!
                    ) ?: throw ErrorCodeException(
                        errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
                    )

                    client.get(ServiceTemplateResource::class).createTemplateVersionInstallHistory(
                        TemplateVersionInstallHistoryInfo(
                            srcMarketTemplateProjectCode = srcTemplateResource.projectId,
                            srcMarketTemplateCode = srcTemplateResource.id,
                            projectCode = latestTemplate.projectId,
                            templateCode = latestTemplate.id,
                            version = srcTemplateResource.version,
                            versionName = srcTemplateResource.versionName,
                            createTime = pipelineTemplateResource.releaseTime
                        )
                    )
                }

                // 如果该模板已经上架至研发商店，需要记录发布的版本历史
                if (marketTemplateStatus == TemplateStatusEnum.RELEASED ||
                    marketTemplateStatus == TemplateStatusEnum.UNDERCARRIAGED) {
                    client.get(ServiceTemplateResource::class).createTemplateVersionRel(
                        TemplateVersionRelationInfo(
                            projectCode = pipelineTemplateResource.projectId,
                            templateCode = templateId,
                            version = pipelineTemplateResource.version,
                            versionName = pipelineTemplateResource.versionName!!,
                            number = pipelineTemplateResource.number,
                            published = marketTemplateStatus == TemplateStatusEnum.RELEASED,
                            creator = templateVersionInfo.creator,
                            updater = templateVersionInfo.creator,
                            createTime = pipelineTemplateResource.createdTime,
                            updateTime = pipelineTemplateResource.updateTime
                        )
                    )
                }
            }
            pipelineTemplateInfoService.createOrUpdate(
                pipelineTemplateInfo = createPipelineTemplateInfo(
                    marketTemplateStatus = marketTemplateStatus,
                    latestTemplate = latestTemplate
                )
            )

            val isConstraint = latestTemplate.type == TemplateType.CONSTRAINT.name
            // 防止生产已经删除版本，但新数据库表还未删除，导致的脏数据
            val v1TemplateVersions = templateVersionInfos.map { it.version }
            val v2TemplateVersions = pipelineTemplateResourceService.getTemplateVersions(
                PipelineTemplateResourceCommonCondition(
                    projectId = projectId,
                    templateId = templateId,
                    status = VersionStatus.RELEASED
                )
            )
            val deletedRecords = v2TemplateVersions.mapNotNull { resource ->
                (if (isConstraint) resource.srcTemplateVersion else resource.version)?.toLong()
            }.filterNot { it in v1TemplateVersions }.takeIf { it.isNotEmpty() }

            deletedRecords?.let {
                logger.info("template versions need to be deleted :$it")
                pipelineTemplateResourceService.delete(
                    commonCondition = PipelineTemplateResourceCommonCondition(
                        projectId = projectId,
                        templateId = templateId,
                        srcTemplateVersions = if (isConstraint) it else null,
                        versions = if (isConstraint) null else it,
                        status = VersionStatus.RELEASED
                    )
                )
            }
        } finally {
            lock.unlock()
        }
    }

    fun getTemplateVersions(
        latestTemplate: TTemplateRecord
    ): Pair<String?/*srcTemplateProjectId*/, List<TemplateVersion>> {
        return if (latestTemplate.type == TemplateType.CONSTRAINT.name) {
            val srcLatestTemplate = templateDao.getLatestTemplate(
                dslContext = dslContext,
                templateId = latestTemplate.srcTemplateId
            )
            Pair(
                first = srcLatestTemplate.projectId,
                second = templateFacadeService.listTemplateVersions(
                    projectId = srcLatestTemplate.projectId,
                    templateId = srcLatestTemplate.id
                )
            )
        } else {
            Pair(
                first = null,
                second = templateFacadeService.listTemplateVersions(
                    projectId = latestTemplate.projectId,
                    templateId = latestTemplate.id
                )
            )

        }
    }

    fun createPipelineTemplateResource(
        latestTemplate: TTemplateRecord,
        currentTemplate: TTemplateRecord,
        params: List<BuildFormProperty>,
        modelTransferResult: PTemplateModelTransferResult,
        seq: Int,
        pipelineVersion: Int,
        triggerVersion: Int,
        marketTemplateStatus: TemplateStatusEnum
    ): PipelineTemplateResource {
        val isConstraint = latestTemplate.type == TemplateType.CONSTRAINT.name
        val (srcTemplateProjectId, srcTemplateVersion, srcTemplateId) =
            currentTemplate.takeIf { isConstraint }?.let {
                Triple(it.projectId, it.version, it.id)
            } ?: Triple(null, null, null)

        val storeFlag = !isConstraint && marketTemplateStatus == TemplateStatusEnum.RELEASED
        val version = if (isConstraint) {
            pipelineTemplateResourceService.getOrNull(
                commonCondition = PipelineTemplateResourceCommonCondition(
                    projectId = latestTemplate.projectId,
                    templateId = latestTemplate.id,
                    srcTemplateVersion = srcTemplateVersion
                )
            )?.version ?: pipelineTemplateGenerator.generateTemplateVersion()
        } else {
            currentTemplate.version
        }

        return PipelineTemplateResource(
            projectId = latestTemplate.projectId,
            templateId = latestTemplate.id,
            type = PipelineTemplateType.PIPELINE,
            settingVersion = seq,
            version = version,
            storeFlag = storeFlag,
            number = seq,
            versionName = currentTemplate.versionName,
            versionNum = seq,
            settingVersionNum = seq,
            pipelineVersion = pipelineVersion,
            triggerVersion = triggerVersion,
            srcTemplateProjectId = srcTemplateProjectId,
            srcTemplateId = srcTemplateId,
            srcTemplateVersion = srcTemplateVersion,
            params = params,
            model = modelTransferResult.templateModel,
            yaml = modelTransferResult.yamlWithVersion?.yamlStr,
            status = VersionStatus.RELEASED,
            description = currentTemplate.desc,
            sortWeight = 0,
            creator = latestTemplate.creator,
            updater = latestTemplate.creator,
            releaseTime = (currentTemplate.updateTime ?: currentTemplate.createdTime).timestampmilli(),
            createdTime = currentTemplate.createdTime.timestampmilli(),
            updateTime = currentTemplate.updateTime.timestampmilli(),
        )
    }

    fun createPipelineTemplateInfo(
        marketTemplateStatus: TemplateStatusEnum,
        latestTemplate: TTemplateRecord
    ): PipelineTemplateInfoV2 {
        val latestReleasedResource = pipelineTemplateResourceService.getLatestReleasedResource(
            projectId = latestTemplate.projectId,
            templateId = latestTemplate.id
        ) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_TEMPLATE_NOT_EXISTS
        )
        val instanceSize = templatePipelineDao.countByVersionFeat(
            dslContext = dslContext,
            projectId = latestTemplate.projectId,
            templateId = latestTemplate.id,
            instanceType = TemplateType.CONSTRAINT.name
        )
        logger.info("template instance count {}|{}|{}", latestTemplate.projectId, latestTemplate.id, instanceSize)
        val isConstraint = latestTemplate.type == TemplateType.CONSTRAINT.name
        // 新版本中，storeFlag表示为是否已经上架研发商店。关联和下架模板storeFlag都为false
        val storeFlag = !isConstraint && marketTemplateStatus == TemplateStatusEnum.RELEASED
        // 如果模板已经上传研发商店，发布策略默认为自动
        val publishStrategy = if (storeFlag) UpgradeStrategyEnum.AUTO else null
        val strategy = if (isConstraint) UpgradeStrategyEnum.AUTO else null
        return PipelineTemplateInfoV2(
            id = latestTemplate.id,
            projectId = latestTemplate.projectId,
            name = latestTemplate.templateName,
            desc = latestTemplate.desc,
            mode = TemplateType.valueOf(latestTemplate.type),
            category = latestTemplate.category,
            type = PipelineTemplateType.PIPELINE,
            logoUrl = latestTemplate.logoUrl,
            enablePac = false,
            releasedVersion = latestReleasedResource.version,
            releasedVersionName = latestReleasedResource.versionName,
            releasedSettingVersion = latestReleasedResource.settingVersion,
            latestVersionStatus = VersionStatus.RELEASED,
            storeFlag = storeFlag,
            srcTemplateId = latestReleasedResource.srcTemplateId,
            srcTemplateProjectId = latestReleasedResource.srcTemplateProjectId,
            instancePipelineCount = instanceSize,
            publishStrategy = publishStrategy,
            upgradeStrategy = strategy,
            settingSyncStrategy = strategy,
            creator = latestTemplate.creator,
            updater = latestTemplate.creator,
            createdTime = latestTemplate.createdTime.timestampmilli(),
            updateTime = latestTemplate.updateTime.timestampmilli()
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineTemplateMigrateService::class.java)
        private val migrateTemplateExecutorService = Executors.newFixedThreadPool(5)
        private val migrateProjectTemplateExecutorService = Executors.newFixedThreadPool(5)
    }
}
