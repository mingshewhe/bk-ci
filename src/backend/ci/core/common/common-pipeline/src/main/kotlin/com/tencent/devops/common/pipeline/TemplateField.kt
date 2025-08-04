package com.tencent.devops.common.pipeline

import com.tencent.devops.common.pipeline.pojo.setting.PipelineSettingGroupType

/**
 * 覆盖模版的字段
 * 当流水线从模版实例化时,表示哪些字段需要覆盖模版的值,使用流水线自定义的值,如果没有指定,则使用模版的值
 */
data class TemplateField(
    // 覆盖的参数Id列表
    val paramIds: List<String>? = null,
    // 覆盖推荐版本号
    val recommendedVersion: Boolean? = null,
    // 覆盖的触发器
    val triggerStepIds: List<String>? = null,
    // 覆盖的设置组
    val settingGroups: List<PipelineSettingGroupType>? = null
)
