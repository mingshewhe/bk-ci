package com.tencent.devops.process.service.template.v2

import com.tencent.devops.process.service.template.TemplateFacadeService
import org.springframework.stereotype.Service

/**
 * 流水线模版兼容适配类
 */
@Service
class PipelineTemplateCompatibilityAdapter(
    private val v1TemplateFacadeService: TemplateFacadeService,
    private val v2TemplateFacadeService: PipelineTemplateFacadeService
) {

}
