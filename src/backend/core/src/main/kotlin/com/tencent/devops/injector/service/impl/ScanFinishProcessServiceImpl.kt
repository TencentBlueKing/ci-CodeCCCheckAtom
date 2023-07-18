package com.tencent.devops.injector.service.impl

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.devops.injector.service.ScanFinishProcessService
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.utils.CodeccEnvHelper

open class ScanFinishProcessServiceImpl : ScanFinishProcessService {

    override fun processAfterScanFinish(atomContext: AtomContext<CodeccCheckAtomParamV3>) {
        //清理.temp目录
        CodeccEnvHelper.deleteCodeccWorkspace()
    }
}