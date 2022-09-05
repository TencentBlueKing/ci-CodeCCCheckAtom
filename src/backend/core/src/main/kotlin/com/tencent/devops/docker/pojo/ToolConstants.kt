package com.tencent.devops.docker.pojo

object ToolConstants {
    const val COVERITY = "coverity"
    const val KLOCWORK = "klocwork"
    const val PINPOINT = "pinpoint"
    const val DUPC = "dupc"
    const val CCN = "ccn"
    const val TSCCN = "tsccn"
    const val CODEQL = "codeql"
    const val CLOC = "cloc"
    const val SCC = "scc"
    const val CLANG = "clang"
    const val CLANGWARNING = "clangwarning"
    const val RESHARPER = "resharper"
    const val SPOTBUGS = "spotbugs"
    const val GITHUBSTATISTIC = "githubstatistic"
    const val RIPS = "rips"
    val COMPILE_TOOLS = listOf(COVERITY, KLOCWORK, PINPOINT,CODEQL, CLANG, CLANGWARNING, SPOTBUGS, RESHARPER)
    val NOLINT_TOOLS = listOf(DUPC, CCN, TSCCN, CLOC, SCC, COVERITY, KLOCWORK, PINPOINT, GITHUBSTATISTIC)
    val CODE_TOOLS_ACOUNT = listOf(CLOC, SCC)
}
