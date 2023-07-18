package com.tencent.devops.pojo

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.hash.pojo.HashCCNCommonFile
import com.tencent.devops.hash.pojo.HashLintCommonFile


data class FileProcessResult(
    val filePath: String,
    var fileRelPath: String? = null,
    var fileAbsolutePath: String? = null,
    var fileMd5: String? = null,
    var toolName: String? = null,
    val commandParam: CommandParam,
    val ignoreDefectInfo: List<IgnoreDefectSubInfo>? = null,
    var lintDefects: List<HashLintCommonFile>? = null,
    var ccnDefects: List<HashCCNCommonFile>? = null,
    var defectTraceFileMap: Map<String, FileMD5Info>? = null
)
