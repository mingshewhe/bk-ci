package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.common.pipeline.template.JobTemplateModel
import com.tencent.devops.common.pipeline.template.StageTemplateModel
import com.tencent.devops.common.pipeline.template.StepTemplateModel
import com.tencent.devops.process.pojo.template.v2.PipelineTemplateSetting
import org.springframework.stereotype.Service

/**
 * 流水线模版PIPELINE AS CODE相关类
 */
@Service
class PipelineTemplatePACService {
    fun yamlTransferToModel(yaml: String): ITemplateModel {
        TODO()
    }

    fun modelTransferToYaml(
        model: ITemplateModel,
        setting: PipelineTemplateSetting? = null
    ): String {
        return when (model) {
            is Model -> {
                ""
            }

            is StageTemplateModel -> {
                ""
            }

            is JobTemplateModel -> {
                ""
            }

            is StepTemplateModel -> {
                ""
            }

            else -> {
                ""
            }
        }
    }
}
