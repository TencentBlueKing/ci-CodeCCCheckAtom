package com.tencent.devops.scm

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCScmException
import com.tencent.devops.scm.pojo.ChangeRecord
import com.tencent.devops.scm.pojo.ScmBlameVO
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.FilePathUtils
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/**
 * 该类实现逻辑为获取Git项目下的文件作者提交信息
 * 作者信息包含：
 * {
 * "author": "xxx",
 * "authorMail": "xxx@xxx.com",
 * "lineRevisionId": "01a4e97884b9b5871037e49b595sdf4bd459ebb450c",
 * "lineShortRevisionId": "01a4e978",
 * "lineUpdateTime": 1563160945000,
 * "lines": [ [1, 226] ]
 * }
 */
object GitBlame {

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
     * @Description [行数与数组行数归纳，如：[3,4] 5 => [3, 5]]
     * @date 2021/9/7
     */
    fun zoomList(lines: ArrayList<Any>, lineNum: Int): ArrayList<Any> {
        val endNums = lines.size - 1
        var subLineNums = ArrayList<Any>()
        if (lines[endNums] is ArrayList<*>) {
            subLineNums.addAll(lines[endNums] as Collection<Any>)
            if ((lineNum - (subLineNums[1] as Int)) == 1) {
                subLineNums[1] = lineNum
                lines[endNums] = subLineNums
            } else {
                lines.add(lineNum)
            }
        } else if ((lineNum - (lines[endNums] as Int)) == 1) {
            lines[endNums] = mutableListOf(lines[endNums], lineNum)
        } else {
            lines.add(lineNum)
        }
        return lines
    }

    /**
     * @Description [格式化文件路径]
     * @date 2021/9/7
     */
    fun formatFilePath(filePath: String): String {
        return filePath.replace("(", "\\(").replace(")", "\\)").replace(" ", "\\ ")
    }


