package com.tencent.devops.docker.scm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.docker.DockerRun
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCScmException
import com.tencent.devops.scm.GitBlame
import com.tencent.devops.scm.SvnBlame
import com.tencent.devops.scm.Utils
import com.tencent.devops.scm.pojo.ScmBlameVO
import com.tencent.devops.utils.FilePathUtils
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File
import java.nio.file.Files

class ScmBlame(
    override val commandParam: CommandParam,
    override val toolName: String,
    override val streamName: String,
    override val taskId: Long
) : Scm(commandParam, toolName, streamName, taskId) {

    private fun appendOutputFile(outputFile: String) {
        val outputFileText = File(outputFile).readText()
        val outputFileObj = jacksonObjectMapper().readValue<List<MutableMap<String, Any?>>>(outputFileText)
        outputFileObj.forEach {
            it["taskId"] = taskId
            val filePath = it["filePath"] as String
            if (it["fileRelPath"] == null || it["fileRelPath"].toString().isEmpty()) {
                it["fileRelPath"] = FilePathUtils.getRelPath(filePath)
            }
        }
        val outputFileTextWithTaskId = jacksonObjectMapper().writeValueAsString(outputFileObj)
        File(outputFile).writeText(outputFileTextWithTaskId)
    }

    override fun localRunCmd(inputFile: String, outputFile: String){
        var scmBlameList = mutableListOf<ScmBlameVO>()
        var inputFileList = mutableListOf<String>()
        var username = ""
        var password = ""
        val inputFileInfo = jacksonObjectMapper().readValue<Map<String, Any?>>(File(inputFile).readText())
        if (inputFileInfo["file_path_list"] != null) {
            inputFileList = inputFileInfo["file_path_list"] as MutableList<String>
        }
        if (inputFileInfo.containsKey("svn_user")){
            username = inputFileInfo["svn_user"] as String
        }

        if (inputFileInfo.containsKey("svn_password")){
            password = inputFileInfo["svn_password"] as String
        }

        inputFileList.forEach { filePath ->
            try {
                val scmType = Utils.checkScmDirPath(filePath)
                if (scmType.equals("git")) {
                    val scmInfo = GitBlame.blameRun(filePath)
                    scmBlameList.add(scmInfo)
                } else if (scmType.equals("svn")) {
                    val scmInfo = SvnBlame.blameRun(filePath, username, password)
                    scmBlameList.add(scmInfo)
                }
            }catch (e: Exception){
                LogUtils.printLog("[Warnnig] git blame $filePath failed, please check it?")
                return@forEach
            }

        }
        val scmBlameOutputFile = File(outputFile)
        scmBlameOutputFile.bufferedWriter().use { out -> out.write(JsonUtil.toJson(scmBlameList)) }
    }

    override fun generateCmd(inputFile: String, outputFile: String): List<String> {
        val cmdList = mutableListOf<String>()
        if (commandParam.scmType == "git" || commandParam.scmType == "github") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/git_blame.py --input=$inputFile --output=$outputFile")
        } else if (commandParam.scmType == "svn") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/svn_blame.py --input=$inputFile --output=$outputFile")
        } else if (commandParam.scmType == "perforce") {
            LogUtils.printLog("scmType is perforce, none cmd to run")
            cmdList.add("scmType is perforce, none cmd to run")
        } else {
            LogUtils.printLog("scmType is empty")
            cmdList.clear()
        }
        return cmdList
    }

    override fun scmOpFail(inputFile: String) {
        LogUtils.printLog("scm blame failed, upload input file...")
        CodeccWeb.upload(landunParam = commandParam.landunParam,
            filePath = inputFile,
            resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_blame_input.json",
            uploadType = "SCM_JSON",
            toolName = toolName)
    }

    override fun uploadInputFile(inputFile: String) {
        CodeccWeb.upload(landunParam = commandParam.landunParam,
            filePath = inputFile,
            resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_blame_input.json",
            uploadType = "SCM_JSON",
            toolName = toolName)
    }

    override fun scmOpSuccess(outputFile: String) {
        appendOutputFile(outputFile)
        LogUtils.printLog("scm blame success, upload $outputFile")
        CodeccWeb.upload(landunParam = commandParam.landunParam,
            filePath = outputFile,
            resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_blame.json",
            uploadType = "SCM_JSON",
            toolName = toolName)
    }

    override fun generateInputFile(): String {
        val toolDataPath = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName)
        val inputFile = toolDataPath + File.separator + "scm_blame_input.json"
        val toolScanInput = toolDataPath + File.separator + "tool_scan_output.json"
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

        LogUtils.printLog("start to get update file path list")

        //update filePathList
        currentMd5List.forEach { currentMd5 ->
            var status = true
            ccacheMd5List.forEach { ccacheMd5 ->
                if (currentMd5["filePath"] == ccacheMd5["filePath"]
                    && currentMd5["fileRelPath"] == ccacheMd5["fileRelPath"]
                    && currentMd5["md5"] == ccacheMd5["md5"]) {
                    status = false
                    return@forEach
                }
            }
            if (status) {
                filePathList.add(currentMd5["filePath"].toString())
            }
        }

        LogUtils.printLog("finish to get update file path list")

        val inputData = mutableMapOf<String, Any?>()
        inputData["file_path_list"] = filePathList
        if (commandParam.svnUser.isNotBlank()) {
            inputData["svn_user"] = commandParam.svnUser
            val svnVersion = "svn --version --quiet"
            try {
                val result = ScriptUtils.execute(
                    script = svnVersion,
                    dir = File(toolDataPath),
                    printErrorLog = false,
                    print2Logger = false
                )
                for (line: String in result.split("\n")) {
                    inputData["svn_version"] = line.trim()
                    break
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_SVN_COMMAND_RUN_FAIL,
                    "run command $svnVersion failed! ",
                    arrayOf(svnVersion)
                )
            }
        }
        if (commandParam.svnPassword.isNotBlank()) {
            inputData["svn_password"] = commandParam.svnPassword
        }
        val inputDataStr = jacksonObjectMapper().writeValueAsString(inputData)
        File(inputFile).writeText(inputDataStr)

        return inputFile
    }

    override fun generateLocalInputFile(): String {
        val toolDataPath = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName)
        val inputFile = toolDataPath + File.separator + "scm_blame_input.json"
        val toolScanInput = toolDataPath + File.separator + "md5_files"
        var currentMd5List = mutableListOf<MutableMap<String, String>>()
        val currentMd5FileList = jacksonObjectMapper().readValue<Map<String, Any?>>(File(toolScanInput).readText())
        if (currentMd5FileList["files_list"] != null) {
            currentMd5List = currentMd5FileList["files_list"] as MutableList<MutableMap<String, String>>
        }
        var fileList = mutableListOf<String>()
        currentMd5List.forEach { currentMd5 ->
            val fileOriginalPath = currentMd5["fileOriginalPath"] as String
            fileList.add(fileOriginalPath)
        }

        LogUtils.printLog("commandParam $commandParam")
        val inputData = mutableMapOf<String, Any?>()
        inputData["file_path_list"] = fileList
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

    override fun generateOutputFile() = ScanComposer.generateToolDataPath(commandParam.dataRootPath,
        streamName, toolName) + File.separator + "scm_blame_output.json"

    override fun runCmd(imageParam: ImageParam, inputFile: String, outputFile: String) {
        val startTime = System.currentTimeMillis()
        LogUtils.printLog("scm blame run cmd: ${commandParam.repos}")

        // perforce 代码库的 blame 逻辑，不需要走镜像脚本，直接用perforce sdk实现
        if (commandParam.scmType == "perforce") {
            val p4client = getP4Client()
            // scm_blame_input.json 获取带告警的文件路径列表
            val inputStr = String(Files.readAllBytes(File(inputFile).toPath()))
            val inputMap = JsonUtil.to<Map<String, Any?>>(inputStr)
            if (inputMap["file_path_list"] == null) {
                LogUtils.printLog("fail to blame perforce: input file path list is emoty")
                return
            }

            // 强转 file_path_list，进行 blame 操作
            val result = p4client.getChangeList((inputMap["file_path_list"] as MutableList<String>).toMutableSet())
            Files.write(File(outputFile).toPath(), JsonUtil.toJson(result).toByteArray())
        } else {
            try {
                DockerRun.runImage(imageParam, commandParam, toolName)
            } catch (e: Throwable) {
                LogUtils.printLog("Scm operate exception, message: ${e.message}")
                scmOpFail(inputFile)
                throw CodeCCScmException(
                    ErrorCode.SCM_COMMAND_RUN_FAIL,
                    e.message ?: ""
                )
            }
        }
        LogUtils.printLog("$toolName scm blame cost ${System.currentTimeMillis() - startTime}")
    }
}
