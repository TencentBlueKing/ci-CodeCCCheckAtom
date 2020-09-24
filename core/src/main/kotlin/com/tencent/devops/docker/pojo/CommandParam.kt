package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandParam(
    val landunParam: LandunParam,
    val devCloudAppId: String,
    val devCloudUrl: String,
    val devCloudToken: String,
    val scmType: String,
    val svnUser: String,
    val svnPassword: String,
    val scmSshAccess: String?, // 空或者svn
    val repoUrlMap: String,  // getRepoUrlMap(codeccExecuteConfig) like "{\"2NLb\":\"http://xxx/xxxx/xxxxxxx\"}"
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
    var spotBugsHomeBin: String,
    val goPath: String,
    val gatherDefectThreshold: Long, // 告警收敛的阈值

    val needPrintDefect: Boolean = false,
    val openScanPrj: Boolean? = false,
    val extraPrams: Map<String, String>
) {
    fun copy(): CommandParam {
        val json = jacksonObjectMapper().writeValueAsString(this)
        return jacksonObjectMapper().readValue(json)
    }

    override fun toString(): String {
        return "CommandParam(devCloudAppId='$devCloudAppId', devCloudUrl='$devCloudUrl', scmType='$scmType', svnUser='$svnUser', repoUrlMap='$repoUrlMap', repoRelPathMap=$repoRelPathMap, repoScmRelpathMap='$repoScmRelpathMap', subCodePathList=$subCodePathList, scanTools='$scanTools', dataRootPath='$dataRootPath', py27Path='$py27Path', py35Path='$py35Path', py27PyLintPath='$py27PyLintPath', py35PyLintPath='$py35PyLintPath', subPath='$subPath', goRoot='$goRoot', coverityResultPath='$coverityResultPath', projectBuildCommand='$projectBuildCommand', coverityHomeBin='$coverityHomeBin', projectBuildPath='$projectBuildPath', syncType=$syncType, klockWorkHomeBin='$klockWorkHomeBin', pinpointHomeBin='$pinpointHomeBin', codeqlHomeBin='$codeqlHomeBin', clangHomeBin='$clangHomeBin', spotBugsHomeBin='$spotBugsHomeBin', goPath='$goPath', needPrintDefect=$needPrintDefect)"
    }

    companion object {
        const val extraHookRepoIdKey = "hookRepoId"
        const val extraHookMrSourceBranchKey = "hookMrSourceBranch"
        const val extraHookMrTargetBranchKey = "hookMrTargetBranch"
    }
}
