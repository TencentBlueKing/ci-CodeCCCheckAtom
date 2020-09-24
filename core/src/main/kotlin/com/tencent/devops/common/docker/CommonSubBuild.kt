package com.tencent.devops.common.docker

import com.tencent.devops.common.api.SubBuild
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.TaskBaseVO
import com.tencent.devops.pojo.OpenScanConfigParam

object CommonSubBuild : SubBuild {
    override fun subBuild(codeccTaskInfo: TaskBaseVO?,
                          commandParam: CommandParam,
                          staticScanTools: List<String>,
                          openScanConfigParam: OpenScanConfigParam,
                          threadCount: Int,
                          runCompileTools: Boolean): HashMap<String, Any>? {
        val subBuildInfo: HashMap<String, Any> = HashMap()
        subBuildInfo["staticScanTools"] = staticScanTools
        subBuildInfo["threadCount"] = threadCount
        subBuildInfo["runCompileTools"] = runCompileTools

        return subBuildInfo
    }

}