package com.tencent.devops.docker.scm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ScanType
import com.tencent.devops.docker.scm.pojo.ScmIncrementJson
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
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
            CodeccWeb.changScanType(commandParam.landunParam, analyzeConfigInfo.taskId, streamName, toolName)
        }
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
        CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_increment_input.json", "SCM_JSON")
    }

    override fun uploadInputFile(inputFile: String) {
        CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_increment_input.json", "SCM_JSON")
    }

    override fun scmOpSuccess(outputFile: String) {
        parseOutputFile(outputFile)
        CodeccWeb.upload(commandParam.landunParam, outputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_scm_increment_output.json", "SCM_JSON")
        LogUtils.printLog("scm increment success.")
    }

    override fun generateInputFile(): String {
        val inputFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "scm_increment_input.json"
        val scmIncrementList: MutableList<MutableMap<String, String>> = mutableListOf()
        commandParam.repoRelPathMap.forEach { repoRelPath ->
            val codePath = CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath + File.separator + repoRelPath.value)
            val scmIncrement = mutableMapOf(
                "workspacke_path" to codePath
            )
            if (null != analyzeConfigInfo.lastCodeRepos && analyzeConfigInfo.lastCodeRepos.isNotEmpty()) {
                analyzeConfigInfo.lastCodeRepos.forEach { repoRevison ->
                    if (repoRevison.repoId == repoRelPath.key) {
                        scmIncrement["pre_revision"] = repoRevison.revision ?: ""
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
//        LogUtils.printDebugLog("scmIncrement:inputDataStr: $inputDataStr")
        File(inputFile).writeText(inputDataStr)
        return inputFile
    }

    override fun generateOutputFile() = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "scm_increment_output.json"
}
