package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.template.ITemplateModel
import com.tencent.devops.common.pipeline.template.JobTemplateModel
import com.tencent.devops.common.pipeline.template.StageTemplateModel
import com.tencent.devops.common.pipeline.template.StepTemplateModel
import org.springframework.stereotype.Service

/**
 * 流水线模版PIPELINE AS CODE相关类
 */
@Service
class PipelineTemplatePACService {
    fun yamlTransferToModel(yaml: String): ITemplateModel {
        TODO()
    }

    fun modelTransferToYaml(model: ITemplateModel): String {
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
