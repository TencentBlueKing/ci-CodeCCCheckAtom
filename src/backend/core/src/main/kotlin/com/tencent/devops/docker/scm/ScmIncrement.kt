package com.tencent.devops.docker.scm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.docker.pojo.ScanType
import com.tencent.devops.docker.scm.pojo.ScmIncrementJson
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.scan.SetForceFullScanReqVO
import com.tencent.devops.scm.GitIncrement
import com.tencent.devops.scm.GitInfo
import com.tencent.devops.scm.SvnIncrement
import com.tencent.devops.scm.SvnInfo
import com.tencent.devops.scm.pojo.IncrementFile
import com.tencent.devops.scm.pojo.ScmIncrementVO
import com.tencent.devops.scm.pojo.ScmInfoVO
import com.tencent.devops.scm.pojo.SubModule
import java.io.File

class ScmIncrement(
    override val commandParam: CommandParam,
    override val toolName: String,
    override val streamName: String,
    override val taskId: Long,

    private val analyzeConfigInfo: AnalyzeConfigInfo,
    private val incrementFiles: MutableList<String>,
    private val deleteFiles: MutableList<String>
) : Scm(commandParam, toolName, streamName, taskId) {

    private fun parseOutputFile(outputFile: String) {
        val outPutFileText = File(outputFile).readText()
        val outPutFileObj = jacksonObjectMapper().readValue<ScmIncrementJson>(outPutFileText)

        if (outPutFileObj.scmIncrementList != null) {
            outPutFileObj.scmIncrementList.forEach { scmIncrementInfo ->
                incrementFiles.addAll(scmIncrementInfo.updateFileList)
                deleteFiles.addAll(scmIncrementInfo.deleteFileList)
            }
        }

        // isPreRevision 为false，说明没有版本文件，则改成全量
        if (outPutFileObj.isPreRevision != null && !outPutFileObj.isPreRevision) {
            LogUtils.printLog("scan project for full with check_scm_increment ...")

            analyzeConfigInfo.scanType = ScanType.FULL

            val toolNames = mutableListOf(toolName.toUpperCase())

            CodeccSdkApi.changeScanType(
                analyzeConfigInfo.taskId,
                SetForceFullScanReqVO(commandParam.landunParam.buildId, toolNames)
            )
        }
    }

    override fun localRunCmd(inputFile: String, outputFile: String) {
        var scmIncrementList = mutableListOf<IncrementFile>()
        var inputFileList = mutableListOf<Map<String, Any?>>()
        var scmIncrementVO = ScmIncrementVO()
        var username = ""
        var password = ""
        val inputFileInfo = jacksonObjectMapper().readValue<Map<String, Any?>>(File(inputFile).readText())
        if (inputFileInfo["scm_increment"] != null) {
            inputFileList = inputFileInfo["scm_increment"] as MutableList<Map<String, Any?>>
        }
        if (inputFileInfo.containsKey("svn_user")) {
            username = inputFileInfo["svn_user"] as String
        }

        if (inputFileInfo.containsKey("svn_password")) {
            password = inputFileInfo["svn_password"] as String
        }

        LogUtils.printLog("inputFileList $inputFileList")

        inputFileList.forEach { scmIncrementInfo ->
            if (commandParam.scmType == "git" || commandParam.scmType == "github") {
                scmIncrementVO = GitIncrement.run(
                    scmIncrementInfo["pre_revision"] as String,
                    scmIncrementInfo["workspacke_path"] as String,
                    scmIncrementInfo["submodules"] as MutableList<Map<String, Any?>>
                )
            } else if (commandParam.scmType == "svn") {
                scmIncrementVO = SvnIncrement.run(
                    scmIncrementInfo["pre_revision"] as String,
                    scmIncrementInfo["workspacke_path"] as String,
                    username,
                    password
                )
            } else {
                LogUtils.printLog("scmType is empty")
            }
        }

        val scmBlameOutputFile = File(outputFile)
        scmBlameOutputFile.bufferedWriter().use { out -> out.write(JsonUtil.toJson(scmIncrementVO)) }
    }

    override fun generateCmd(inputFile: String, outputFile: String): List<String> {
        val cmdList = mutableListOf<String>()
        if (commandParam.scmType == "git" || commandParam.scmType == "github") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/git_increment.py --input=$inputFile --output=$outputFile")
        } else if (commandParam.scmType == "svn") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/svn_increment.py --input=$inputFile --output=$outputFile")
        } else {
            LogUtils.printLog("scmType is empty")
            cmdList.clear()
        }
        return cmdList
    }

    override fun scmOpFail(inputFile: String) {
        LogUtils.printLog("scm increment failed, upload the input file...")
        CodeccWeb.upload(
            landunParam = commandParam.landunParam,
            filePath = inputFile,
            resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_increment_input.json",
            uploadType = "SCM_JSON",
            toolName = toolName
        )
    }

    override fun uploadInputFile(inputFile: String) {
        CodeccWeb.upload(
            landunParam = commandParam.landunParam,
            filePath = inputFile,
            resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_increment_input.json",
            uploadType = "SCM_JSON",
            toolName = toolName
        )
    }

    override fun scmOpSuccess(outputFile: String) {
        parseOutputFile(outputFile)
        CodeccWeb.upload(
            landunParam = commandParam.landunParam,
            filePath = outputFile,
            resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_increment_output.json",
            uploadType = "SCM_JSON",
            toolName = toolName
        )
        LogUtils.printLog("scm increment success.")
    }

    override fun generateInputFile(): String {
        val inputFile = ScanComposer.generateToolDataPath(
            commandParam.dataRootPath,
            streamName,
            toolName
        ) + File.separator + "scm_increment_input.json"
        val scmIncrementList: MutableList<MutableMap<String, Any>> = mutableListOf()
        commandParam.repos.forEach { repo ->
            val codePath =
                CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath + File.separator + repo.relPath)
            val scmIncrement = mutableMapOf(
                "workspacke_path" to codePath,
                "pre_revision" to "",
                "submodules" to mutableListOf<SubModule>()
            )
            if (null != analyzeConfigInfo.lastCodeRepos && analyzeConfigInfo.lastCodeRepos.isNotEmpty()) {
                analyzeConfigInfo.lastCodeRepos.forEach { lastRepo ->
                    if (lastRepo.url == repo.url) {
                        scmIncrement["pre_revision"] = lastRepo.revision ?: ""
                        scmIncrement["submodules"] = lastRepo.subModules ?: mutableListOf<SubModule>()
                    }
                }
            } else {
                scmIncrement["pre_revision"] = ""
            }

            LogUtils.printLog("scmIncrement $scmIncrement")

            scmIncrementList.add(scmIncrement)
        }

        val inputData = mutableMapOf<String, Any?>()
        inputData["scm_increment"] = scmIncrementList
        if (commandParam.svnUser.isNotBlank()) {
            inputData["svn_user"] = commandParam.svnUser
        }
        if (commandParam.svnPassword.isNotBlank()) {
            inputData["svn_password"] = commandParam.svnPassword
        }

        val inputDataStr = jacksonObjectMapper().writeValueAsString(inputData)
