package com.tencent.devops.docker

import com.tencent.devops.pojo.CodeccCheckAtomParamV3

object LocalParam {
    val toolName: ThreadLocal<String> = object : ThreadLocal<String>() {
        override fun initialValue(): String {
            return ""
        }
    }

    val param: ThreadLocal<CodeccCheckAtomParamV3> = object : ThreadLocal<CodeccCheckAtomParamV3>() {
        override fun initialValue(): CodeccCheckAtomParamV3 {
            return CodeccCheckAtomParamV3()
        }
    }

    fun set(toolNm: String, prm: CodeccCheckAtomParamV3) {
        toolName.set(toolNm)
        param.set(prm)
    }
}
