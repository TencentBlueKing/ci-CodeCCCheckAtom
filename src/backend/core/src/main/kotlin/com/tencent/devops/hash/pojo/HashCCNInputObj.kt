package com.tencent.devops.hash.pojo

data class HashCCNInputObj(
        val defects : List<HashCCNInputFile>,
        val filesTotalCCN : List<HashFileTotalCCNFile>?
)
