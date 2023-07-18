package com.tencent.devops.injector.service

import com.tencent.devops.docker.pojo.LandunParam
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CodeccExecuteConfig

interface BkCheckService : InjectorService {

    fun downloadIncDbFile(codeccExecuteConfig: CodeccExecuteConfig, landunParam: LandunParam, scanTools: List<String>)
}