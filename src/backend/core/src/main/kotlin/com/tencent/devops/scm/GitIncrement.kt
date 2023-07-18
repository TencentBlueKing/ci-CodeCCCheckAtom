package com.tencent.devops.scm

import com.google.common.base.Strings
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.OSType
import com.tencent.devops.scm.pojo.IncrementFile
import com.tencent.devops.scm.pojo.ScmIncrementVO
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File

/**
 * 该类实现两个Git版本的对比差异，获取差异文件列表
 * "updateFileList": ["/scan/bin/common/config.py"]
 */
object GitIncrement {

    /**
     * @Description ["关键字按系统获取"]
     * @date 2021/9/7
     */
    fun getKey(): String {
        return when (CodeccEnvHelper.getOS()) {
            OSType.LINUX, OSType.MAC_OS -> {
                "^"
            }
            OSType.WINDOWS -> {
                "^^"
            }
            else -> {
                "^"
            }
        }
    }

    /**
     * @Description ["检查版本是否存在"]
     * @date 2021/9/7
     */
    fun checkRevision(preRevision: String, workSpacePath: File): Boolean {
        val cmd = "git cat-file -t $preRevision"
        try {
            val result = ScriptUtils.execute(
                script = cmd,
                dir = workSpacePath,
                printErrorLog = false,
                print2Logger = false
            )
            if (!result.trim().contains("commit")) {
                LogUtils.printLog("pre revision $preRevision is not exist")
                return false
            }
        } catch (e: Exception) {
            LogUtils.printLog("pre revision $preRevision is not exist")
            return false
        }
        return true
    }

