package com.tencent.devops.docker.scm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import java.io.File

class ScmBlame(
    override val commandParam: CommandParam,
    override val toolName: String,
    override val streamName: String,
    override val taskId: Long
) : Scm(commandParam, toolName, streamName, taskId) {

    private fun appendOutputFile(outputFile: String) {
        val outputFileText = File(outputFile).readText()
//        LogUtils.printDebugLog("output file json: $outputFileText")
        val outputFileObj = jacksonObjectMapper().readValue<List<MutableMap<String, Any?>>>(outputFileText)
        outputFileObj.forEach {
            it["taskId"] = taskId
        }
        val outputFileTextWithTaskId = jacksonObjectMapper().writeValueAsString(outputFileObj)
        File(outputFile).writeText(outputFileTextWithTaskId)
    }

    override fun generateCmd(inputFile: String, outputFile: String): List<String> {
        val cmdList = mutableListOf<String>()
        if (commandParam.scmType == "git" || commandParam.scmType == "github") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/git_blame.py --input=$inputFile --output=$outputFile")
//            if (toolName == ToolConstants.COVERITY || toolName == ToolConstants.KLOCWORK) {
//                cmdList.add("python /usr/codecc/scm_tools/src/git_blame_cov_kw.py --input=$inputFile --output=$outputFile")
//            } else {
//                cmdList.add("python /usr/codecc/scm_tools/src/git_blame.py --input=$inputFile --output=$outputFile")
//            }
        } else if (commandParam.scmType == "svn") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/svn_blame.py --input=$inputFile --output=$outputFile")
        } else {
            LogUtils.printLog("scmType is empty")
            cmdList.clear()
        }
        return cmdList
    }

    override fun scmOpFail(inputFile: String) {
        LogUtils.printLog("scm blame failed, upload input file...")
        CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_blame_input.json", "SCM_JSON")
    }

    override fun uploadInputFile(inputFile: String) {
        CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_blame_input.json", "SCM_JSON")
    }

    override fun scmOpSuccess(outputFile: String) {
        appendOutputFile(outputFile)
        LogUtils.printLog("scm blame success, upload $outputFile")
        CodeccWeb.upload(commandParam.landunParam, outputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_blame.json", "SCM_JSON")
    }

    override fun generateInputFile(): String {
        val toolDataPath = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName)
        val inputFile = toolDataPath + File.separator + "scm_blame_input.json"
        val toolScanInput = toolDataPath + File.separator + "tool_scan_output.json"
//        val filePathList = CodeccConfig.fileListFromDefects(toolScanInput)
        val filePathList = mutableSetOf<String>()
        //get current Md5 file
        var currentMd5List = mutableListOf<MutableMap<String, String>>()
        val md5outputFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "md5_files"
        val currentMd5FileList = jacksonObjectMapper().readValue<Map<String, Any?>>(File(md5outputFile).readText())
        if (currentMd5FileList["files_list"] != null) {
            currentMd5List = currentMd5FileList["files_list"] as MutableList<MutableMap<String, String>>
        }
        //get ccache Md5 file
        var ccacheMd5List = mutableListOf<MutableMap<String, String>>()
        val ccacheMd5 = CodeccWeb.getMD5ForBlame(commandParam.landunParam, taskId, toolName.toUpperCase())
        val ccacheMd5FileList = jacksonObjectMapper().readValue<Map<String, Any?>>(ccacheMd5)
        if (ccacheMd5FileList["data"] != null) {
            ccacheMd5List = ccacheMd5FileList["data"] as MutableList<MutableMap<String, String>>
        }
        //update filePathList
        currentMd5List.forEach { currentMd5 ->
            var status = true
            ccacheMd5List.forEach { ccacheMd5 ->
                if (currentMd5["filePath"] == ccacheMd5["filePath"] || currentMd5["fileRelPath"] == ccacheMd5["fileRelPath"]) {
                    status = false
                    if (currentMd5["md5"] != ccacheMd5["md5"]) {
                        filePathList.add(currentMd5["filePath"].toString())
                    }
                }
            }
            if (status) {
                filePathList.add(currentMd5["filePath"].toString())
            }
        }
        val inputData = mutableMapOf<String, Any?>()
        inputData["file_path_list"] = filePathList
        if (commandParam.svnUser.isNotBlank()) {
            inputData["svn_user"] = commandParam.svnUser
        }
        if (commandParam.svnPassword.isNotBlank()) {
            inputData["svn_password"] = commandParam.svnPassword
        }
        val inputDataStr = jacksonObjectMapper().writeValueAsString(inputData)
        File(inputFile).writeText(inputDataStr)

        return inputFile
    }

    override fun generateOutputFile() = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "scm_blame_output.json"
}