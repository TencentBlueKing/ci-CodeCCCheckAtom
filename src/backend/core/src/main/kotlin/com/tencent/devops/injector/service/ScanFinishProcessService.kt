package com.tencent.devops.injector.service

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.OpenScanConfigParam


interface ScanFinishProcessService : InjectorService{


    fun processAfterScanFinish(atomContext: AtomContext<CodeccCheckAtomParamV3>)

}