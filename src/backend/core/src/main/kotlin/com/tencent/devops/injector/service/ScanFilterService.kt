package com.tencent.devops.injector.service

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.OpenScanConfigParam


interface ScanFilterService : InjectorService{


    fun filterScanTool(scanTools: MutableList<String>,
                       openScanConfigParam: OpenScanConfigParam,
                       commandParam: CommandParam
    ): MutableList<String>


    fun filterScanCheckerSet(checkerSetType: String?, param: CodeccCheckAtomParamV3)
}