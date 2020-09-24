package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
open class CommonVO {
    open var entityId: String? = null
    open var createdDate: Long? = null
    open var createdBy: String? = null
    open var updatedDate: Long? = null
    open var updatedBy: String? = null
}
