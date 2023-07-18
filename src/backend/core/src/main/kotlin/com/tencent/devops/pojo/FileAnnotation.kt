package com.tencent.devops.pojo

import com.fasterxml.jackson.annotation.JsonIgnore

data class FileAnnotation(
    var filePath: String? = null,
    var fileRelPath: String? = null,
    var fileUpdateTime: Long? = null,
    var revision: String? = null,
    var longRevision: String? = null,
    var change: String? = null,
    var scmType: String? = null,
    var url: String? = null,
    var changeRecords: MutableList<ChangeRecord>,
    @field:JsonIgnore
    var changeRecordsMap: MutableMap<String, ChangeRecord>
)
