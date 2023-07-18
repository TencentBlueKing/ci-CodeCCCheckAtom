package com.tencent.devops.injector.service

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.LandunParam
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CodeccExecuteConfig

interface ThirdEnvService : InjectorService {

    fun checkThirdEnv(commandParam: CommandParam, toolName: String)

    fun logThirdHelpInfo()
}