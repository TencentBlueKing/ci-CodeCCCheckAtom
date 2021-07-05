package com.tencent.devops.docker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.DefectsEntity
import com.tencent.devops.docker.pojo.ScanType
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.scm.ScmBlame
import com.tencent.devops.docker.scm.ScmDiff
import com.tencent.devops.docker.scm.ScmIncrement
import com.tencent.devops.docker.scm.ScmInfo
import com.tencent.devops.docker.scm.pojo.ScmDiffItem
import com.tencent.devops.docker.tools.FileUtil
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccConfig
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.exception.CodeccDependentException
import com.tencent.devops.pojo.exception.CodeccRepoServiceException
import com.tencent.devops.pojo.exception.CodeccTaskExecException
import com.tencent.devops.utils.CompressUtils
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import java.io.File
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date

object ScanComposer {

    // 单工具scan
    fun scan(streamName: String, toolName: String, commandParam: CommandParam) {
        val startTime = Date()
        val ft = SimpleDateFormat("yyyy-MM-dd hh:mm:ss")
        LogUtils.printLog("start scan time: " + ft.format(startTime))
        // 获取本次扫描版本信息
//        LogUtils.printDebugLog("start scm info...")
//        if (!ScmInfo(commandParam, toolName, streamName, 0).scmOperate()) {
////            uploadErrorLog(analyzeConfigInfo, streamName, toolName, commandParam)
//            throw CodeccRepoServiceException("scm info failed.")
//        }
//        LogUtils.printDebugLog("start scm success")

        // get CodeCC API data
        LogUtils.printDebugLog("Get properties info from server...")
        val analyzeConfigInfo = CodeccWeb.getConfigDataByCodecc(streamName, toolName, commandParam)
        LogUtils.printDebugLog("Get properties info from server success")
        if (commandParam.repoUrlMap.isBlank()) {
            LogUtils.printDebugLog("No scm change scan type to full scan")
            analyzeConfigInfo.scanType = ScanType.FULL
            CodeccWeb.changScanType(commandParam.landunParam, analyzeConfigInfo.taskId, streamName, toolName)
        }
        if (analyzeConfigInfo.scanType == ScanType.FAST_INCREMENTAL){
            if (ToolConstants.COVERITY == toolName || ToolConstants.KLOCWORK == toolName) {
                getStatusFromCodecc(commandParam, analyzeConfigInfo, toolName, 5)
            }else{
                getStatusFromCodecc(commandParam, analyzeConfigInfo, toolName, 4)
            }
            LogUtils.printLog(" scan finished: ${ft.format(startTime)} to ${ft.format(Date())}")
            return
        }
        if (analyzeConfigInfo.scanType == ScanType.PARTIAL_INCREMENTAL){
            CodeccWeb.codeccStreamPush(commandParam.landunParam, streamName, toolName, commandParam.openScanPrj)
            // 构建结束
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 1, 1)
            // 排队开始
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 2, 3)
            getStatusFromCodecc(commandParam, analyzeConfigInfo, toolName, 5)
            LogUtils.printLog(" scan finished: ${ft.format(startTime)} to ${ft.format(Date())}")
            return
        }
        // check third env
        CodeccConfig.checkThirdEnv(commandParam, toolName)

        // 排队开始
        CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 1, 3)
        if (ToolConstants.COVERITY != toolName && ToolConstants.KLOCWORK != toolName) { // 非coverity才有下面这些内容
            // 排队结束
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 1, 1)

            // 下载开始
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 2, 3)

            // 下载结束
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 2, 1)

            // 扫描开始
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 3)
        }

        // 判断扫描类型
        val incrementFiles = mutableListOf<String>()
        val deleteFiles = mutableListOf<String>()
        val diffFileList = mutableListOf<ScmDiffItem.DiffFileItem>()
        if (analyzeConfigInfo.scanType == ScanType.INCREMENT) {
            LogUtils.printDebugLog("scan type is increment...")
            if (!ScmIncrement(commandParam, toolName, streamName, analyzeConfigInfo.taskId, analyzeConfigInfo, incrementFiles, deleteFiles).scmOperate()) {
                uploadErrorLog(analyzeConfigInfo, streamName, toolName, commandParam)
//                CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 2)
                LogUtils.printDebugLog("scm increment failed")
                throw CodeccRepoServiceException(errorMsg = "scm increment failed.", toolName = toolName)
            }
            if (toolName == ToolConstants.COVERITY) {
                downloadCovResult(commandParam, streamName, toolName)
            }
        } else if (analyzeConfigInfo.scanType == ScanType.DIFF) {
//            if (toolName == ToolConstants.COVERITY) {
//                downloadCovResult(commandParam, streamName, toolName)
//            }
            if (toolName == ToolConstants.COVERITY || toolName == ToolConstants.KLOCWORK || toolName == ToolConstants.PINPOINT || toolName == ToolConstants.DUPC) {
                LogUtils.printDebugLog("scan type is diff is not support in tool $toolName")
                analyzeConfigInfo.scanType = ScanType.FULL
                CodeccWeb.changScanType(commandParam.landunParam, analyzeConfigInfo.taskId, streamName, toolName)
            } else {
                LogUtils.printDebugLog("scan type is diff...")
                if (!ScmDiff(commandParam, toolName, streamName, analyzeConfigInfo.taskId, incrementFiles, deleteFiles, diffFileList).scmOperate()) {
                    uploadErrorLog(analyzeConfigInfo, streamName, toolName, commandParam)
//                    CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 2)
                    throw CodeccRepoServiceException(errorMsg = "scm diff failed.", toolName = toolName)
                }
            }
        }

        // 工具扫描
        LogUtils.printDebugLog("tool scan start...")
        if (!Scan(commandParam, toolName, streamName, analyzeConfigInfo, incrementFiles, diffFileList).scan()) {
//            if (toolName == ToolConstants.COVERITY && (commandParam.openScanPrj == true || commandParam.repoUrlMap.contains("bkdevops-plugins") ||  ! checkIncludeCompileLanuage(analyzeConfigInfo.language))){
            if (toolName == ToolConstants.COVERITY){
                val cov_empty_file = File( generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "cov_empty.txt")
                if (cov_empty_file.exists()){
                    println("Ignore coverity ERROR: Coverity can not capture any file!")
                    // 构建结束
                    CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 1, 1)
                    CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 5, 3)
                    CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 5, 1)
                    return
                }
            }
            uploadErrorLog(analyzeConfigInfo, streamName, toolName, commandParam)
            throw CodeccTaskExecException(errorMsg = "tool scan failed.", toolName = toolName)
        }
        LogUtils.printDebugLog("tool scan finish")

        LogUtils.printDebugLog("getScanFileList...")
        val filePathList = getScanFileList(commandParam.dataRootPath, streamName, toolName)
        LogUtils.printDebugLog("getScanFileList filePathList size: ${filePathList.size}")

        // 添加md5
        LogUtils.printDebugLog("append md5...")
        md5Files(commandParam, streamName, toolName, filePathList)
        LogUtils.printDebugLog("append md5 success")

        // 增量文件如果告警为0，则加入到删除文件列表中
        LogUtils.printDebugLog("checkUpdateFilesIsExistDefects...")

        // 没有拉代码插件，则不需要这段
        if (commandParam.repoUrlMap.isNotBlank()) {
            val deleteFileList = checkUpdateFilesIsExistDefects(incrementFiles, deleteFiles, filePathList, analyzeConfigInfo.scanType)
            LogUtils.printDebugLog("checkUpdateFilesIsExistDefects success, deleteFileList size: ${deleteFileList.size}")

            // 上传增量文件信息
            LogUtils.printDebugLog("upload increment version info...")
            val params = incrementVersionInfo(commandParam, streamName, toolName, analyzeConfigInfo.taskId, deleteFileList)
            if (null != params) {
                LogUtils.printDebugLog("params is not null, upload it")
                CodeccWeb.uploadRepoInfo(commandParam.landunParam, params)
            }

            // scmBlame()
            LogUtils.printDebugLog("ScmBlame...")
            if (!ScmBlame(commandParam, toolName, streamName, analyzeConfigInfo.taskId).scmOperate()) {
                uploadErrorLog(analyzeConfigInfo, streamName, toolName, commandParam)
//                CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 2)
                LogUtils.printDebugLog("scm blame failed")
                throw CodeccRepoServiceException(errorMsg = "scm blame failed.", toolName = toolName)
            }
            LogUtils.printDebugLog("ScmBlame success")
        }else{
            // scmBlame()
            commandParam.scmType = "git"
            commandParam.repoUrlMap = "{}"
            LogUtils.printDebugLog("ScmBlame...")
            if (!ScmBlame(commandParam, toolName, streamName, analyzeConfigInfo.taskId).scmOperate()) {
                uploadErrorLog(analyzeConfigInfo, streamName, toolName, commandParam)
                LogUtils.printDebugLog("scm blame failed")
                throw CodeccRepoServiceException(errorMsg = "scm blame failed.", toolName = toolName)
            }
            LogUtils.printDebugLog("ScmBlame success")
            commandParam.scmType = ""
            commandParam.repoUrlMap = ""
        }

        // 打印告警
        if (commandParam.needPrintDefect && toolName !in ToolConstants.CODE_TOOLS_ACOUNT) {
            LogUtils.printDebugLog("print defects begin")
            printDefects(commandParam, streamName, toolName, analyzeConfigInfo)
            LogUtils.printDebugLog("print defects end")
        }

        if (ToolConstants.COVERITY == toolName || ToolConstants.KLOCWORK == toolName) {
            LogUtils.printDebugLog("enter coverity branch")
            val zipResultFileName = commandParam.dataRootPath + File.separator + streamName + "_${toolName.toUpperCase()}_result.zip"
            val zipResultFile = File(zipResultFileName)
            if (zipResultFile.exists()) {
                val toolPrefix = when (toolName) {
                    ToolConstants.COVERITY -> "cov"
                    ToolConstants.KLOCWORK -> "kw"
                    else -> ""
                }

                CodeccWeb.upload(
                    landunParam = commandParam.landunParam,
                    filePath = zipResultFileName,
                    resultName = streamName + "_${toolPrefix}_result.zip",
                    uploadType = "SUCCESS_RESULT",
                    toolName = toolName)
                CodeccWeb.codeccStreamPush(commandParam.landunParam, streamName, toolName, commandParam.openScanPrj)
            }
            // 构建结束
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 1, 1)
            // 排队开始
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 2, 3)
        } else {
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 1)
        }
        if (ToolConstants.COVERITY == toolName || ToolConstants.KLOCWORK == toolName) {
            getStatusFromCodecc(commandParam, analyzeConfigInfo, toolName, 5)
        } else {
            getStatusFromCodecc(commandParam, analyzeConfigInfo, toolName, 4)
        }

        // 删除临时文件 null 说明是生产环境
        if (!LogUtils.getDebug()) {
            val dataToolPath = File(generateToolDataPath(commandParam.dataRootPath, streamName, toolName))
            LogUtils.printLog("start to delete file: ${dataToolPath.canonicalPath}")
            try {
                FileUtils.deleteDirectory(dataToolPath)
            } catch (e: Throwable) {
                LogUtils.printLog("delete temp file failed: ${e.message}")
            }
        }

        // 结束
        LogUtils.printLog(" scan finished: ${ft.format(startTime)} to ${ft.format(Date())}")
    }

    private fun uploadErrorLog(analyzeConfigInfo: AnalyzeConfigInfo, streamName: String, toolName: String, commandParam: CommandParam){
        if (ToolConstants.COVERITY == toolName || ToolConstants.KLOCWORK == toolName) {
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 1, 2)
        }else{
            CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 2)
        }
    }

    private fun downloadCovResult(commandParam: CommandParam, streamName: String, toolName: String) {
        val dataToolPath = generateToolDataPath(commandParam.dataRootPath, streamName, toolName)
        val filePath = commandParam.dataRootPath + File.separator + streamName + "_COVERITY_download_result.zip"
        CodeccWeb.download(filePath, "${streamName}_cov_result.zip", "LAST_RESULT", commandParam.landunParam)
        if (File(filePath).exists()) {
            LogUtils.printLog("unzip $filePath to folder: $dataToolPath ...")
            try {
                FileUtil.unzipFile(filePath, dataToolPath)
            } catch (e: Throwable) {
                LogUtils.printDebugLog("unzip $filePath failed. e: ${e.message}")
            }
            File(filePath).delete()
        }
    }

    private fun getStatusFromCodecc(commandParam: CommandParam, analyzeConfigInfo: AnalyzeConfigInfo, toolName: String, currentStep: Int) {
        var syncNum = 1
        LogUtils.printStr("analyzing...")
        var countFailed = 0
        while (true) {
            if (countFailed > 5) {
                LogUtils.printLog("Fail: 重试失败超过5次，异常退出！")
                throw CodeccDependentException("Fail: 重试失败超过5次，异常退出！", toolName = toolName)
            }
            if (commandParam.openScanPrj == true) {
                Thread.sleep(30000)
            } else {
                Thread.sleep(2000)
            }

            try {
                val data = CodeccWeb.codeccGetData(commandParam.landunParam, analyzeConfigInfo.taskId, toolName)
                    ?: throw CodeccDependentException("codeccGetData failed.", toolName = toolName)
                if (data.flag == 2 || data.flag == 4) {
                    val errorMsg = data.stepArray?.lastOrNull()?.msg ?: "任务被中断或发生异常!"
                    throw CodeccDependentException("Fail: $errorMsg \n$data", toolName = toolName)
                } else if (data.currStep == currentStep && data.flag == 1) {
                    LogUtils.printLog("")
                    LogUtils.printLog("finished!\n")
                    break
                }
                if (syncNum % 60 == 0) {
                    LogUtils.printLog("")
                }
                LogUtils.printStr(".")
                syncNum += 1
            } catch (e: Throwable) {
                LogUtils.printErrorLog("codeccGetData exception: ${e.message}")
                countFailed++
            }
        }
    }

    private fun waitCodeccFinish(commandParam: CommandParam, streamName: String, toolName: String, analyzeConfigInfo: AnalyzeConfigInfo) {
        // 通知后台解析告警文件
        LogUtils.printLog("notify CodeCC to parse file")
        CodeccWeb.notifyCodeccFinish(commandParam.landunParam, streamName, toolName)

        // 循环获取状态
        val reportStart = System.currentTimeMillis()
        while (true) {
            if (commandParam.openScanPrj == true) {
                Thread.sleep(30000)
            } else {
                Thread.sleep(2000)
            }
            if (CodeccWeb.reportCodeccStatus(commandParam.landunParam, streamName, toolName)) {
                LogUtils.printLog("report CodeCC status finish!")
                break
            }
            val reportEnd = System.currentTimeMillis()
            if ((reportEnd - reportStart) >= 60 * 30 * 1000) {
                LogUtils.printLog("report CodeCC over 30mins")
                break
            }
        }

        // 扫描结束
        CodeccWeb.codeccUploadTaskLog(analyzeConfigInfo.taskId, streamName, toolName, commandParam.landunParam, 3, 1)
    }

    private fun getFields(commandParam: CommandParam): String {
        val fileContent = StringBuilder()
        val fields: Array<Field> = CommandParam::class.java.declaredFields

        return try {
            for (f in fields) {
                f.isAccessible = true       // 设置些属性是可以访问的
                val value = f[commandParam] // 得到此属性的值
                val name = f.name           // 得到此属性的名称
                fileContent.append("$name=$value\n")
            }
            fileContent.toString()
        } catch (e: IllegalAccessException) {
            LogUtils.printLog("get fields failed")
            ""
        }
    }

    private fun printDefects(
        commandParam: CommandParam,
        streamName: String,
        toolName: String,
        analyzeConfigInfo: AnalyzeConfigInfo
    ) {
        try {
            val outputFile = File(generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "tool_scan_output.json")
            val severitMap = mapOf(1 to "[Error]", 2 to "[Warning]", 3 to "[Info]")
            if (outputFile.exists()) {
                val checkerMap = mutableMapOf<String, String?>()
                if (analyzeConfigInfo.openCheckers != null){
                    val openCheckers = analyzeConfigInfo.openCheckers
                    openCheckers.forEach {
                        if(it.severity != 0){
                            checkerMap[it.checkerName] = severitMap.get(it.severity)
                        }
                    }
                }
                val outPutJson = if (toolName == ToolConstants.GITHUBSTATISTIC) {
                    "{defects:${outputFile.readText()}}"
                } else {
                    outputFile.readText()
                }
                val outputData = JsonUtil.to(outPutJson, object : TypeReference<Map<String, Any>>() {})
                val defects = outputData["defects"]
                if (defects is List<*>) {
                    defects.forEachIndexed { index, it ->
                        if (index > 10000) {
                            LogUtils.printLog("缺陷数量超过10000条，将省略打印....")
                            return
                        }
                        val defectStr = jacksonObjectMapper().writeValueAsString(it)
                        val defect = JsonUtil.to(defectStr, object : TypeReference<DefectsEntity>() {})
                        if(checkerMap.containsKey(defect.checkerName)){
                            defect.severity = checkerMap[defect.checkerName]
                        }
                        LogUtils.printDefect(defect, toolName)
                    }
                }
            } else {
                LogUtils.printLog("tools output file not exists: $outputFile")
            }
        } catch (e: Throwable) {
            LogUtils.printDebugLog("print defects exception: ${e.message}")
        }
    }

    private fun compressToolsScanOutput(
        commandParam: CommandParam,
        streamName: String,
        toolName: String,
        taskId: Long
    ): Boolean {
        try {
            val outputFile = File(generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "tool_scan_output.json")
            if (outputFile.exists()) {
                val bakFile = File(generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "tool_scan_output_bak.json")
                outputFile.copyTo(bakFile, true)
                val outputData = JsonUtil.to(outputFile.readText(), object : TypeReference<Map<String, Any>>() {})
                val defects = outputData["defects"]
                val fileData = mutableMapOf<String, Any>()
                if (defects != null) {
                    val zipBytes = CompressUtils.zlibCompress(jacksonObjectMapper().writeValueAsBytes(defects))
                    val zipStr = String(Base64.getEncoder().encode(zipBytes))
                    fileData["defectsCompress"] = zipStr
                    fileData["task_id"] = taskId.toString()
                    fileData["tool_name"] = toolName
                    fileData["stream_name"] = streamName
                    fileData["buildId"] = commandParam.landunParam.buildId
                    if (outputData.containsKey("project_id")) fileData["projectId"] = outputData["project_id"]!!.toString()
                    if (outputData.containsKey("report_id")) fileData["reportId"] = outputData["report_id"]!!.toString()
                    outputData.filter { it.key != "defects" }.forEach { key, value ->
                        fileData[key] = value
                    }
                }
                outputFile.writeText(JsonUtil.toJson(fileData))
                return true
            } else {
                LogUtils.printLog("compress tools output file not exists: $outputFile")
                return false
            }
        } catch (e: Throwable) {
            LogUtils.printDebugLog("compressToolsScanOutput exception, cause: ${e.message}")
            return false
        }
    }

    fun generateToolDataPath(codeccWorkspace: String, streamName: String, toolName: String): String {
        val toolDataPath = codeccWorkspace + File.separator + streamName + "_" + toolName
        if (!File(toolDataPath).exists()) {
            File(toolDataPath).mkdirs()
        }
        return toolDataPath
    }

    private fun getScanFileList(codeccWorkspace: String, streamName: String, toolName: String): List<String> {
        if (toolName == ToolConstants.GITHUBSTATISTIC) {
            return mutableListOf<String>()
        }
        val toolDataPath = generateToolDataPath(codeccWorkspace, streamName, toolName)
        val toolScanInput = toolDataPath + File.separator + "tool_scan_output.json"
        return CodeccConfig.fileListFromDefects(toolScanInput)
    }

    private fun md5Files(commandParam: CommandParam, streamName: String, toolName: String, filePathList: List<String>) {
        val outputFile = generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "md5_files"
        val md5FilesJson = mutableMapOf<String, Any?>()
        val md5FilesJsonmd5FileList = mutableListOf<MutableMap<String, String>>()
        filePathList.forEach { filePahth ->
            val file = File(CommonUtils.changePathToWindows(filePahth))

            if (file.isDirectory || ! file.exists()) {
                return@forEach
            }
            val md5Info = mutableMapOf<String, String>()
            md5Info["filePath"] = filePahth
            if (File(filePahth).isFile){
                md5Info["fileRelPath"] = filePahth.replace(getRelFilePath(commandParam, filePahth), "/").replace("//", "/")
            }
            file.inputStream().use {
                md5Info["md5"] = DigestUtils.md5Hex(it)
            }

            md5FilesJsonmd5FileList.add(md5Info)
        }
        md5FilesJson["files_list"] = md5FilesJsonmd5FileList
        File(outputFile).writeText(jacksonObjectMapper().writeValueAsString(md5FilesJson))

        if (File(outputFile).exists()) {
            LogUtils.printDebugLog("append md5 success upload outputFile...")
            CodeccWeb.upload(landunParam = commandParam.landunParam,
                filePath = outputFile,
                resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_md5.json",
                uploadType = "SCM_JSON",
                toolName = toolName)
        }
    }

    private fun md5FilesBak(commandParam: CommandParam, streamName: String, toolName: String, filePathList: List<String>) {
        val outputFile = generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "md5_files"
        val md5FilesJson = mutableMapOf<String, Any?>()
        val md5FilesJsonmd5FileList = mutableListOf<MutableMap<String, String>>()
        filePathList.forEach { filePahth ->
            val file = File(CommonUtils.changePathToWindows(filePahth))

            if (file.isDirectory) {
                return@forEach
            }
            val md5Info = mutableMapOf<String, String>()
            md5Info["filePath"] = filePahth
            if (commandParam.repoRelPathMap.filterNot { it.key.isBlank() }.isNotEmpty()) {
                commandParam.repoRelPathMap.forEach { repoRelPath ->
                    val codePath = CommonUtils.changePathToDocker(File(commandParam.landunParam.streamCodePath, repoRelPath.value).canonicalPath)
                    val re = Regex(codePath)
                    if (re.containsMatchIn(filePahth)) {
                        md5Info["fileRelPath"] = filePahth.replace(codePath, "/").replace("//", "/")
                    }
                }
            } else {
                md5Info["fileRelPath"] = filePahth.replace(commandParam.landunParam.streamCodePath, "/").replace("//", "/")
            }

            if (!md5Info.keys.contains("fileRelPath")){
                md5Info["fileRelPath"] = filePahth
            }

            file.inputStream().use {
                md5Info["md5"] = DigestUtils.md5Hex(it)
            }

            md5FilesJsonmd5FileList.add(md5Info)
        }
        md5FilesJson["files_list"] = md5FilesJsonmd5FileList
        File(outputFile).writeText(jacksonObjectMapper().writeValueAsString(md5FilesJson))

        if (File(outputFile).exists()) {
            LogUtils.printDebugLog("append md5 success upload outputFile...")
            CodeccWeb.upload(landunParam = commandParam.landunParam,
                filePath = outputFile,
                resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_md5.json",
                uploadType = "SCM_JSON",
                toolName = toolName)
        }
    }

    private fun getRelFilePath(commandParam: CommandParam, filePath: String): String {
        var scanDir = File(filePath).parent
        var clyeNum = 4096
        if (commandParam.scmType == "git" || commandParam.scmType == "github") {
            while (clyeNum > 0){
                scanDir = "$scanDir/.git"
                if (File(scanDir).isDirectory){
                    break
                }else if (File(scanDir).isFile){
                    break
                }else{
                    scanDir = File(File(scanDir).parent).parent
                    if (scanDir == "/" || scanDir.endsWith(":/") || scanDir.endsWith(":\\")){
                        break
                    }
                    clyeNum--
                    LogUtils.printDebugLog(scanDir.toString())
                }
            }
        }else if (commandParam.scmType == "svn"){
            while (clyeNum > 0){
                scanDir = "$scanDir/.svn"
                if (File(scanDir).isDirectory){
                    break
                }else if (File(scanDir).isFile){
                    break
                }else{
                    scanDir = File(File(scanDir).parent).parent
                    if (scanDir == "/" || scanDir.endsWith(":/") || scanDir.endsWith(":\\")){
                        break
                    }
                    clyeNum--
                    LogUtils.printDebugLog(scanDir.toString())
                }
            }
        }else{
            val codePath = CommonUtils.changePathToDocker(File(commandParam.landunParam.streamCodePath).canonicalPath)
            scanDir = scanDir.replace(codePath, "/").replace("//", "/")
        }
        if (scanDir.endsWith(".git") || scanDir.endsWith(".svn")){
            scanDir = File(scanDir).parent
        }
        return scanDir
    }

    private fun checkUpdateFilesIsExistDefects(
        incrementFiles: List<String>,
        deleteFiles: List<String>,
        filePathList: List<String>,
        scanType: ScanType
    ): List<String> {
        val deleteFileList = mutableListOf<String>()

        if (scanType == ScanType.INCREMENT) {
            deleteFileList.addAll(deleteFiles)
            LogUtils.printLog(JsonUtil.toJson(deleteFiles))
            incrementFiles.forEach { filePath ->
                if (!filePathList.contains(filePath)) {
                    deleteFileList.add(filePath)
                }
            }
        }
        return deleteFileList
    }

    private fun incrementVersionInfo(
        commandParam: CommandParam,
        streamName: String,
        toolName: String,
        taskId: Long,
        deleteFileList: List<String>
    ): MutableMap<String, Any?>? {
        val scmInfoFile = commandParam.dataRootPath+ File.separator + "scm_info_output.json"
        if (!File(scmInfoFile).exists()) {
            return null
        }
        val outPutFileText = File(scmInfoFile).readText()
        val outPutFileObj = jacksonObjectMapper().readValue<List<Map<String, Any?>>>(outPutFileText)
        val params: MutableMap<String, Any?> = mutableMapOf()
        params["toolName"] = toolName.toUpperCase()
        params["repoList"] = outPutFileObj
        params["taskId"] = taskId.toString()
        params["buildId"] = commandParam.landunParam.buildId
        params["projectId"] = commandParam.landunParam.devopsProjectId
        params["deleteFiles"] = deleteFileList
        params["repoWhiteList"] = commandParam.subCodePathList
        LogUtils.printLog("repoWhiteList is ${params["repoWhiteList"]}")
        params["triggerToolNames"] = commandParam.scanTools.toUpperCase().split(",")
        LogUtils.printLog("triggerToolNames is ${params["triggerToolNames"]}")

        return params
    }

    private  fun checkIncludeCompileLanuage(inputLanguage: Long?): Boolean{
        //C#
        if ((inputLanguage!! and 1L) > 0) {
            return true
        }
        //C++
        if ((inputLanguage!! and 2L) > 0) {
            return true
        }
        //JAVA
        if ((inputLanguage!! and 4L) > 0) {
            return true
        }
        //OC
        if ((inputLanguage!! and 16L) > 0) {
            return true
        }
        //GOLANG
        if ((inputLanguage!! and 512L) > 0) {
            return true
        }
        //KOTLIN
        if ((inputLanguage!! and 4096L) > 0) {
            return true
        }
        return false
    }
}
