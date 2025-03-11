package com.tencent.devops.process.service.template.v2

import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线模版门面类
 */
@Service
class PipelineTemplateFacadeService @Autowired constructor(
    private val pipelineTemplateCommonService: PipelineTemplateCommonService,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateModelParser: PipelineTemplateModelParser,
    private val pipelineTemplatePACService: PipelineTemplatePACService,
    private val pipelineTemplatePermissionService: PipelineTemplatePermissionService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService
) {
    // 创建模板 自定义/研发商店安装/代码库/本地文件
    // 复制模板
    // 导出模板
    // 删除模板 直接删除（自定义）、研发商店卸载、代码库
    // 编辑模板
    // 保存草稿
    // 发布模板
    // 复制模板
    // 获取用户最近打开的模板类型
    // 获取模板列表
    // 查看模板详情
    // 流水线模板检查
    // 模板版本对比
    // 查看全部模板版本历史
}
