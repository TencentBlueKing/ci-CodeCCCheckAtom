package com.tencent.devops.docker.scm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.scm.pojo.ScmDiffItem
import com.tencent.devops.docker.scm.pojo.ScmDiffJson
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import java.io.File

class ScmDiff(
    override val commandParam: CommandParam,
    override val toolName: String,
    override val streamName: String,
    override val taskId: Long,

    private val incrementFiles: MutableList<String>,
    private val deleteFiles: MutableList<String>,
    private val diffFileList: MutableList<ScmDiffItem.DiffFileItem>
) : Scm(commandParam, toolName, streamName, taskId) {

    companion object {
        const val inputFileName = "git_branch_diff_input.json"
        const val outputFileName = "git_branch_diff_output.json"
    }

    private fun parseOutputFile(outputFile: String) {
        val outPutFileText = File(outputFile).readText()
        val outPutFileObj = jacksonObjectMapper().readValue<ScmDiffJson>(outPutFileText)

        outPutFileObj.scmIncrementList.forEach { scmIncrementInfo ->
            diffFileList.addAll(scmIncrementInfo.diffFileList)
            incrementFiles.addAll(scmIncrementInfo.updateFileList)
            deleteFiles.addAll(scmIncrementInfo.deleteFileList)
        }
        LogUtils.printDebugLog("generate diff out file content success: $diffFileList,\n $incrementFiles,\n $deleteFiles")
    }

    override fun generateCmd(inputFile: String, outputFile: String): List<String> {
        val cmdList = mutableListOf<String>()
        if (commandParam.scmType == "git" || commandParam.scmType == "github") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/git_branch_diff.py --input=$inputFile --output=$outputFile")
        } else {
            throw CodeccUserConfigException("only git or github is support in diff mode, current is ${commandParam.scmType}")
        }
        return cmdList
    }

    override fun scmOpFail(inputFile: String) {
        LogUtils.printLog("scm increment failed, upload the input file...")
        CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_$inputFileName", "SCM_JSON")
    }

    override fun uploadInputFile(inputFile: String) {
        CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_$inputFileName", "SCM_JSON")
    }

    override fun scmOpSuccess(outputFile: String) {
        parseOutputFile(outputFile)
        LogUtils.printLog("scm increment success.")
    }

    override fun generateInputFile(): String {
        val inputFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + inputFileName
        val hookRepoId = commandParam.extraPrams[CommandParam.extraHookRepoIdKey]

        commandParam.repoRelPathMap.forEach { repoRelPath ->
            if (repoRelPath.key == hookRepoId) {
                val inputData = mapOf(
                    "workspace" to listOf(CommonUtils.changePathToDocker(commandParam.projectBuildPath + File.separator + repoRelPath.value)),
                    "bk_ci_hook_source_branch" to commandParam.extraPrams[CommandParam.extraHookMrSourceBranchKey],
                    "bk_ci_hook_target_branch" to commandParam.extraPrams[CommandParam.extraHookMrTargetBranchKey]
                )
                val inputDataStr = jacksonObjectMapper().writeValueAsString(inputData)
                File(inputFile).writeText(inputDataStr)
                LogUtils.printDebugLog("generate diff input file content: $inputDataStr")
                return inputFile
            }
        }
        throw CodeccUserConfigException("GIT事件触发的代码库，跟本地拉取的代码库不一致")
    }

    override fun generateOutputFile() = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + outputFileName
}