package com.tencent.devops.injector.service.impl

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.injector.service.ScanFilterService
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.OpenScanConfigParam

class ScanFilterServiceImpl : ScanFilterService {

    override fun filterScanTool(
        scanTools: MutableList<String>,
        openScanConfigParam: OpenScanConfigParam,
        commandParam: CommandParam
    ): MutableList<String> {
        return scanTools
    }

    override fun filterScanCheckerSet(checkerSetType: String?, param: CodeccCheckAtomParamV3) {

    }


}