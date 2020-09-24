package com.tencent.devops.docker.scm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.scm.pojo.ScmInfoItem
import com.tencent.devops.docker.scm.pojo.ScmInfoJson
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.utils.CodeccParamsHelper
import java.io.File
import java.net.URL

class ScmInfo(
    override val commandParam: CommandParam,
    override val toolName: String,
    override val streamName: String,
    override val taskId: Long
) : Scm(commandParam, toolName, streamName, taskId) {

    private fun appendOutputFile(outputFile: String) {
        val outPutFileText = File(outputFile).readText()
        val scmInfoJson = jacksonObjectMapper().readValue<ScmInfoJson>(outPutFileText)
        val scmInfoList = mutableListOf<ScmInfoItem>()
        scmInfoJson.scmInfoList.forEach { scmInfoItem ->
            if (commandParam.repoUrlMap.isNotBlank()) {
                val repoId = getRepoId(commandParam.repoUrlMap, commandParam.repoScmRelpathMap, scmInfoItem.url)
                LogUtils.printDebugLog("repoId: $repoId")
                scmInfoItem.repoId = repoId
            }
            scmInfoItem.taskId = taskId.toString()
            scmInfoItem.buildId = commandParam.landunParam.buildId

            scmInfoList.add(scmInfoItem)
        }
        val integrateOutPutFileText = jacksonObjectMapper().writeValueAsString(scmInfoList)
        File(outputFile).writeText(integrateOutPutFileText)
    }

    private fun getUrl(url: String): URL {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            URL(url)
        } else {
            val sshUrlArr = url.split(":")
            URL("http://" + sshUrlArr.first().removePrefix("git@") + "/" + sshUrlArr.last())
        }
    }

    private fun getRepoId(repoUrlMap: String, repoScmRelpathMap: String, url: String): String {
        val urlPath = getUrl(url).path
        val map = CodeccParamsHelper.transferStrToMap(repoUrlMap)
        val relPathMap = CodeccParamsHelper.transferStrToMap(repoScmRelpathMap)
        map.forEach {
            val relPath = relPathMap[it.key]
            var valuePath = getUrl(it.value).path
            if (relPath != null) {
                if (relPath.isNotEmpty()){
                    valuePath = valuePath.removeSuffix("/") + "/"+ relPath.removePrefix("/").removeSuffix("/")
                }
            }
            if (valuePath == urlPath) {
                return it.key
            }
        }
        return ""
    }

    override fun generateCmd(inputFile: String, outputFile: String): List<String> {
        val cmdList = mutableListOf<String>()
        if (commandParam.scmType == "git" || commandParam.scmType == "github") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/git_info.py --input=$inputFile --output=$outputFile")
        } else if (commandParam.scmType == "svn") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/svn_info.py --input=$inputFile --output=$outputFile")
        } else {
            LogUtils.printLog("scmType is empty")
            cmdList.clear()
        }
        return cmdList
    }

    override fun scmOpFail(inputFile: String) {
        LogUtils.printLog("scm info failed, upload $inputFile")
        CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_info_input.json", "SCM_JSON")
    }

    override fun uploadInputFile(inputFile: String) {
        CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_info_input.json", "SCM_JSON")
    }

    override fun scmOpSuccess(outputFile: String) {
        appendOutputFile(outputFile)
        LogUtils.printLog("scm info success")
        CodeccWeb.upload(commandParam.landunParam, outputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_info.json", "SCM_JSON")
        LogUtils.printLog("Upload scm info success")
    }

    override fun generateInputFile(): String {
        val inputFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "scm_info_input.json"
        val dirPathList = mutableListOf<String>()
        if (commandParam.repoRelPathMap.filterNot { it.key.isBlank() }.isNotEmpty()) {
            dirPathList.addAll(commandParam.repoRelPathMap.map { CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath + File.separator + it.value) })
        } else {
            dirPathList.add(CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath))
        }
        val inputData = mapOf("dir_path_list" to dirPathList)
        val inputDataStr = jacksonObjectMapper().writeValueAsString(inputData)
//        LogUtils.printDebugLog("scmInfo:inputDataStr: $inputDataStr")
        File(inputFile).writeText(inputDataStr)
        return inputFile
    }

    override fun generateOutputFile() = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "scm_info_output.json"
}