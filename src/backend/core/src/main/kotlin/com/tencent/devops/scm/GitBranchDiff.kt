package com.tencent.devops.scm

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCScmException
import com.tencent.devops.scm.pojo.FileDiffLines
import com.tencent.devops.scm.pojo.IncrementFile
import com.tencent.devops.scm.pojo.ScmDiffVO
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File

/**
 * 该类实现两个Git分支的对比差异，获取差异文件列表和变更行数
 * {
 * "diffLineList": [ 1,2,3,4,13],
 * "filePath": "/devops/scm/pojo/ScmInfoVO.kt"
 *  }
 */
object GitBranchDiff {

    /**
     * @Description ["检查当前workspace是否已切换为源分支"]
     * @date 2021/9/7
     */
    fun checkWorkspace(workspace: String, sourceBranch: String): Boolean {
        val workspacePath = File(workspace)
        if (workspacePath.isDirectory) {
            val cmd = "git branch"
            try {
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = workspacePath,
                    printErrorLog = false,
                    print2Logger = false
                )
                for (line: String in result.split("\n")) {
                    if (line.contains("*")) {
                        val gitBranch = line.trim().split("*")[1]
                        if (gitBranch.equals(sourceBranch) || gitBranch.contains("devops-virtual-branch")) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }
        }
        LogUtils.printLog("the workspace $workspace is branch $sourceBranch please check it")

        return false
    }

    /**
     * @Description ["检查当前workspace是否已切换为源分支"]
     * @date 2021/9/7
     */
    fun getSourceBranch(workspace: String): String {
        val workspacePath = File(workspace)
        if (workspacePath.isDirectory) {
            val cmd = "git branch"
            try {
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = workspacePath,
                    printErrorLog = false,
                    print2Logger = false
                )
                for (line: String in result.split("\n")) {
                    if (line.contains("*")) {
                        return line.trim().split("*")[1].trim()

                    }
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }
        }
        LogUtils.printLog("the workspace $workspace is not checkout branch please check it")

        return ""
    }

    /**
     * @Description ["通过源分支和目标分支，获取差异文件列表"]
     * @date 2021/9/7
     */
    fun getDiffFileList(workspace: String, sourceBranch: String, targetBranch: String): MutableSet<String> {
        val diffFileList = mutableSetOf<String>()
        val workspacePath = File(workspace)
        if (workspacePath.isDirectory) {
            val changePathRegex = Regex(".* => .*")
            var cmd = "git diff origin/$targetBranch...origin/$sourceBranch --numstat"
            //如果源分支名称为FETCH_HEAD，则是虚拟分支，无需添加origin
            if (sourceBranch.equals("FETCH_HEAD")){
                cmd = "git diff origin/$targetBranch...$sourceBranch --numstat"
            }
            try {
                LogUtils.printLog("diff file list with $sourceBranch...$targetBranch")
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = workspacePath,
                    printErrorLog = false,
                    print2Logger = false
                )
                for (line: String in result.split("\n")) {
                    if (line.trim().equals("")) {
                        continue
                    }
                    var relFilePath = line.trim().split("\t")[2]
                    LogUtils.printDebugLog("relFilePath：$relFilePath ${relFilePath.matches(changePathRegex)}")
                    if (relFilePath.matches(changePathRegex)) {
                        if (relFilePath.contains("{") && relFilePath.contains("}")) {
                            //目录迁移匹配：/main/java/com/{business => }/impol/test.java to /main/java/com/impol/test.java
                            val pathList = relFilePath
                                .substring(relFilePath.indexOf("{")+1, relFilePath.indexOf("}"))
                                .split(" => ")
                            val newPath = relFilePath
                                .substring(0,relFilePath.indexOf("{")) +
                                    pathList.get(1) +
                                    relFilePath.substring(relFilePath.indexOf("}")+1,relFilePath.length)
                            relFilePath = newPath.replace("//","/")
                        }else {
                            //目录迁移匹配：test.java => /impol/test.java to /impol/test.java
                            val pathList = relFilePath.split(" => ")
                            relFilePath = pathList.get(1)
                        }
                    }
                    val filePath = File(workspace + File.separator + relFilePath)
                    if (!filePath.isFile) {
                        continue
                    }
                    diffFileList.add(filePath.canonicalPath.replace("//", "/"))
                }
            } catch (e: Exception) {
                LogUtils.printLog("run command $cmd failed! ")
            }
        }
        return diffFileList
    }

