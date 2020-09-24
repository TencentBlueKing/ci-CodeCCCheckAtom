package com.tencent.devops.common.api

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.devops.pojo.CodeccCheckAtomParamV3

interface SubReport {

    fun doTaskFailReport(atomContext: AtomContext<CodeccCheckAtomParamV3>,startTime:Long)
}