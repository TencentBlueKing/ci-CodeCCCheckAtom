package com.tencent.devops.scm

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCScmException
import com.tencent.devops.scm.pojo.ChangeRecord
import com.tencent.devops.scm.pojo.ScmBlameVO
import com.tencent.devops.utils.FilePathUtils
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import org.json.JSONObject
import org.json.XML
import java.text.SimpleDateFormat

/**
 * 该类实现逻辑为获取Svn项目下的文件作者提交信息
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
object SvnBlame {

    /**
     * @Description ["格式化文件路径"]
     * @date 2021/9/7
     */
    fun formatFilePath(filePath: String): String {
        return filePath.replace("(", "\\(").replace(")", "\\)").replace(" ", "\\ ")
    }

    /**
     * @Description ["行数与数组行数归纳，如：[3,4] 5 => [3, 5]"]
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
     * @Description ["获取文件级别信息，如：revision，longRevision，fileUpdateTime, branch, url"]
     * @date 2021/9/7
     */
    fun setFileInfo(scmInfo: ScmBlameVO): ScmBlameVO {
        if (scmInfo.changeRecords!!.size > 0) {
            scmInfo.branch = ""
            var cmd = ""
            val filePath = scmInfo.filePath.toString()
            val folderPath = File(filePath).parentFile
            try {
                cmd = "svn info  --xml " + formatFilePath(filePath)
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = folderPath,
                    printErrorLog = false,
                    print2Logger = false
                )
                val svnInfoJson = XML.toJSONObject(result)
                if (!svnInfoJson.isNull("info")) {
                    val info = svnInfoJson.get("info") as JSONObject
                    if (!info.isNull("entry")) {
                        val entry = info.get("entry") as JSONObject
                        scmInfo.fileRelPath = entry.get("relative-url").toString().replace("^", "")
                        scmInfo.url = entry.get("url") as String
                        if (!entry.isNull("repository")) {
                            val repo = entry.get("repository") as JSONObject
                            scmInfo.rootUrl = repo.get("root") as String
                        }
                        if (!entry.isNull("commit")) {
                            val commit = entry.get("commit") as JSONObject
                            scmInfo.revision = commit.get("revision").toString()
                            var dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            if ((commit.get("date") as String).contains("T")) {
                                dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                            }
                            val date = dateFormat.parse((commit.get("date") as String).split(".")[0])
                            scmInfo.fileUpdateTime = (date.time).toString().toLong()
                        }
                    }
                }
            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_SVN_COMMAND_RUN_FAIL,
                    "run command $cmd failed! ",
                    arrayOf(cmd)
                )
            }
        }
        return scmInfo
    }

    /**
     * @Description ["svn blame and parse output"]
     * @date 2021/9/7
     */
    fun blameRun(filePath: String, user: String, password: String): ScmBlameVO {
        val svnBlameOptions = " --username $user --password $password "
        var scmInfo = ScmBlameVO("")
        val curFile = File(filePath)
        if (curFile.exists()) {
            var changeRecord = ChangeRecord("")
            var records = mutableSetOf<String>()
            var commit = mutableMapOf<String, ArrayList<Any>?>()
            var changeRecordList = mutableListOf<ChangeRecord>()
            val cmd =
                "svn blame --non-interactive  --no-auth-cache --trust-server-cert --xml $svnBlameOptions $filePath"
            try {
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = curFile.parentFile,
                    printErrorLog = false,
                    print2Logger = false
                )
                val svnBlameJson = XML.toJSONObject(result)
                if (!svnBlameJson.isNull("blame")) {
                    val blame = svnBlameJson.get("blame") as JSONObject
                    if (!blame.isNull("target")) {
                        val target = blame.get("target") as JSONObject
                        if (!target.isNull("entry")) {
                            target.getJSONArray("entry").forEach { elem ->
                                val lineElem = elem as JSONObject
                                val info = lineElem.get("commit") as JSONObject
                                val lineRevisionId = (info.get("revision") as Int).toString()
                                val lineNum = lineElem.get("line-number") as Int
                                if (!changeRecord.equals(ChangeRecord(""))) {
                                    records.add(
                                        String(
                                            Base64.getEncoder().encode(JsonUtil.toJson(changeRecord).toByteArray())
                                        )
                                    )
                                }
                                changeRecord = ChangeRecord("")
                                changeRecord.lineRevisionId = lineRevisionId
                                if (commit.containsKey(lineRevisionId)) {
                                    val zoomLines =
                                        SvnBlame.zoomList(commit.get(lineRevisionId) as ArrayList<Any>, lineNum)
                                    commit[lineRevisionId] = zoomLines
                                } else {
                                    val zoomLines = ArrayList<Any>()
                                    zoomLines.add(lineNum)
                                    commit[lineRevisionId] = zoomLines
                                }
                                changeRecord.lineShortRevisionId = lineRevisionId
                                changeRecord.author = info.get("author") as String
                                changeRecord.authorMail = info.get("author") as String
                                var dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                if ((info.get("date") as String).contains("T")) {
                                    dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                                }
                                val date = dateFormat.parse((info.get("date") as String).split(".")[0])
                                changeRecord.lineUpdateTime = (date.time).toString().toLong()

                            }
                        }
                    }
                }

            } catch (e: Exception) {
                throw CodeCCScmException(
                    ErrorCode.SCM_SVN_COMMAND_RUN_FAIL,
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
        scmInfo = SvnBlame.setFileInfo(scmInfo)
        if (scmInfo.fileRelPath.isNullOrEmpty()) {
            scmInfo.fileRelPath = FilePathUtils.getRelPath(filePath)
        }
        scmInfo.filePath = CommonUtils.changePathToDocker(filePath)
        scmInfo.scmType = "svn"
        return scmInfo
    }
}


fun main() {
    val blameInfo = "/Users/jimxzcai/workspace/test_source/ld_test_cpp/co_hook_sys_call.cpp;3,4"
    val user = ""
    val password = ""
    val scmInfo = SvnBlame.blameRun(blameInfo, user, password)
    val scmBlameOutputFile = File("/Users/jimxzcai/Downloads/test_scm_blame.json")
    scmBlameOutputFile.bufferedWriter().use { out -> out.write(JsonUtil.toJson(scmInfo)) }
}