    /**
     * @Description [获取文件级别信息，如：revision，longRevision，fileUpdateTime, branch, url]
     * @date 2021/9/7
     */
    fun setFileInfo(scmInfo: ScmBlameVO): ScmBlameVO {
        if (scmInfo.changeRecords!!.size > 0) {
            var cmd = ""
            val filePath = scmInfo.filePath.toString()
            val folderPath = File(filePath).parentFile
            val gitRootPath = Utils.findGitDirPath(folderPath.canonicalPath)
            if (filePath != null) {
                scmInfo.fileRelPath = filePath.replace(gitRootPath.replace("\\", "/"), "")
                        .replace("//", "/")
                        .replace("\\\\", "/")
                        .replace("\\", "/")
            }
            try {
                cmd = "git log --pretty=format:"+getKey()+"h " + formatFilePath(filePath)
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = folderPath,
                    printErrorLog = false,
                    print2Logger  = false
                )
                for (line: String in result.split("\n")) {
                    scmInfo.revision = line
                    break
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }

            try {
                cmd = "git log --pretty=format:"+getKey()+"H " + formatFilePath(filePath)
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = folderPath,
                    printErrorLog = false,
                    print2Logger  = false
                )
                for (line: String in result.split("\n")) {
                    scmInfo.longRevision = line
                    break
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }

            try {
                cmd = "git log --pretty=format:\""+getKey()+"ad\"  --date=raw --reverse " + formatFilePath(filePath)
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = folderPath,
                    printErrorLog = false,
                    print2Logger  = false
                )
                for (line: String in result.split("\n")) {
                    scmInfo.fileUpdateTime = (line.split(" ")[0].replace("\"", "") + "000").toLong()
                    break
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }

            try {
                cmd = "git branch "
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = folderPath,
                    printErrorLog = false,
                    print2Logger  = false
                )
                for (line: String in result.split("\n")) {
                    if (line.contains("*")) {
                        val gitBranch = line.split("*")[1]
                        if (gitBranch.contains("no branch")) {
                            scmInfo.branch = "master"
                        } else if (gitBranch.contains(")") && gitBranch.contains(" ")) {
                            val strSplit = gitBranch.split(" ")
                            scmInfo.branch = strSplit[strSplit.size - 1].replace(")", "").replace(" ", "")
                        } else {
                            scmInfo.branch = gitBranch.replace(" ", "")
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
                    dir = folderPath,
                    printErrorLog = false,
                    print2Logger  = true
                )
                for (line: String in result.split("\n")) {
                    var url = line.trim()
                    val re = Regex("^origin")
                    if (re.matches(url) && url.contains("(fetch)")) {
                        url = url.replace("origin\t", "").replace("(fetch)", "").replace(" ", "")
                    }
                    if (url.contains("http://") && url.contains("@")) {
                        url = "http://" + url.split("@")[1]
                    }
                    scmInfo.url = url.trim()
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
        return scmInfo
    }

    /**
     * @Description [git blame and parse output]
     * @date 2021/9/7
     */
    fun blameRun(filePath: String): ScmBlameVO {
        var scmInfo = ScmBlameVO("")
        val curFile = File(filePath)
        if (curFile.exists()) {
            var changeRecord = ChangeRecord("")
            var records = mutableSetOf<String>()
            var commit = mutableMapOf<String, ArrayList<Any>?>()
            var changeRecordList = mutableListOf<ChangeRecord>()
            val cmd = "git blame $filePath --line-porcelain "
            try {
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = curFile.parentFile,
                    printErrorLog = false,
                    print2Logger  = false
                )
                result.split("\n").forEach { line ->
                    if (Regex("^[a-z0-9]{20,} .*").matches(line)) {
                        val lineRevisionId = line.split(" ")[0]
                        val lineNum = line.split(" ")[2].toInt()
                        if (!changeRecord.equals(ChangeRecord(""))) {
                            records.add(String(Base64.getEncoder().encode(JsonUtil.toJson(changeRecord).toByteArray())))
                        }
                        changeRecord = ChangeRecord("")
                        changeRecord.lineRevisionId = lineRevisionId
                        if (commit.containsKey(lineRevisionId)) {
                            val zoomLines = zoomList(commit.get(lineRevisionId) as ArrayList<Any>, lineNum)
                            commit[lineRevisionId] = zoomLines
                        } else {
                            val zoomLines = ArrayList<Any>()
                            zoomLines.add(lineNum)
                            commit[lineRevisionId] = zoomLines
                        }
                        changeRecord.lineShortRevisionId = lineRevisionId.substring(0, 8)
                    } else if (Regex("^author .*").matches(line) && !line.equals("=")) {
                        changeRecord.author = line.split(" ")[1]
                    } else if (Regex("^author-mail .*").matches(line)) {
                        changeRecord.authorMail = line.split(" ")[1]
                    } else if (Regex("^author-time .*").matches(line)) {
                        changeRecord.lineUpdateTime = (line.split(" ")[1] + "000").toLong()
                    }
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_GIT_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }
            if (!changeRecord.equals(ChangeRecord(""))) {
                records.add(String(Base64.getEncoder().encode(JsonUtil.toJson(changeRecord).toByteArray())))
            }
            records.forEach { base64Info ->
                changeRecord =
                    JsonUtil.getObjectMapper().readValue<ChangeRecord>(Base64.getDecoder().decode(base64Info))
                changeRecord.lines = commit.get(changeRecord.lineRevisionId)
                if (!changeRecord.author.equals("")) {
                    changeRecordList.add(changeRecord)
                }
            }

            if (changeRecordList.size > 0) {
                scmInfo.changeRecords = changeRecordList
                scmInfo.filePath = filePath
            }

        }
        scmInfo = setFileInfo(scmInfo)
        if (scmInfo.fileRelPath.isNullOrEmpty()) {
            scmInfo.fileRelPath = FilePathUtils.getRelPath(filePath)
        }
        scmInfo.filePath = CommonUtils.changePathToDocker(filePath)
        scmInfo.scmType = "git"
        return scmInfo
    }

}


