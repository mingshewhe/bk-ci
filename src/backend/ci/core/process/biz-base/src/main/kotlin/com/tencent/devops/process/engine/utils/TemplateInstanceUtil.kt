package com.tencent.devops.process.engine.utils

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.TemplateField
import com.tencent.devops.common.pipeline.container.Stage
import com.tencent.devops.common.pipeline.container.TriggerContainer
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.BuildNo
import com.tencent.devops.common.pipeline.pojo.TemplateInstanceRecommendedVersion
import com.tencent.devops.common.pipeline.pojo.TemplateInstanceTriggerConfig
import com.tencent.devops.common.pipeline.pojo.TemplateVariable
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.trigger.TimerTriggerElement
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSettingGroupType
import com.tencent.devops.process.constant.ProcessTemplateMessageCode
import com.tencent.devops.process.engine.utils.PipelineUtils.getFixedStages
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateResource
import com.tencent.devops.process.utils.FIXVERSION
import com.tencent.devops.process.utils.MAJORVERSION
import com.tencent.devops.process.utils.MINORVERSION

/**
 *  模板实例工具类
 */
object TemplateInstanceUtil {
    /**
     * 通过流水线参数、模板编排和触发器控制生成新Model
     */
    @Suppress("ALL")
    fun instanceModel(
        templateModel: Model,
        pipelineName: String,
        templateVariables: List<TemplateVariable>?,
        instanceFromTemplate: Boolean,
        labels: List<String>? = null,
        defaultStageTagId: String?,
        templateId: String? = null,
        staticViews: List<String> = emptyList(),
        triggerConfigs: List<TemplateInstanceTriggerConfig>? = null,
        recommendedVersion: TemplateInstanceRecommendedVersion? = null,
        overrideTemplateField: TemplateField? = null
    ): Model {
        val templateTrigger = templateModel.getTriggerContainer()
        val triggerElements = mergeTriggerElements(
            templateTriggerElements = templateTrigger.elements,
            triggerConfigs = triggerConfigs,
            overrideTriggerStepIds = overrideTemplateField?.triggerStepIds
        )
        val pipelineParam = mergeParams(
            templateParams = templateTrigger.params,
            templateVariables = templateVariables,
            overrideParamIds = overrideTemplateField?.paramIds
        )
        val buildNo = mergeRecommendedVersion(
            pipelineParams = pipelineParam,
            templateBuildNo = templateModel.getTriggerContainer().buildNo,
            recommendedVersion = recommendedVersion,
            overrideReCommendedVersion = overrideTemplateField?.recommendedVersion
        )
        val triggerContainer = templateTrigger.copy(
            buildNo = buildNo,
            elements = triggerElements,
            params = pipelineParam,
        )

        return Model(
            name = pipelineName,
            desc = "",
            stages = getFixedStages(templateModel, triggerContainer, defaultStageTagId),
            labels = labels ?: templateModel.labels,
            instanceFromTemplate = instanceFromTemplate,
            templateId = templateId,
            staticViews = staticViews
        )
    }

    fun instanceModel(
        model: Model,
        templateResource: PipelineTemplateResource
    ): Model {
        if (templateResource.model !is Model) {
            throw ErrorCodeException(
                errorCode = ProcessTemplateMessageCode.ERROR_TEMPLATE_TYPE_MODEL_TYPE_NOT_MATCH
            )
        }
        val templateModel = templateResource.model as Model
        val triggerContainer = mergeTriggerContainer(
            model = model,
            templateModel = templateModel
        )
        val stages = mutableListOf<Stage>()
        templateModel.stages.forEachIndexed { index, stage ->
            if (index == 0) {
                stages.add(stage.copy(containers = listOf(triggerContainer)))
            } else {
                stages.add(stage)
            }
        }
        return model.copy(
            stages = stages,
            parsedTemplateId = templateResource.templateId,
            parsedTemplateVersion = templateResource.version
        )
    }

