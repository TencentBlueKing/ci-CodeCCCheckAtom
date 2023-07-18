package com.tencent.devops.scm

import com.google.common.base.Strings
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCScmException
import com.tencent.devops.scm.pojo.ScmInfoVO
import com.tencent.devops.scm.pojo.SubModule
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File

/**
 * 该类实现获取Git库相关信息
 * {
 * "branch": "master",
 * "commitID": "69d9b5ce9af897d144f756b329cd45b0f2c2d645",
 * "fileUpdateAuthor": "xxx",
 * "fileUpdateTime": 1634625066000,
 * "revision": "69d9b5c",
 * "scmType": "git",
 * "subModules": [],
 * "url": "https://xxx.com/origin-test.git"
 * }
 */
object GitInfo {

    /**
     * @Description ["关键字按系统获取"]
     * @date 2021/9/7
     */
    fun getKey(): String {
        return when (CodeccEnvHelper.getOS()) {
            OSType.LINUX, OSType.MAC_OS -> {
                "%"
            }
            OSType.WINDOWS -> {
                "%%"
            }
            else -> {
                "%"
            }
        }
    }

    /**
     * @Description ["获取info信息"]
     * @date 2021/9/7
     */
    fun infoRun(dirPath: String): ScmInfoVO {
        var scmInfo = ScmInfoVO("")
        if (File(dirPath).isDirectory) {
            var cmd = ""
            var logNum = "-1"
            try {
                cmd = "git branch "
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = File(dirPath),
                    printErrorLog = false,
                    print2Logger = false
                )
                for (line: String in result.split("\n")) {
                    if (line.contains("*")) {
                        val gitBranch = line.split("*")[1]
                        scmInfo.branch = gitBranch.replace("HEAD detached at ", "").replace("(", "").replace(")", "")
                            .replaceFirst(" ", "")
                        break
                    }
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }

            if (scmInfo.branch!!.contains("devops-virtual-branch")) {
                logNum = "-2"
            }

            try {
                cmd = "git log --pretty=format:\"" + getKey() + "an->" +
                        getKey() + "h->" + getKey() + "H->" + getKey() + "ad\" --date=raw $logNum"
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = File(dirPath),
                    printErrorLog = false,
                    print2Logger = false
                )
                for (line: String in result.split("\n")) {
                    if (line.contains("->")) {
                        val msgArray = line.split("->")
                        if (msgArray.size == 4) {
                            scmInfo.fileUpdateAuthor = msgArray[0]
                            scmInfo.revision = msgArray[1]
                            scmInfo.commitID = msgArray[2]
                            scmInfo.fileUpdateTime = (msgArray[3].split(" ")[0].replace("\"", "") + "000").toLong()
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }

            try {
                cmd = "git remote -v "
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = File(dirPath),
                    printErrorLog = false,
                    print2Logger = false
                )
                for (line: String in result.split("\n")) {
                    var url = line
                    if (url.contains("origin") && url.contains("(fetch)")) {
                        url = line.replace("origin\t", "").replace("(fetch)", "").replace(" ", "")
                    }
                    if (url.contains("http://") && url.contains("@")) {
                        url = "http://" + url.split("@")[1]
                    } else if (url.contains("https://") && url.contains("@")) {
                        url = "https://" + url.split("@")[1]
                    }
                    scmInfo.url = url.trimStart()
                    break
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }
        }
        scmInfo.scmType = "git"
        return scmInfo
    }

    /**
     * @Description ["获取submodule信息"]
     * @date 2021/9/7
     */
    fun getSubmodule(dirPath: String, scmInfo: ScmInfoVO): ScmInfoVO {
        var submoduleName = ""
        val moduleList = mutableListOf<SubModule>()
        var moduleInfo = SubModule("")
        val gitModulePath = File(dirPath + "/.gitmodules")
        if (gitModulePath.isFile) {
            gitModulePath.readLines().forEach { line ->
                if (line.contains("[submodule")) {
                    submoduleName = line.replace("[submodule", "").replace("\"]", "").replace("\"", "").trim()
                    if (!moduleInfo.equals(SubModule(""))) {
                        moduleInfo.subModule = submoduleName
                        moduleList.add(moduleInfo)
                        moduleInfo = SubModule("")
                    }
                } else {
                    if (line.contains("=") && line.contains("url")) {
                        var url = line.split("=")[1].replace(" ", "")
                        if (url.contains("http://") && url.contains("@")) {
                            url = "http://" + url.split("@")[1]
                        } else if (url.contains("https://") && url.contains("@")) {
                            url = "https://" + url.split("@")[1]
                        }
                        moduleInfo.url = url
                    }
                    if (line.contains("=") && line.contains("path")) {
                        var path = line.split("=")[1].replace(" ", "")
                        val subModulePath = File(dirPath + "/"+path)
                        if (subModulePath.isDirectory){
                            var cmd = "git log --pretty=format:\"" + GitInfo.getKey() + "H\" --date=raw -1"
                            val result = ScriptUtils.execute(
                                script = cmd,
                                dir = subModulePath,
                                printErrorLog = false,
                                print2Logger = false
                            )
                            for (line: String in result.split("\n")) {
                                if (!Strings.isNullOrEmpty(line)) {
                                    moduleInfo.commitId = line
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
        if (!moduleInfo.equals(SubModule(""))) {
            moduleInfo.subModule = submoduleName
            moduleList.add(moduleInfo)
        }
        scmInfo.subModules = moduleList

        return scmInfo
    }
}