    /**
     * @Description ["通过源分支和目标分支，获取差异文件的变更行数"]
     * @date 2021/9/7
     */
    fun getDiffFileChangeLines(
        workspace: String,
        sourceBranch: String,
        targetBranch: String,
        filePath: String
    ): FileDiffLines {
        var fileDiffLines = FileDiffLines()
        val workspacePath = File(workspace)
        val linesList = mutableListOf<Int>()
        var startLine = ""
        var lineAccount = 0
        if (workspacePath.isDirectory) {
            var cmd = "git diff origin/$targetBranch...origin/$sourceBranch $filePath"
            //如果源分支名称为FETCH_HEAD，则是虚拟分支，无需添加origin
            if (sourceBranch.equals("FETCH_HEAD")){
                cmd = "git diff origin/$targetBranch...$sourceBranch $filePath"
            }
            try {
                LogUtils.printLog("diff lines with $sourceBranch...$targetBranch in $filePath")
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = workspacePath,
                    printErrorLog = false,
                    print2Logger = false
                )
                LogUtils.printDebugLog("diff content as ${result}")
                for (line: String in result.split("\n")) {
                    if (Regex("^@@.*").matches(line.trim())) {
                        val strTmp = line.trim().split("@@")[1]
                        startLine = strTmp.split("+")[1].split(",")[0]
                        lineAccount = 0
                    } else if (Regex("^-.*").matches(line.trim()) ||
                               Regex("^---.*").matches(line.trim()) ||
                               Regex("^\\+\\+\\+.*").matches(
                            line.trim()
                        )
                    ) {
                        continue
                    } else if (Regex("^\\+.*").matches(line.trim())) {
                        LogUtils.printDebugLog("change from startLine is ${startLine}")
                        LogUtils.printDebugLog("$filePath file at ${startLine.toInt() + lineAccount} " +
                                "line is changed, need add in fileDiffLines")
                        linesList.add(startLine.toInt() + lineAccount)
                        lineAccount += 1
                    } else {
                        lineAccount += 1
                    }
                }
            } catch (e: Exception) {
                LogUtils.printLog("run command $cmd failed! ")
            }

            if (linesList.size > 0) {
                if (linesList.contains(1)) {
                    linesList.add(0, 0)
                }
                fileDiffLines.filePath = filePath
                fileDiffLines.diffLineList = linesList
            }
        }
        return fileDiffLines
    }

    /**
     * @Description ["通过源分支和目标分支，获取workspace变更数据"]
     * @date 2021/9/7
     */
    fun run(sourceBranch: String, targetBranch: String, workspace: String): ScmDiffVO {
        val scmDiffVO = ScmDiffVO()
        val incrementFile = IncrementFile()
        val incrementRepoList = mutableListOf<IncrementFile>()
        val diffFileList = mutableListOf<FileDiffLines>()
        val fileList = getDiffFileList(workspace, sourceBranch, targetBranch)
        incrementFile.updateFileList = fileList
        for (filePath: String in fileList) {
            val file = File(filePath)
            if (!file.isFile) {
                continue
            }
            val fileDiffLines = getDiffFileChangeLines(workspace, sourceBranch, targetBranch, filePath)
            if (!fileDiffLines.equals(FileDiffLines())) {
                diffFileList.add(fileDiffLines)
            }
        }
        incrementFile.diffFileList = diffFileList
        incrementRepoList.add(incrementFile)
        if (incrementRepoList.size > 0) {
            scmDiffVO.scmIncremt = incrementRepoList
        }
        return scmDiffVO
    }
}