    fun instanceSetting(
        setting: PipelineSetting,
        templateSetting: PipelineSetting,
        settingGroups: List<PipelineSettingGroupType>? = null
    ): PipelineSetting {
        if (settingGroups == null) return templateSetting
        val instanceSetting = setting.copy()
        mergeBuildNumRule(
            setting = instanceSetting,
            templateSetting = templateSetting,
            settingGroups = settingGroups
        )
        mergeLabel(
            setting = instanceSetting,
            templateSetting = templateSetting,
            settingGroups = settingGroups
        )
        mergeNotices(
            setting = instanceSetting,
            templateSetting = templateSetting,
            settingGroups = settingGroups
        )
        mergeConcurrency(
            setting = instanceSetting,
            templateSetting = templateSetting,
            settingGroups = settingGroups
        )
        return instanceSetting
    }

    fun getRecommendedVersion(
        buildNo: BuildNo?,
        params: List<BuildFormProperty>,
        overrideReCommendedVersion: Boolean?
    ): TemplateInstanceRecommendedVersion? {
        if (overrideReCommendedVersion != true || buildNo == null) return null
        val recommendedVersion = TemplateInstanceRecommendedVersion(
            enabled = true,
            buildNo = buildNo
        )
        params.forEach { param ->
            when (param.id) {
                MAJORVERSION -> recommendedVersion.major = param.defaultValue.toString().toIntOrNull() ?: 0
                MINORVERSION -> recommendedVersion.minor = param.defaultValue.toString().toIntOrNull() ?: 0
                FIXVERSION -> recommendedVersion.fix = param.defaultValue.toString().toIntOrNull() ?: 0
            }
        }
        return recommendedVersion
    }

    private fun mergeTriggerContainer(
        model: Model,
        templateModel: Model,
    ): TriggerContainer {
        val triggerElements = mergeTriggerElements(
            templateTriggerElements = templateModel.getTriggerContainer().elements,
            triggerConfigs = model.triggerConfigs,
            overrideTriggerStepIds = model.overrideTemplateField?.triggerStepIds
        )
        val pipelineParams = mergeParams(
            templateParams = templateModel.getTriggerContainer().params,
            templateVariables = model.templateVariables,
            overrideParamIds = model.overrideTemplateField?.paramIds
        )
        val buildNo = mergeRecommendedVersion(
            pipelineParams = pipelineParams,
            templateBuildNo = templateModel.getTriggerContainer().buildNo,
            recommendedVersion = model.recommendedVersion,
            overrideReCommendedVersion = model.overrideTemplateField?.recommendedVersion
        )
        return templateModel.getTriggerContainer().copy(
            buildNo = buildNo,
            elements = triggerElements,
            params = pipelineParams
        )
    }

    /**
     * 合并触发器
     */
    private fun mergeTriggerElements(
        templateTriggerElements: List<Element>,
        triggerConfigs: List<TemplateInstanceTriggerConfig>?,
        overrideTriggerStepIds: List<String>?
    ): List<Element> {
        if (triggerConfigs == null) return templateTriggerElements

        val triggerConfigMap = triggerConfigs.filter { it.stepId != null }.associateBy { it.stepId }
        return templateTriggerElements.map { templateTriggerElement ->
            if (templateTriggerElement.stepId.isNullOrEmpty()) {
                templateTriggerElement
            } else {
                val triggerConfig = triggerConfigMap[templateTriggerElement.stepId]
                val overrideTrigger = overrideTrigger(
                    templateTriggerElement = templateTriggerElement,
                    overrideTriggerStepIds = overrideTriggerStepIds,
                    triggerConfig = triggerConfig
                )
                if (overrideTrigger) {
                    copyTriggerElement(
                        triggerElement = templateTriggerElement,
                        triggerConfig = triggerConfig!!
                    )
                } else {
                    templateTriggerElement
                }
            }
        }
    }

    private fun mergeParams(
        templateParams: List<BuildFormProperty>,
        templateVariables: List<TemplateVariable>?,
        overrideParamIds: List<String>?
    ): List<BuildFormProperty> {
        if (templateVariables == null) return templateParams

        val templateVariableMap = templateVariables.associateBy { it.key }
        return templateParams.map { templateParam ->
            val templateVariable = templateVariableMap[templateParam.id]
            val overrideParam = overrideParam(
                templateParam = templateParam,
                overrideParamIds = overrideParamIds,
                templateVariable = templateVariable
            )
            val pipelineParams = if (overrideParam) {
                templateParam.copy(
                    defaultValue = templateVariable!!.value,
                    required = templateVariable.allowModifyAtStartup ?: templateParam.required
                )
            } else {
                templateParam
            }
            PipelineUtils.cleanOptions(pipelineParams)
        }
    }

