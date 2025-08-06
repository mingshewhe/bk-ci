package com.tencent.devops.common.pipeline.pojo

import com.tencent.devops.common.pipeline.pojo.setting.PipelineSettingGroupType

/**
 * 模版实例化时的字段,当流水线从模版实例化时,表示哪些字段可以被实例化,实例化时可以选择跟随模版的值，还是流水线自定义
 *
 * 为什么增加了overrideAllXxx这个变量,这个是因为ui和code方式实例化时,对是否覆盖模版的参数和设置有所不同
 *  - 历史数据/ui方式新增的实例化,参数和设置默认流水线自定义，不跟随模版的
 *  - code方式新增的实例化,参数和设置默认跟随模版的
 */
data class TemplateInstanceField(
    // 是否覆盖全部参数和设置,ui方式新增的实例化,值为true,code方式新增的实例化,值为false
    // true: 全部使用流水线自定义设置,excludeParamIds、excludeSettingGroups中包含的值使用模版设置
    // false: 全部使用模版设置,excludeParamIds、excludeSettingGroups中包含的值使用流水线自定义
    val overrideAllParamAndSetting: Boolean = true,
    // 需要排除的参数
    val excludeParamIds: List<String>? = null,
    // 流水线指定的自定义的触发器,只能自定义启用/禁用,不能新增修改删除触发器
    val triggerStepIds: List<String>? = null,
    // 需要排除的设置
    val excludeSettingGroups: List<PipelineSettingGroupType>? = null
) {
    companion object {
        // 推荐版本号
        const val BK_CI_BUILD_NO = "BK_CI_BUILD_NO"
    }

    fun overrideParam(paramId: String): Boolean {
        return if (overrideAllParamAndSetting) {
            excludeParamIds?.contains(paramId) != true
        } else {
            excludeParamIds?.contains(paramId) == true
        }
    }

    /**
     * 是否覆盖推荐版本,推荐版本号也放在参数中传递
     */
    fun overrideRecommendedVersion(): Boolean {
        return overrideParam(BK_CI_BUILD_NO)
    }

    fun overrideTrigger(triggerStepId: String): Boolean {
        return triggerStepIds?.contains(triggerStepId) == true
    }

    fun overrideSetting(settingGroup: PipelineSettingGroupType): Boolean {
        return if (overrideAllParamAndSetting) {
            excludeSettingGroups?.contains(settingGroup) != true
        } else {
            excludeSettingGroups?.contains(settingGroup) == true
        }
    }
}
