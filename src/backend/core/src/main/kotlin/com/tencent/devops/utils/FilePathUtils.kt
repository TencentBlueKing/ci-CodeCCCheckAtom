package com.tencent.devops.utils

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CommonUtils
import java.io.File

/**
 * 统一处理路径与相对路径
 */
object FilePathUtils {

    private var commandParam : CommandParam? = null


    fun init(commandParam: CommandParam) {
        this.commandParam = commandParam
    }

    fun getRelPath(filePath: String): String {
        if (commandParam == null) {
            return "/"
        }
        val workspace = try{
            when (commandParam!!.scmType) {
                "git", "github" -> {
                    getGitDir(filePath)
                }
                "svn" -> {
                    getSvnDir(filePath)
                }
                else -> {
                    ""
                }
            }
        }catch (e : Exception){
            ""
        }
        val relPath = if (workspace.isEmpty() || workspace == "/") {
            filePath.replace(getWorkspaceDir(), "/")
                    .replace("//", "/")
        } else {
            filePath.replace(workspace, "/")
                    .replace("//", "/")
        }
        LogUtils.printDebugLog("$filePath relPath is $relPath")
        return relPath
    }

    private fun getGitDir(filePath: String): String {
        var scanDir = File(filePath).parent
        var clyeNum = 4096
        while (clyeNum > 0) {
            scanDir = "$scanDir/.git"
            if (File(scanDir).isDirectory) {
                break
            } else if (File(scanDir).isFile) {
                break
            } else {
                scanDir = File(File(scanDir).parent).parent
                if (scanDir == "/" || scanDir.endsWith(":/") || scanDir.endsWith(":\\")) {
                    return ""
                }
                clyeNum--
            }
        }
        return File(scanDir).parent.replace("\\", "/")
    }

    private fun getSvnDir(filePath: String): String {
        var scanDir = File(filePath).parent
        var clyeNum = 4096
        while (clyeNum > 0) {
            scanDir = "$scanDir/.svn"
            if (File(scanDir).isDirectory) {
                break
            } else if (File(scanDir).isFile) {
                break
            } else {
                scanDir = File(File(scanDir).parent).parent
                if (scanDir == "/" || scanDir.endsWith(":/") || scanDir.endsWith(":\\")) {
                    return ""
                }
                clyeNum--
            }
        }
        return File(scanDir).parent.replace("\\", "/")
    }

    private fun getWorkspaceDir(): String {
        return CommonUtils.changePathToDocker(File(commandParam!!.landunParam.streamCodePath).canonicalPath)
                .replace("//", "/").replace("\\", "/")
    }
}