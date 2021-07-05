package com.tencent.devops.common.api

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.TaskBaseVO
import com.tencent.devops.pojo.OpenScanConfigParam

interface SubBuild {
    fun subBuild(codeccTaskInfo: TaskBaseVO?,
                 commandParam: CommandParam,
                 staticScanTools: List<String>,
                 openScanConfigParam: OpenScanConfigParam,
                 threadCount: Int,
                 runCompileTools: Boolean): HashMap<String, Any>?
}