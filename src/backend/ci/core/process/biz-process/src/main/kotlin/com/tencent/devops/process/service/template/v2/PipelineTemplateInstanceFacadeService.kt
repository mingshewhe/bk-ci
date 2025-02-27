package com.tencent.devops.process.service.template.v2

import com.tencent.devops.common.client.Client
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.permission.template.PipelineTemplatePermissionService
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineTemplateInstanceFacadeService @Autowired constructor(
    private val pipelineTemplateCommonService: PipelineTemplateCommonService,
    private val pipelineTemplateInfoService: PipelineTemplateInfoService,
    private val pipelineTemplateModelParser: PipelineTemplateModelParser,
    private val pipelineTemplatePACService: PipelineTemplatePACService,
    private val pipelineTemplatePermissionService: PipelineTemplatePermissionService,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineTemplateResourceService: PipelineTemplateResourceService,
    private val pipelineTemplateSettingService: PipelineTemplateSettingService,
    private val dslContext: DSLContext,
    private val client: Client,
    private val pipelineTemplateRelatedService: PipelineTemplateRelatedService
) {

}