//        LogUtils.printDebugLog("scmIncrement:inputDataStr: $inputDataStr")
        File(inputFile).writeText(inputDataStr)
        return inputFile
    }

    override fun generateLocalInputFile(): String {
        val inputFile = ScanComposer.generateToolDataPath(
            commandParam.dataRootPath,
            streamName,
            toolName
        ) + File.separator + "scm_increment_input.json"
        val scmIncrementList: MutableList<MutableMap<String, Any>> = mutableListOf()
        commandParam.repos.forEach { repo ->
            val codePath =
                CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath + File.separator + repo.relPath)
            val scmIncrement = mutableMapOf(
                "workspacke_path" to codePath,
                "pre_revision" to "",
                "submodules" to mutableListOf<SubModule>()
            )
            if (null != analyzeConfigInfo.lastCodeRepos && analyzeConfigInfo.lastCodeRepos.isNotEmpty()) {
                analyzeConfigInfo.lastCodeRepos.forEach { lastRepo ->
                    if (lastRepo.url == repo.url) {
                        scmIncrement["pre_revision"] = lastRepo.revision ?: ""
                        scmIncrement["submodules"] = lastRepo.subModules ?: mutableListOf<SubModule>()
                    }
                }
            } else {
                scmIncrement["pre_revision"] = ""
            }
            scmIncrementList.add(scmIncrement)
        }

        val inputData = mutableMapOf<String, Any?>()
        inputData["scm_increment"] = scmIncrementList
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

    override fun generateOutputFile() = ScanComposer.generateToolDataPath(
        commandParam.dataRootPath,
        streamName,
        toolName
    ) + File.separator + "scm_increment_output.json"

    override fun runCmd(imageParam: ImageParam, inputFile: String, outputFile: String) {

    }
}
