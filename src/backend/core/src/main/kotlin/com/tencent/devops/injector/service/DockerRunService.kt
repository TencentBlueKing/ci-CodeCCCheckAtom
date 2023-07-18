package com.tencent.devops.injector.service

import com.tencent.bk.devops.plugin.docker.pojo.DockerRunRequest
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam

interface DockerRunService : InjectorService{

    fun getDockerRunRequestParam(imageParam: ImageParam, commandParam: CommandParam) : DockerRunRequest



}