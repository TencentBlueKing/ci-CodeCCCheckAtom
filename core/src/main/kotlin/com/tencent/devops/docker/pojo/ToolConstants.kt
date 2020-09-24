package com.tencent.devops.docker.pojo

object ToolConstants {
    const val COVERITY = "coverity"
    const val KLOCWORK = "klocwork"
    const val PINPOINT = "pinpoint"
    const val DUPC = "dupc"
    const val CCN = "ccn"
    const val CODEQL = "codeql"
    const val CLOC = "cloc"
    const val CLANG = "clang"
    const val SPOTBUGS = "spotbugs"
    val COMPILE_TOOLS = listOf(COVERITY, KLOCWORK, PINPOINT,CODEQL, CLANG, SPOTBUGS)
    val NOLINT_TOOLS = listOf(DUPC, CCN, CLOC, COVERITY, KLOCWORK, PINPOINT)
}