    /**
     * @Description ["获取增量数据"]
     * @date 2021/9/7
     */
    fun run(preRevision: String, workSpace: String, subModules: MutableList<Map<String, Any?>>): ScmIncrementVO {
        var incrementRepoList = mutableListOf<IncrementFile>()
        val workSpacePath = File(workSpace)
        var preRevision = preRevision
        var incrementVO = ScmIncrementVO()
        var gitDirPath = ""
        if (workSpacePath.isDirectory) {
            var cmd = ""
            try {
                cmd = "git ls-remote --get-url"
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = workSpacePath,
                    printErrorLog = false,
                    print2Logger = false
                )
                for (line: String in result.split("\n")) {
                    if (!line.contains("fatal:")) {
                        gitDirPath = Utils.findGitDirPath(workSpace)
                    }
                }
            } catch (e: Exception) {
                LogUtils.printLog("run command $cmd failed! ")
            }

            if (preRevision.equals("")) {
                preRevision = "HEAD" + getKey()
            } else {
                if (
                    checkRevision(preRevision, workSpacePath)) {
                    incrementVO.isPreRevision = true
                } else {
                    preRevision = ""
                }
            }

            if (!gitDirPath.isNullOrEmpty() && !preRevision.isNullOrEmpty()) {
                var incrementFile = incrementRun(gitDirPath, preRevision)
                incrementFile = submoduleRun(incrementFile, gitDirPath, subModules)
                incrementRepoList.add(incrementFile)
            }
            if (incrementRepoList.size > 0) {
                incrementVO.scmIncremt = incrementRepoList
            }
        }
        return incrementVO
    }

    /**
     * @Description ["获取submoduel增量数据"]
     * @date 2021/9/7
     */
    fun submoduleRun(incrementFile: IncrementFile, gitDirPath: String, subModules: MutableList<Map<String, Any?>>): IncrementFile {
        val updateFileList = incrementFile.updateFileList
        val deleteFileList = incrementFile.deleteFileList
        val gitModulePath = File(gitDirPath + "/.gitmodules")
        if (gitModulePath.isFile) {
            gitModulePath.readLines().forEach { line ->
                if (line.contains("=") && line.contains("path")) {
                    var path = line.split("=")[1].replace(" ", "")
                    val subModulePath = File(gitDirPath + File.separator + path)
                    if (subModulePath.isDirectory && subModules.size > 0) {
                        var revision = "HEAD" + getKey()
                        for (subModule in subModules) {
                            LogUtils.printLog("subModule: $subModule, current folder: ${path}")
                            if (path.contains(subModule["subModule"]!!.toString())){
                                revision = (subModule["commitId"] as String?)!!
                                break
                            }
                        }
                        val incrementCommand = "git diff HEAD " + revision + " --name-only"
                        LogUtils.printLog("git module increment command: $incrementCommand")
                        try {
                            val subModuleResult = ScriptUtils.execute(
                                script = incrementCommand,
                                dir = subModulePath,
                                printErrorLog = false,
                                print2Logger = false
                            )
                            for (subline: String in subModuleResult.split("\n")) {
                                val filePath = File(subModulePath.canonicalPath + File.separator + subline.trim())
                                if (filePath.isFile && !Utils.isBinary(filePath)) {
                                    updateFileList?.add(filePath.canonicalPath.replace("//", "/"))
                                } else {
                                    if (!filePath.isDirectory) {
                                        deleteFileList?.add(filePath.canonicalPath.replace("//", "/"))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            LogUtils.printErrorLog("run command $incrementCommand failed in $path ")
                        }
                    }
                }
            }
        }

        incrementFile.updateFileList = updateFileList
        incrementFile.deleteFileList = deleteFileList

        return incrementFile
    }

    /**
     * @Description ["获取增量数据"]
     * @date 2021/9/7
     */
    fun incrementRun(gitDirPath: String, preRevision: String): IncrementFile {
        val updateFileList = mutableSetOf<String>()
        val deleteFileList = mutableSetOf<String>()
        val getLatestVersion = "git rev-parse --short HEAD"
        val incrementCommand = "git diff HEAD $preRevision --name-only"
        var incrementRenameCommand = "git log HEAD...$preRevision --summary | grep \'" + getKey() + " rename\'"
        if (CodeccEnvHelper.getOS().equals(OSType.WINDOWS)) {
            incrementRenameCommand = "git log HEAD...$preRevision --summary | findstr \"" + getKey() + " rename\""
        }
        var incrementFile = IncrementFile()
        try {
            val result = ScriptUtils.execute(
                script = getLatestVersion,
                dir = File(gitDirPath),
                printErrorLog = false,
                print2Logger = false
            )
            for (line: String in result.split("\n")) {
                incrementFile.latestRevision = line.trim() as String
                LogUtils.printLog("diffSourceVersion: $preRevision")
                LogUtils.printLog("diffTargetVersion: ${incrementFile.latestRevision}")
                break
            }
        } catch (e: Exception) {
            LogUtils.printLog("run command $getLatestVersion failed! ")
        }

        try {
            val result = ScriptUtils.execute(
                script = incrementCommand,
                dir = File(gitDirPath),
                printErrorLog = false,
                print2Logger = false
            )
            for (line: String in result.split("\n")) {
                val filePath = File(gitDirPath + File.separator + line.trim())
                if (filePath.isFile && !Utils.isBinary(filePath)) {
                    updateFileList.add(filePath.canonicalPath.replace("//", "/"))
                } else {
                    if (!filePath.isDirectory) {
                        deleteFileList.add(filePath.canonicalPath.replace("//", "/"))
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.printLog("run command $incrementCommand failed! ")
        }

        try {
            val result = ScriptUtils.execute(
                script = incrementRenameCommand,
                dir = File(gitDirPath),
                printErrorLog = false,
                print2Logger = false
            )
            for (line: String in result.split("\n")) {
                //获取字符串如： rename src/main/kotlin/com/tencent/devops/scm/{ => pojo}/ChangeRecord.kt (100%)
                var movePath = line.replace("rename", "").replace("(100%)", "").trim()
                if (!movePath.contains("{") && !movePath.contains("}") && movePath.contains("=>")) {
                    movePath = movePath.split("=>")[1].replace(" ", "")
                } else if (movePath.contains("{") && movePath.contains("}") && movePath.contains("=>")) {
                    movePath = movePath.replace("{ => ", "").replace("}/", "")
                }
                val filePath = File(gitDirPath + File.separator + movePath.trim())
                if (filePath.isFile && !Utils.isBinary(filePath)) {
                    updateFileList.add(filePath.canonicalPath.replace("//", "/"))
                } else {
                    if (!filePath.isDirectory) {
                        deleteFileList.add(filePath.canonicalPath.replace("//", "/"))
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.printLog("run command $incrementRenameCommand failed! ")
        }

        //获取未暂存文件
        val gitStatusUntrackedFiles = "git status -s"
        try {
            val result = ScriptUtils.execute(
                script = gitStatusUntrackedFiles,
                dir = File(gitDirPath),
                printErrorLog = false,
                print2Logger = false
            )
            for (line: String in result.split("\n")) {
                if (line.matches(Regex("^\\?\\? ")) || line.matches(Regex("^M "))) {
                    val filePath =
                        File(gitDirPath + File.separator + line.replace("??", "").replace("M ", "").trim())
                    if (filePath.isFile && !Utils.isBinary(filePath)) {
                        print("git untracked file: " + filePath.canonicalFile)
                        updateFileList.add(filePath.canonicalPath.replace("//", "/"))
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.printLog("run command $gitStatusUntrackedFiles failed! ")
        }

        incrementFile.updateFileList = updateFileList
        incrementFile.deleteFileList = deleteFileList

        return incrementFile
    }
}