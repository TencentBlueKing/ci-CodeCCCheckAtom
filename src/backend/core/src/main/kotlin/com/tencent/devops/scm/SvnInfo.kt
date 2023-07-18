package com.tencent.devops.scm

import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCScmException
import com.tencent.devops.scm.pojo.ScmInfoVO
import com.tencent.devops.utils.script.ScriptUtils
import org.json.JSONObject
import org.json.XML
import java.io.File
import java.text.SimpleDateFormat

/**
 * 该类实现获取Svn库相关信息
 * {
 * "branch": "master",
 * "commitID": "69d9b5ce9af897d144f756b329cd45b0f2c2d645",
 * "fileUpdateAuthor": "xxx",
 * "fileUpdateTime": 1634625066000,
 * "revision": "69d9b5c",
 * "scmType": "svn",
 * "subModules": [],
 * "url": "https://xxx.com/origin-test.git"
 * }
 */
object SvnInfo {

    /**
     * @Description ["获取info数据"]
     * @date 2021/9/7
     */
    fun infoRun(dirPath: String): ScmInfoVO {
        var scmInfo = ScmInfoVO("")
        if (File(dirPath).isDirectory) {
            var cmd = ""
            try {
                cmd = "svn info --xml $dirPath"
                val result = ScriptUtils.execute(
                    script = cmd,
                    dir = File(dirPath),
                    printErrorLog = false,
                    print2Logger = false
                )
                val svnInfoJson = XML.toJSONObject(result)
                if (!svnInfoJson.isNull("info")) {
                    val info = svnInfoJson.get("info") as JSONObject
                    if (!info.isNull("entry")) {
                        val entry = info.get("entry") as JSONObject
                        if (!entry.isNull("url")) {
                            scmInfo.url = entry.get("url") as String
                        }
                        if (!entry.isNull("commit")) {
                            val commit = entry.get("commit") as JSONObject
                            scmInfo.fileUpdateAuthor = commit.get("author") as String
                            scmInfo.revision = commit.get("revision").toString()
                            var dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            if ((commit.get("date") as String).contains("T")) {
                                dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                            }
                            val date = dateFormat.parse((commit.get("date") as String).split(".")[0])
                            scmInfo.fileUpdateTime = (date.time).toString().toLong()
                        }
                        if (!entry.isNull("repository")) {
                            val repo = entry.get("repository") as JSONObject
                            scmInfo.rootUrl = repo.get("root") as String
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
        scmInfo.scmType = "svn"
        return scmInfo
    }
}