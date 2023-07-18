package com.tencent.devops.utils

import com.perforce.p4java.core.file.FileSpecBuilder
import com.perforce.p4java.exception.AccessException
import com.perforce.p4java.option.server.GetDepotFilesOptions
import com.perforce.p4java.option.server.TrustOptions
import com.perforce.p4java.server.CmdSpec
import com.perforce.p4java.server.IOptionsServer
import com.perforce.p4java.server.IServerAddress
import com.perforce.p4java.server.ServerFactory.getOptionsServer
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.ChangeRecord
import com.tencent.devops.pojo.FileAnnotation
import com.tencent.devops.scm.pojo.ScmInfoVO
import org.apache.commons.lang3.exception.ExceptionUtils
import java.text.SimpleDateFormat

class P4Client(
    val uri: String,
    val userName: String,
    val password: String? = null,
    val clientName: String? = null,
    charsetName: String = "none"
) : AutoCloseable {
    private val server: IOptionsServer = getOptionsServer(uri, null)

    init {
        server.userName = userName
        if (uri.startsWith(IServerAddress.Protocol.P4JAVASSL.toString())) {
            server.addTrust(TrustOptions().setAutoAccept(true))
        }
        server.connect()
        setCharset(charsetName)
        login()
        server.currentClient  = server.getClient(clientName)
    }

    fun getPerforceInfo(): ScmInfoVO {
        LogUtils.printLog("getPerforceInfo")
        val info = server.execMapCmdList(CmdSpec.REVIEW, emptyArray(), emptyMap())
        LogUtils.printDebugLog(info)
        val maxInfo = info.maxByOrNull {
            if (it["change"] == null || it["change"].toString().isBlank()) {
                -1
            } else {
                Integer.parseInt(it["change"].toString())
            }
        }
        val scmInfoVO = ScmInfoVO()
        val changeListId = maxInfo?.get("change")?.toString() ?: ""
        scmInfoVO.commitID = changeListId
        scmInfoVO.revision = changeListId
        scmInfoVO.fileUpdateAuthor = maxInfo?.get("user")?.toString() ?: ""
        scmInfoVO.url = uri
        scmInfoVO.scmType = "perforce"
        if (changeListId.isNotBlank()) {
            val changeList =  server.getChangelist(Integer.parseInt(changeListId))
            scmInfoVO.fileUpdateTime = changeList.date.time
        }

        return scmInfoVO
    }

    fun getChangeList(filePathSet: MutableSet<String>): MutableList<FileAnnotation> {
        if (filePathSet.isEmpty()) {
            return emptyList<FileAnnotation>().toMutableList()
        }
        LogUtils.printLog("clientName is $clientName")
        // 建立本地文件路径和远端文件路径的映射
        val localDepotMap = mutableMapOf<String, String>()
        filePathSet.forEach { localFile ->
            val fileSpecList = FileSpecBuilder.makeFileSpecList(listOf(localFile))
            val depotFileSpecList = server.getDepotFiles(fileSpecList, GetDepotFilesOptions())
            if (depotFileSpecList.isNullOrEmpty()) {
                LogUtils.printErrorLog("can not get file depot path: $localFile")
            }
            if (depotFileSpecList.first().depotPath == null
                || depotFileSpecList.first().depotPath.pathString.isNullOrBlank()) {
                LogUtils.printErrorLog("perforce fail for file: $localFile ${depotFileSpecList.first()}")
                return@forEach
            }
            localDepotMap[depotFileSpecList.first().depotPath.pathString] = localFile
        }

        // 从perforce远端获取文件的blame信息， -u 显示修改用户、日期
        val opts = mutableListOf<String>()
        opts.add("-u")
        opts.add("-i")
        filePathSet.forEach {
            opts.add(it)
        }
        val result = server.execMapCmdList(CmdSpec.ANNOTATE, opts.toTypedArray(), null)
        // 处理 p4 annotation 结果
        return generateFileAnnotation(result, localDepotMap)
    }

    private fun generateFileAnnotation(
        annotationList: MutableList<Map<String, Any>>,
        localDepotMap: MutableMap<String, String>
    ): MutableList<FileAnnotation> {
        var lineNum = 0
        val fileAnnotationList = mutableListOf<FileAnnotation>()
        var changeRecordList: MutableList<ChangeRecord>
        var changeRecordMap: MutableMap<String, ChangeRecord>

        annotationList.forEach { annotationMap ->
            if (annotationMap.containsKey("rev")) {
                lineNum = 0
                changeRecordList = mutableListOf()
                changeRecordMap = mutableMapOf()
                val fileAnnotation =  FileAnnotation(
                    changeRecords = changeRecordList,
                    changeRecordsMap = changeRecordMap
                )

                // 设置绝对路径
                if (!localDepotMap[annotationMap["depotFile"].toString()].isNullOrEmpty()) {
                    fileAnnotation.filePath = localDepotMap[annotationMap["depotFile"].toString()]
                }

                fileAnnotation.fileRelPath = annotationMap["depotFile"].toString()
                fileAnnotation.revision = annotationMap["rev"].toString()
                fileAnnotation.longRevision = annotationMap["rev"].toString()
                fileAnnotation.fileUpdateTime = annotationMap["time"].toString().toLong() * 1000
                fileAnnotation.change = annotationMap["change"].toString()
                fileAnnotation.scmType = "perforce"
                fileAnnotationList.add(fileAnnotation)
                return@forEach
            }

            lineNum++
            if (fileAnnotationList.isEmpty()) {
                return@forEach
            }
            val fileAnnotation = fileAnnotationList.last()
            val changeRecord = ChangeRecord()
            changeRecord.line = lineNum
            changeRecord.author = annotationMap["user"].toString()
            changeRecord.lineRevisionId = annotationMap["upper"].toString()
            changeRecord.lineShortRevisionId = annotationMap["upper"].toString()
            LogUtils.printDebugLog("debug time: $annotationMap \n $fileAnnotation")
            changeRecord.lineUpdateTime = convertStrToTimestamp(annotationMap["time"].toString())
            // 拿不到更新时间的有可能当前文件不是perforce远端的文件
            if (changeRecord.lineUpdateTime == 0L) {
                LogUtils.printLog("can not get line update time: $annotationMap")
            }
            val currChangeRecordMap = fileAnnotation.changeRecordsMap

            if (currChangeRecordMap["${annotationMap["user"].toString()}_${annotationMap["time"].toString()}"] != null
                && currChangeRecordMap["${annotationMap["user"].toString()}_${annotationMap["time"].toString()}"]?.lines != null) {
                currChangeRecordMap["${annotationMap["user"].toString()}_${annotationMap["time"].toString()}"]?.lines?.add(lineNum)
            } else {
                currChangeRecordMap["${annotationMap["user"].toString()}_${annotationMap["time"].toString()}"] = changeRecord
                changeRecord.lines = mutableListOf()
                changeRecord.lines!!.add(changeRecord.line!!)
            }
        }
        fileAnnotationList.forEach {
            it.changeRecords = ArrayList(it.changeRecordsMap.values)
            mergeLines(it.changeRecords)
        }
        return fileAnnotationList
    }

    private fun mergeLines(changeRecords: MutableList<ChangeRecord>) {
        changeRecords.forEach { changeRecord ->
            try {
                mergeLines(changeRecord)
            } catch (e: Throwable) {
                LogUtils.printErrorLog("fail to merge blame lines: ${ExceptionUtils.getStackTrace(e)}")
            }
        }
    }

    private fun mergeLines(changeRecord: ChangeRecord) {
        val lines = changeRecord.lines
        if (lines.isNullOrEmpty() || lines.size == 1) {
            return
        }
        val newLines = mutableListOf<Any>()
        var curr: Int
        var last = Int.MIN_VALUE
        lines.forEachIndexed { index, any ->
            curr = any as Int
            if (index == 0) {
                last = curr
                newLines.add(curr)
                return@forEachIndexed
            }

            if (curr - last == 1) {
                if (newLines.last() is MutableList<*>) {
                    val lineList = newLines.last() as MutableList<Int>
                    if (lineList.size == 2) {
                        lineList.removeLast()
                        lineList.add(curr)
                    } else {
                        LogUtils.printLog("fail to merge lines: ${newLines.last()}")
                    }
                } else {
                    val lineList = mutableListOf<Int>()
                    lineList.add(newLines.last() as Int)
                    lineList.add(curr)
                    newLines.removeLast()
                    newLines.add(lineList)
                }
            } else {
                newLines.add(curr)
            }

            last = curr
        }

        changeRecord.lines = newLines
    }

    private fun convertStrToTimestamp(timeStr: String?): Long? {
        if (timeStr.isNullOrBlank()) {
            return 0
        }
        return try {
            SimpleDateFormat("yyyy/MM/dd HH:mm:ss").parse(timeStr)?.time
        } catch (e: Throwable) {
            LogUtils.printErrorLog("fail to parse date time for: $timeStr")
            LogUtils.printErrorLog(ExceptionUtils.getStackTrace(e))
            0
        }
    }

    private fun setCharset(charsetName: String) {
        if (server.supportsUnicode() && charsetName != "none") {
            LogUtils.printLog("Connection use Charset $charsetName.")
            server.charsetName = charsetName
        } else {
            LogUtils.printLog("Server not supports unicode,charset $charsetName was ignore.")
        }
    }

    private fun isLogin(): Boolean {
        val loginStatus = server.loginStatus
        if (loginStatus.contains("ticket expires")) {
            return true
        }
        if (loginStatus.contains("not necessary")) {
            return true
        }
        if (loginStatus.isEmpty()) {
            return true
        }
        return false
    }

    private fun login() {
        if (isLogin()) {
            LogUtils.printLog("Already logged in：${server.loginStatus}")
            return
        }
        // 插件凭证使用的是用户名+密码类型，且支持ticket和password设置，
        // 所以这里不确定用户设置的是密码还是ticket，
        // 所以先进行密码登录，如果失败，则进行ticket登录
        try {
            server.login(password)
        } catch (e: AccessException) {
            // 触发认证，设置serverId。否则设置ticket的时候会根据serverAddress,
            // 获取时候又根据serverId来获取，导致不匹配，获取不到ticket，认证失败
            server.loginStatus
            server.authTicket = password
        }
        if (!isLogin()) {
            throw AccessException("Login credential error, authentication failed!")
        }
        LogUtils.printLog("Login successful：${server.loginStatus}")
    }

    override fun close() {
        try {
            server.disconnect()
        } catch (ignore: Exception) {
        }
    }
}
