package com.tencent.devops.hash.pojo

data class HashLintOutputFile(
    override val checkerName: String,
    override val description: String,
    override val filePath: String,
    override val line: String,
    val pinpointHash : String?
) : HashLintInputFile(
    checkerName, description, filePath, line
)