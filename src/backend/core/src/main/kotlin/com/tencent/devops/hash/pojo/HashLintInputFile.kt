package com.tencent.devops.hash.pojo

open class HashLintInputFile(
    open val checkerName : String,
    open val description : String,
    open val filePath : String,
    open val line : String
)