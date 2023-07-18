package com.tencent.devops.docker.scm

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.docker.ScanComposer
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.docker.pojo.ScanType
import com.tencent.devops.docker.scm.pojo.ScmDiffItem
import com.tencent.devops.docker.scm.pojo.ScmDiffJson
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.user.CodeCCUserException
import com.tencent.devops.scm.GitBranchDiff
import com.tencent.devops.scm.pojo.IncrementFile
import com.tencent.devops.scm.pojo.ScmDiffVO
import java.io.File

class ScmDiff(
    override val commandParam: CommandParam,
    override val toolName: String,
    override val streamName: String,
    override val taskId: Long,

    private val scanType: ScanType,
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
        LogUtils.printDebugLog("generate diff out file content success: $diffFileList," +
                "\n $incrementFiles,\n $deleteFiles")
    }

    override fun localRunCmd(inputFile: String, outputFile: String){
        var scmIncrementList = mutableListOf<IncrementFile>()
        var inputFileList = mutableListOf<String>()
        var scmDiffVO = ScmDiffVO()
        var sourceBranch = ""
        var targetBranch = ""
        val inputFileInfo = jacksonObjectMapper().readValue<Map<String, Any?>>(File(inputFile).readText())
        if (inputFileInfo["workspace"] != null) {
            inputFileList = inputFileInfo["workspace"] as MutableList<String>
        }
        if (inputFileInfo.containsKey("bk_ci_hook_source_branch")) {
            sourceBranch = inputFileInfo["bk_ci_hook_source_branch"] as String
            //工蜂Pre-push时，源分支开头为refs/for的虚拟分支，该分支无代码变更，需转为FETCH_HEAD分支
            if (sourceBranch.startsWith("refs/for/")){
                sourceBranch = "FETCH_HEAD"
            }
        }

        if (inputFileInfo.containsKey("bk_ci_hook_target_branch")) {
            targetBranch = inputFileInfo["bk_ci_hook_target_branch"] as String
        }

        inputFileList.forEach { workspace ->
            if(sourceBranch.isBlank()){
                sourceBranch = GitBranchDiff.getSourceBranch(workspace)
            }
            if (commandParam.scmType == "git" || commandParam.scmType == "github") {
                scmDiffVO = GitBranchDiff.run(sourceBranch, targetBranch, workspace)
            } else {
                LogUtils.printLog("scmType is empty")
            }

            if (scmDiffVO.scmIncremt?.size!! > 0) {
                scmIncrementList.addAll(scmDiffVO.scmIncremt as MutableList<IncrementFile>)
            }
        }

        scmDiffVO.scmIncremt = scmIncrementList

        val scmDiffOutputFile = File(outputFile)
        scmDiffOutputFile.bufferedWriter().use { out -> out.write(JsonUtil.toJson(scmDiffVO)) }
    }

    override fun generateCmd(inputFile: String, outputFile: String): List<String> {
        val cmdList = mutableListOf<String>()
        if (commandParam.scmType == "git" || commandParam.scmType == "github") {
            cmdList.add("python3 /usr/codecc/scm_tools/src/git_branch_diff.py --input=$inputFile --output=$outputFile")
        } else {
            throw CodeCCUserException(
                ErrorCode.USER_DIFF_MODE_NOT_SUPPORT,
                "only git or github is support in diff mode, current is ${commandParam.scmType}"
            )
        }
        return cmdList
    }

    override fun scmOpFail(inputFile: String) {
        LogUtils.printLog("scm increment failed, upload the input file...")
        CodeccWeb.upload(landunParam = commandParam.landunParam,
            filePath = inputFile,
            resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_$inputFileName",
            uploadType = "SCM_JSON",
            toolName = toolName)
    }

    override fun uploadInputFile(inputFile: String) {
        CodeccWeb.upload(landunParam = commandParam.landunParam,
            filePath = inputFile,
            resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_$inputFileName",
            uploadType = "SCM_JSON",
            toolName = toolName)
    }

    override fun scmOpSuccess(outputFile: String) {
        parseOutputFile(outputFile)
        CodeccWeb.upload(landunParam = commandParam.landunParam,
                filePath = outputFile,
                resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_git_branch_diff_output.json",
                uploadType = "SCM_JSON",
                toolName = toolName)
        LogUtils.printLog("scm increment success.")
    }

    override fun generateInputFile(): String {
        val inputFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + inputFileName
        val hookRepoId = commandParam.extraPrams[CommandParam.extraHookRepoIdKey]

        if(scanType == ScanType.DIFF_BRANCH){
            return getDiffBranchInputFile(inputFile)
        }
        return getHookMrInputFile(inputFile,hookRepoId)
    }

    override fun generateLocalInputFile(): String {
        val inputFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + inputFileName
        val hookRepoId = commandParam.extraPrams[CommandParam.extraHookRepoIdKey]

        if(scanType == ScanType.DIFF_BRANCH){
            return getDiffBranchInputFile(inputFile)
        }
        return getHookMrInputFile(inputFile,hookRepoId)
    }

    private fun getDiffBranchInputFile(inputFile: String): String {
        if (commandParam.repos.isNullOrEmpty() || commandParam.repos.size != 1) {
            throw CodeCCUserException(
                ErrorCode.USER_DIFF_BRANCH_REPO_LIMIT,
                "差异分支扫描只允许设置一个代码库，多个代码库无法识别"
            )
        }
        val repoRelPath = commandParam.repos.first().relPath
        val inputData = mutableMapOf(
                "workspace" to listOf(CommonUtils.changePathToDocker(commandParam.projectBuildPath + File.separator + repoRelPath)),
                "bk_ci_hook_target_branch" to commandParam.extraPrams[CommandParam.diffBranch]
        )
        val inputDataStr = jacksonObjectMapper().writeValueAsString(inputData)
        File(inputFile).writeText(inputDataStr)
        LogUtils.printDebugLog("generate diff input file content: $inputDataStr")
        return inputFile
    }

    private fun getHookMrInputFile(inputFile: String, hookRepoId: String?): String {
        if (commandParam.extraPrams[CommandParam.extraHookMrSourceBranchKey] == null
            && commandParam.extraPrams[CommandParam.extraHookMrTargetBranchKey] == null){
            throw CodeCCUserException(
                ErrorCode.USER_MR_SOURCE_TARGET_NULL,
                "源分支和目标分支为null, 无法进行MR扫描，请检查触发方式是否MR模式？"
            )
        }
        var list = mutableListOf<String>()
        commandParam.repos.forEach { repo ->
            LogUtils.printDebugLog("hook mr: ${repo.relPath}")
            list.add(CommonUtils.changePathToDocker(commandParam.projectBuildPath + File.separator + repo.relPath))
        }
        if (list.size == 0){
            list.add(CommonUtils.changePathToDocker(commandParam.projectBuildPath))
        }
        val inputData = mapOf(
            "workspace" to list,
            "bk_ci_hook_source_branch" to commandParam.extraPrams[CommandParam.extraHookMrSourceBranchKey],
            "bk_ci_hook_target_branch" to commandParam.extraPrams[CommandParam.extraHookMrTargetBranchKey]
        )
        val inputDataStr = jacksonObjectMapper().writeValueAsString(inputData)
        File(inputFile).writeText(inputDataStr)
        LogUtils.printDebugLog("generate diff input file content: $inputDataStr")
        return inputFile
    }

    override fun generateOutputFile() = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + outputFileName
    override fun runCmd(imageParam: ImageParam, inputFile: String, outputFile: String) {

    }
}
