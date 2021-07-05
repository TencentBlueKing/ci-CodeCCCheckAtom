package com.tencent.devops.common.utils

import com.tencent.bk.devops.atom.AtomContext
import com.tencent.devops.common.api.SubReport
import com.tencent.devops.pojo.CodeccCheckAtomParamV3

object CommonSubReport : SubReport {

    override fun doTaskFailReport(atomContext: AtomContext<CodeccCheckAtomParamV3>,startTime:Long) {
    }
}