package com.tencent.devops.scm

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.scm.pojo.IncrementFile
import com.tencent.devops.scm.pojo.ScmIncrementVO
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File

/**
 * 该类实现两个Svn版本的对比差异，获取差异文件列表
 * "updateFileList": ["/scan/bin/common/config.py"]
 */
object SvnIncrement {

    /**
     * @Description ["通过历史与当前版本，获取增量文件列表"]
     * @date 2021/9/7
     */
    fun incrementRun(preRevision: String, workSpace: String, svnOptions: String): IncrementFile {
        val updateFileList = mutableSetOf<String>()
        val deleteFileList = mutableSetOf<String>()
        var incrementFile = IncrementFile()
        val getLatestVersion = "svn info --show-item revision"
        val incrementCommand = "svn diff --non-interactive  --no-auth-cache --trust-server-cert -r" +
                " $preRevision:HEAD --summarize $svnOptions"

        try {
            val result = ScriptUtils.execute(
                script = getLatestVersion,
                dir = File(workSpace),
                printErrorLog = false,
                print2Logger = false
            )
            for (line: String in result.split("\n")) {
                incrementFile.latestRevision = line.trim()
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
                dir = File(workSpace),
                printErrorLog = false,
                print2Logger = false
            )
            for (line: String in result.split("\n")) {
                if (line.trim().equals("")){
                    continue
                }
                val relFilePath = line.trim().substring(1).replace(" ", "")
                val filePath = File(workSpace + File.separator + relFilePath)
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

        incrementFile.updateFileList = updateFileList
        LogUtils.printLog("updateFileList: " + updateFileList.joinToString("\n"))
        incrementFile.deleteFileList = deleteFileList
        LogUtils.printLog("deleteFileList: " + deleteFileList.joinToString("\n"))

        return incrementFile

    }

    /**
     * @Description ["通过历史与当前版本，获取增量数据"]
     * @date 2021/9/7
     */
    fun run(preRevision: String, workSpace: String, user: String, password: String): ScmIncrementVO {
        var incrementRepoList = mutableListOf<IncrementFile>()
        var preRevision = preRevision
        val workSpacePath = File(workSpace)
        val svnOptions = " --username $user --password $password "
        var incrementVO = ScmIncrementVO()
        if (workSpacePath.isDirectory) {
            var cmd = ""

            if (preRevision.equals("")) {
                preRevision = "PREV"
            }
            var incrementFile = incrementRun(preRevision, workSpace, svnOptions)
            incrementRepoList.add(incrementFile)
            if (incrementRepoList.size > 0) {
                incrementVO.scmIncremt = incrementRepoList
            }
        }
        return incrementVO
    }
}