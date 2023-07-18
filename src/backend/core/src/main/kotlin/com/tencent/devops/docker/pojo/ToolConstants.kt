package com.tencent.devops.docker.pojo

object ToolConstants {
    const val COVERITY = "coverity"
    const val KLOCWORK = "klocwork"
    const val PINPOINT = "pinpoint"
    const val PVS = "pvs"
    const val DUPC = "dupc"
    const val CCN = "ccn"
    const val TSCCN = "tsccn"
    const val CODEQL = "codeql"
    const val SCC = "scc"
    const val CLANG = "clang"
    const val CLANGWARNING = "clangwarning"
    const val RESHARPER = "resharper"
    const val ANDROIDLINT = "android-lint"
    const val SPOTBUGS = "spotbugs"
    const val GITHUBSTATISTIC = "githubstatistic"
    const val RIPS = "rips"
    const val CODEQLWX = "codeql-wx"
    const val PECKERDEFECTSCAN = "peckerdefectscan"
    const val BKCHECK = "bkcheck"
    val COMPILE_TOOLS = listOf(COVERITY, KLOCWORK, PINPOINT,CODEQL, CLANG, CLANGWARNING, SPOTBUGS, PVS, ANDROIDLINT
        , RESHARPER, CODEQLWX, PECKERDEFECTSCAN)
    val NOLINT_TOOLS = listOf(DUPC, CCN, TSCCN, SCC, COVERITY, KLOCWORK, PINPOINT, GITHUBSTATISTIC)
    val CODE_TOOLS_ACOUNT = listOf(SCC)
    val NO_DIFF_CODE_TOOLS = listOf(SCC, COVERITY)
}
