package com.tencent.devops.hash.pojo

data class HashCCNInputObj(
        val defects : List<HashCCNCommonFile>,
        val filesTotalCCN : List<HashFileTotalCCNFile>?,
        val lowThresholdDefects: List<HashCCNCommonFile>?
)
