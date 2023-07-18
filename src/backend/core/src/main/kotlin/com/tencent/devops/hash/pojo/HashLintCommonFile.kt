package com.tencent.devops.hash.pojo

import com.tencent.devops.docker.pojo.DefectInstance

data class HashLintCommonFile(
    val checkerName: String,
    val description: String,
    val filePath: String,
    val line: String?,
    var ignoreCommentDefect: Boolean? = false,
    var ignoreCommentReason: String? = null,
    var pinpointHash: String? = null,
    var langValue: Long? = null,
    var language: String? = null,
    var author: String? = null,
    var revision: String? = null,
    var lineUpdateTime: Long? = null,
    var branch: String? = null,
    var relPath: String? = null,
    var url: String? = null,
    val defectInstances: List<DefectInstance>? = null
)
