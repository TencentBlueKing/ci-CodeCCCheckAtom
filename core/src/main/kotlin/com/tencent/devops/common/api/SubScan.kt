package com.tencent.devops.common.api

import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ToolOptions

interface SubScan {
    fun removeCompiledLanguages(inputLanguage: Long): Long

    fun toolOptionsPro(toolName:String,commandParam:CommandParam,analyzeConfigInfo:AnalyzeConfigInfo,inputData: MutableMap<String, Any?>):  Any?
}