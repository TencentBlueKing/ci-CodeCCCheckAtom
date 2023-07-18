package com.tencent.devops.injector.service.impl

import com.tencent.devops.docker.pojo.LandunParam
import com.tencent.devops.injector.service.BkCheckService
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CodeccExecuteConfig

class BkCheckServiceImpl : BkCheckService {

    override fun downloadIncDbFile(codeccExecuteConfig: CodeccExecuteConfig, landunParam: LandunParam,
                                   scanTools: List<String>) {

    }
}