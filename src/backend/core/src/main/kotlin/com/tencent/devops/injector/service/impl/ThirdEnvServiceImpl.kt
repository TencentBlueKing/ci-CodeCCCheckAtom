package com.tencent.devops.injector.service.impl

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.injector.service.ThirdEnvService
import com.tencent.devops.pojo.exception.user.CodeCCUserException
import com.tencent.devops.pojo.exception.ErrorCode

class ThirdEnvServiceImpl : ThirdEnvService {
    override fun checkThirdEnv(commandParam: CommandParam, toolName: String) {

    }

    override fun logThirdHelpInfo() {
        throw CodeCCUserException(
            ErrorCode.USER_ENV_MISSING,
            "need install docker to run tool",
            arrayOf("docker")
        )
    }

}