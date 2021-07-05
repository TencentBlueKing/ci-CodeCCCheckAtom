package com.tencent.devops.hash.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.devops.hash.pojo.HashGenerateInputFile

data class HashGenerateOutputFile(
    override val checkerName: String,
    override val description: String,
    override val filePath: String,
    override val line: String,
    val pinpointHash : String?
) : HashGenerateInputFile(
    checkerName, description, filePath, line
)