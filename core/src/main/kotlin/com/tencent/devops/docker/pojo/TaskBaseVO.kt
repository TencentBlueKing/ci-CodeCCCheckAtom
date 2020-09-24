package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
open class TaskBaseVO : CommonVO() {
    open val taskId: Long = 0
    open val nameEn: String? = null
    open val nameCn: String? = null
    open val projectId: String? = null
    open val projectName: String? = null
    open val pipelineId: String? = null
    open val pipelineName: String? = null
    open val codeLang: Long? = null
    open val taskOwner: List<String>? = null
    open val status: Int? = null
    open val createFrom: String? = null
    open val executeTime: String? = null
    open val executeDate: List<String>? = null
    open val scanType: Int? = null
    open val gongfengFlag: Boolean? = false
    open val gongfengProjectId: Long? = 0
    open val gongfengCommitId: String? = null
}
