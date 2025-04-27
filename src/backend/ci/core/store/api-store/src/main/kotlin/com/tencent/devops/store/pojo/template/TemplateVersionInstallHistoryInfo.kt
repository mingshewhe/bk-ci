package com.tencent.devops.store.pojo.template

import io.swagger.v3.oas.annotations.media.Schema
import org.joda.time.LocalDateTime

@Schema(title = "模板版本安装历史实体")
data class TemplateVersionInstallHistoryInfo(
    @get:Schema(title = "研发商店父模板ID")
    val srcMarketTemplateId: String = "",
    @get:Schema(title = "研发商店父模板项目ID")
    val srcMarketTemplateProjectCode: String,
    @get:Schema(title = "研发商店父模板代码")
    val srcMarketTemplateCode: String,
    @get:Schema(title = "项目ID")
    val projectCode: String,
    @get:Schema(title = "模板代码（对应process数据库的ID）")
    val templateCode: String,
    @get:Schema(title = "安装的发布版本号")
    val version: Long,
    @get:Schema(title = "安装的版本名称")
    val versionName: String,
    @get:Schema(title = "创建人")
    val creator: String = "system",
    @get:Schema(title = "创建时间")
    val createTime: LocalDateTime? = null
)
