package com.tencent.devops.injector.service.impl

import com.tencent.bk.devops.plugin.docker.pojo.DockerRunRequest
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.injector.service.DockerRunService
import java.io.File

class DockerRunServiceImpl : DockerRunService {

    override fun getDockerRunRequestParam(imageParam: ImageParam, commandParam: CommandParam): DockerRunRequest {
        return DockerRunRequest(
            userId = commandParam.landunParam.userId,
            imageName = imageParam.imageName,
            command = imageParam.command,
            dockerLoginUsername = imageParam.registryUser,
            dockerLoginPassword = imageParam.registryPwd,
            workspace = File(commandParam.landunParam.streamCodePath),
            ipEnabled = false
        )
    }
}