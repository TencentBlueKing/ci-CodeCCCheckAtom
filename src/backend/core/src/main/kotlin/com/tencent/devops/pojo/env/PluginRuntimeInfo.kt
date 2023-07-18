package com.tencent.devops.pojo.env

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.CodeccCheckAtomParamV3

/**
 * 插件运行参数
 */
object PluginRuntimeInfo {

    var buildId: String? = null

    var projectId: String? = null

    var atomContext: AtomContext<CodeccCheckAtomParamV3>? = null

    fun initRuntimeInfo(atomContext: AtomContext<CodeccCheckAtomParamV3>) {
        val params = atomContext.param
        this.buildId = params.pipelineBuildId
        LogUtils.printLog("initRuntimeInfo buildId: $buildId")
        this.projectId = params.projectName
        LogUtils.printLog("initRuntimeInfo projectId: $projectId")
        this.atomContext = atomContext
    }

}