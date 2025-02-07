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

package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.pipeline.pojo.setting.PipelineRunLockType
import com.tencent.devops.common.pipeline.pojo.setting.Subscription
import com.tencent.devops.common.pipeline.utils.PIPELINE_RES_NUM_MIN
import com.tencent.devops.common.pipeline.utils.PIPELINE_SETTING_CONCURRENCY_GROUP_DEFAULT
import com.tencent.devops.common.pipeline.utils.PIPELINE_SETTING_MAX_QUEUE_SIZE_DEFAULT
import com.tencent.devops.common.pipeline.utils.PIPELINE_SETTING_WAIT_QUEUE_TIME_MINUTE_DEFAULT
import com.tencent.devops.common.web.annotation.BkField
import com.tencent.devops.common.web.constant.BkStyleEnum
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "模板配置")
open class PipelineTemplateSetting(
    /* 模板基础配置 */
    @get:Schema(title = "项目id", required = true)
    open val projectId: String,
    @get:Schema(title = "模板名称", required = true)
    open val name: String,
    @get:Schema(title = "描述", required = false)
    open val desc: String,
    @get:Schema(title = "标签ID列表", required = false)
    open val labels: List<String>?,
    @get:Schema(title = "标签名称列表（仅用于前端展示，不参与数据保存）", required = false)
    open val labelNames: List<String> = emptyList(),
    @field:BkField(patternStyle = BkStyleEnum.BUILD_NUM_RULE_STYLE, required = false)
    @get:Schema(title = "构建号生成规则", required = false)
    open val buildNumRule: String? = null, // 构建号生成规则

    /* 通知订阅相关配置 */
    @get:Schema(title = "订阅成功通知组", required = false)
    open val successSubscriptionList: List<Subscription>? = null,
    @get:Schema(title = "订阅失败通知组", required = false)
    open val failSubscriptionList: List<Subscription>? = null,

    /* 运行控制、流水线禁用相关配置 */
    @get:Schema(title = "Lock 类型", required = false)
    open val runLockType: PipelineRunLockType?,
    @get:Schema(title = "最大排队时长", required = false)
    open val waitQueueTimeMinute: Int = PIPELINE_SETTING_WAIT_QUEUE_TIME_MINUTE_DEFAULT,
    @get:Schema(title = "最大排队数量", required = false)
    open val maxQueueSize: Int = PIPELINE_SETTING_MAX_QUEUE_SIZE_DEFAULT,
    @field:BkField(patternStyle = BkStyleEnum.PIPELINE_CONCURRENCY_GROUP_STYLE, required = false)
    @get:Schema(title = "并发时,设定的group", required = false)
    open val concurrencyGroup: String? = PIPELINE_SETTING_CONCURRENCY_GROUP_DEFAULT,
    @get:Schema(title = "并发时,是否相同group取消正在执行的流水线", required = false)
    open val concurrencyCancelInProgress: Boolean = false,
    @get:Schema(title = "并发构建数量限制", required = false)
    open val maxConRunningQueueSize: Int? = null, // MULTIPLE类型时，并发构建数量限制

    /* 平台系统控制相关配置 —— 不作为生成版本的配置 */
    @get:Schema(title = "保存流水线编排的最大个数", required = false)
    open val maxPipelineResNum: Int = PIPELINE_RES_NUM_MIN, // 保存流水线编排的最大个数
    @get:Schema(title = "重试时清理引擎变量表", required = false)
    open val cleanvaliablesWhenRetry: Boolean? = false,
    @get:Schema(title = "YAML流水线特殊配置", required = false)
    open val pipelineAsCodeSettings: PipelineAsCodeSettings?,
    @get:Schema(title = "创建人", required = true)
    open val creator: String,
    @get:Schema(title = "更新人", required = false)
    open val updater: String?
) {
    fun toSettingVersion(
        templateId: String,
        settingVersion: Int,
        versionName: String
    ): PipelineTemplateSettingVersion {
        return PipelineTemplateSettingVersion(
            templateId = templateId,
            settingVersion = settingVersion,
            versionName = versionName,
            projectId = projectId,
            name = name,
            desc = desc,
            labels = labels,
            labelNames = labelNames,
            buildNumRule = buildNumRule,
            successSubscriptionList = successSubscriptionList,
            failSubscriptionList = failSubscriptionList,
            runLockType = runLockType,
            waitQueueTimeMinute = waitQueueTimeMinute,
            maxQueueSize = maxQueueSize,
            concurrencyGroup = concurrencyGroup,
            concurrencyCancelInProgress = concurrencyCancelInProgress,
            maxConRunningQueueSize = maxConRunningQueueSize,
            maxPipelineResNum = maxPipelineResNum,
            cleanvaliablesWhenRetry = cleanvaliablesWhenRetry,
            pipelineAsCodeSettings = pipelineAsCodeSettings,
            creator = creator,
            updater = updater
        )
    }
}
