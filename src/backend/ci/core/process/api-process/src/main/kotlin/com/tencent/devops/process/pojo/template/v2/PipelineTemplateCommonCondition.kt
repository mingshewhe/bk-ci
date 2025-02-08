package com.tencent.devops.process.pojo.template.v2

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.enums.PipelineTemplateSource
import com.tencent.devops.common.pipeline.enums.PipelineTemplateType
import com.tencent.devops.common.pipeline.enums.VersionStatus
import io.swagger.v3.oas.annotations.media.Schema
import kotlin.reflect.full.memberProperties

@Schema(title = "流水线模板通用条件")
data class PipelineTemplateCommonCondition(
    @get:Schema(title = "项目ID", required = true)
    val projectId: String? = null,
    @get:Schema(title = "模板ID", required = true)
    val templateId: String? = null,
    @get:Schema(title = "根据名称模糊搜索", required = true)
    val fuzzySearchName: String? = null,
    @get:Schema(title = "根据名称精准搜索", required = true)
    val exactSearchName: String? = null,
    @get:Schema(title = "公共/约束/自定义模式", required = true)
    val mode: String? = null,
    @get:Schema(title = "应用范畴", required = true)
    val category: String? = null,
    @get:Schema(title = "模板类型", required = true)
    val type: PipelineTemplateType? = null,
    @get:Schema(title = "是否开启PAC", required = true)
    val enablePac: Boolean? = null,
    @get:Schema(title = "最新版本号", required = true)
    val lastedVersion: Long? = null,
    @get:Schema(title = "最新版本状态", required = true)
    val lastedVersionStatus: VersionStatus? = null,
    @get:Schema(title = "最新版本名称", required = true)
    val lastedVersionName: String? = null,
    @get:Schema(title = "最新设置版本号", required = true)
    val lastedSettingVersion: Int? = null,
    @get:Schema(title = "模板来源", required = true)
    val source: PipelineTemplateSource? = null,
    @get:Schema(title = "是否关联研发商店", required = true)
    val storeFlag: Boolean? = null,
    @get:Schema(title = "父模板ID", required = true)
    val srcTemplateId: String? = null,
    @get:Schema(title = "父模板项目ID", required = true)
    val srcTemplateProjectId: String? = null,
    @get:Schema(title = "调试流水线数", required = true)
    val debugPipelineCount: Int? = null,
    @get:Schema(title = "实例流水线数", required = true)
    val instancePipelineCount: Int? = null,
    @get:Schema(title = "创建人", required = true)
    val creator: String? = null,
    @get:Schema(title = "更新人", required = true)
    val updater: String? = null,
    @get:Schema(title = "page", required = true)
    val page: Int? = null,
    @get:Schema(title = "pageSize", required = true)
    val pageSize: Int? = null
) {
    fun checkAllFieldsAreNull() {
        val isAllFieldsAreNull = this::class.memberProperties.all {
            it.call(this) == null
        }
        // TODO
        if (isAllFieldsAreNull) {
            throw ErrorCodeException(
                errorCode = ""
            )
        }
    }
}
