package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.pojo.CodeccExecuteConfig

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandParam(
    val landunParam: LandunParam,
    var scmType: String,
    val svnUser: String,
    var svnPassword: String,
    val scmSshAccess: String?, // 空或者svn
    var repoUrlMap: String,  // getRepoUrlMap(codeccExecuteConfig)
    val repoRelPathMap: Map<String, String>, // getRepoScmRelPathMap(codeccExecuteConfig)
    val repoRelativePathList: List<String>, // code relative path
    val repoScmRelpathMap: String,          // no use
    var repos: List<CodeccExecuteConfig.RepoItem>,
    val subCodePathList: List<String>,
    val scanTools: String,
    val dataRootPath: String,  //  codeccWorkspace like workspace/.temp/codecc_coverity_buildId/
    var py27Path: String,
    var py35Path: String,
    var py27PyLintPath: String,
    var py35PyLintPath: String,
    var subPath: String, // append to path, like /usr/local/svn/bin:/usr/local/bin:/data/bkdevops/apps/coverity
    val goRoot: String,
    val coverityResultPath: String, // codeccWorkspace
    val projectBuildCommand: String,
    var coverityHomeBin: String,
    var pvsHomeBin: String,
    val projectBuildPath: String,
    val syncType: Boolean,
    var klockWorkHomeBin: String,
    var pinpointHomeBin: String,
    var codeqlHomeBin: String,
    var clangHomeBin: String,
    var spotBugsHomeBin: String,
    val goPath: String,
    val gatherDefectThreshold: Long, // 问题收敛的阈值

    val needPrintDefect: Boolean = false,
    val openScanPrj: Boolean? = false,
    var extraPrams: Map<String, String>,
    var prohibitCloc: Boolean? = false,
    var localSCMBlameRun: Boolean = false,
    // bkcheck debug模式是否开启
    var bkcheckDebug: Boolean? = false,
    // 中间结果文件存放路径，运行时组装写入，无需配置
    var codeccWorkspacePath: String? = null
) {
    fun copy(): CommandParam {
        val json = jacksonObjectMapper().writeValueAsString(this)
        return jacksonObjectMapper().readValue(json)
    }

    override fun toString(): String {
        val copyItem = copy()
        copyItem.extraPrams = mapOf()
        copyItem.svnPassword = "***"
        return JsonUtil.toJson(copyItem)
    }

    companion object {
        const val extraHookRepoIdKey = "hookRepoId"
        const val extraHookMrSourceBranchKey = "hookMrSourceBranch"
        const val extraHookMrTargetBranchKey = "hookMrTargetBranch"
        const val diffBranch = "diffBranch"
    }
}
