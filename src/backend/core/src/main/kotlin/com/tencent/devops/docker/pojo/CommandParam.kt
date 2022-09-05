package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandParam(
    val landunParam: LandunParam,
    var scmType: String,
    val svnUser: String,
    var svnPassword: String,
    val scmSshAccess: String?, // 空或者svn
    var repoUrlMap: String,  // getRepoUrlMap(codeccExecuteConfig)
    val repoRelPathMap: Map<String, String>, // getRepoScmRelPathMap(codeccExecuteConfig)
    val repoScmRelpathMap: String,          // no use
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
    val projectBuildPath: String,
    val syncType: Boolean,
    var klockWorkHomeBin: String,
    var pinpointHomeBin: String,
    var codeqlHomeBin: String,
    var clangHomeBin: String,
    var resharperHomeBin: String,
    var spotBugsHomeBin: String,
    val goPath: String,
    val gatherDefectThreshold: Long, // 告警收敛的阈值

    val needPrintDefect: Boolean = false,
    val openScanPrj: Boolean? = false,
    var extraPrams: Map<String, String>
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
    }
}