    private fun overrideParam(
        templateParam: BuildFormProperty,
        overrideParamIds: List<String>?,
        templateVariable: TemplateVariable?,
    ): Boolean {
        // 覆盖的key存在且变量值类型与模板参数类型一致,则流水线的变量覆盖模版的
        return overrideParamIds != null &&
                overrideParamIds.contains(templateParam.id) &&
                templateVariable != null &&
                templateVariable.value.javaClass == templateParam.defaultValue.javaClass
    }

    private fun overrideTrigger(
        templateTriggerElement: Element,
        overrideTriggerStepIds: List<String>?,
        triggerConfig: TemplateInstanceTriggerConfig?
    ): Boolean {
        return !templateTriggerElement.stepId.isNullOrEmpty() &&
                overrideTriggerStepIds != null &&
                overrideTriggerStepIds.contains(templateTriggerElement.stepId) &&
                triggerConfig != null
    }

    private fun copyTriggerElement(
        triggerElement: Element,
        triggerConfig: TemplateInstanceTriggerConfig
    ): Element {
        triggerConfig.disabled?.let {
            triggerElement.additionalOptions?.enable = !triggerConfig.disabled!!
        }
        return when (triggerElement) {
            is TimerTriggerElement -> {
                triggerConfig.cron?.let {
                    triggerElement.copy(
                        advanceExpression = listOf(triggerConfig.cron!!)
                    )
                } ?: triggerElement
            }

            else -> triggerElement
        }
    }

    private fun mergeBuildNumRule(
        setting: PipelineSetting,
        templateSetting: PipelineSetting,
        settingGroups: List<PipelineSettingGroupType>
    ) {
        if (settingGroups.contains(PipelineSettingGroupType.CUSTOM_BUILD_NUM)) return
        setting.buildNumRule = templateSetting.buildNumRule
    }

    private fun mergeLabel(
        setting: PipelineSetting,
        templateSetting: PipelineSetting,
        settingGroups: List<PipelineSettingGroupType>
    ) {
        if (settingGroups.contains(PipelineSettingGroupType.LABEL)) return
        setting.labels = templateSetting.labels
        setting.labelNames = templateSetting.labelNames
    }

    private fun mergeNotices(
        setting: PipelineSetting,
        templateSetting: PipelineSetting,
        settingGroups: List<PipelineSettingGroupType>
    ) {
        if (settingGroups.contains(PipelineSettingGroupType.NOTICES)) return
        setting.successSubscription = templateSetting.successSubscription
        setting.failSubscription = templateSetting.failSubscription
        setting.successSubscriptionList = templateSetting.successSubscriptionList
        setting.failSubscriptionList = templateSetting.failSubscriptionList
    }

    private fun mergeConcurrency(
        setting: PipelineSetting,
        templateSetting: PipelineSetting,
        settingGroups: List<PipelineSettingGroupType>
    ) {
        if (settingGroups.contains(PipelineSettingGroupType.CONCURRENCY)) return
        setting.runLockType = templateSetting.runLockType
        setting.waitQueueTimeMinute = templateSetting.waitQueueTimeMinute
        setting.maxQueueSize = templateSetting.maxQueueSize
        setting.concurrencyGroup = templateSetting.concurrencyGroup
        setting.concurrencyCancelInProgress = templateSetting.concurrencyCancelInProgress
        setting.maxConRunningQueueSize = templateSetting.maxConRunningQueueSize
    }

    private fun mergeRecommendedVersion(
        pipelineParams: List<BuildFormProperty>,
        templateBuildNo: BuildNo?,
        recommendedVersion: TemplateInstanceRecommendedVersion?,
        overrideReCommendedVersion: Boolean?
    ): BuildNo? {
        if (overrideReCommendedVersion != true) return templateBuildNo
        pipelineParams.forEach { param ->
            when (param.id) {
                MAJORVERSION -> param.defaultValue = recommendedVersion?.major ?: 0
                MINORVERSION -> param.defaultValue = recommendedVersion?.minor ?: 0
                FIXVERSION -> param.defaultValue = recommendedVersion?.fix ?: 0
            }
        }
        return recommendedVersion?.buildNo
    }
}
