package com.tencent.devops.docker

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.tools.FileUtil
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.hash.pojo.SummaryGatherInfo
import com.tencent.devops.hash.pojo.HashCCNCommonFile
import com.tencent.devops.hash.pojo.HashCCNInputObj
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCToolException
import com.tencent.devops.pojo.scan.LARGE_REPORT_HEAD_HTML
import com.tencent.devops.pojo.scan.LARGE_REPORT_TAIL_HTML
import com.tencent.devops.processor.AbstractDefectSubProcessor
import com.tencent.devops.processor.annotation.ProcessAnnotation
import com.tencent.devops.utils.I18NUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.reflections.Reflections
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CCNDefectProcessor{

    private var defectSubProcessors = listOf<AbstractDefectSubProcessor>()

    /**
     * 初始化处理器清单
     */
    fun init(executeList: List<String>) {
        if (defectSubProcessors.isNullOrEmpty()) {
            synchronized(CCNDefectProcessor::class) {
                if (defectSubProcessors.isNullOrEmpty()) {
                    val reflection = Reflections("com.tencent.devops")
                    val defectSubProcessorList = reflection.getSubTypesOf(AbstractDefectSubProcessor::class.java)
                    defectSubProcessors = defectSubProcessorList.filter {
                        val processAnnotation = it.getAnnotation(ProcessAnnotation::class.java)
                        null != processAnnotation && executeList.contains(processAnnotation.name) && processAnnotation.type in listOf("ccn", "common")
                    }.sortedBy { it.getAnnotation(ProcessAnnotation::class.java).order }.map { it.newInstance() }
                }
            }
        }
    }


    fun generateCCNListDefect(
            inputFileName: String,
            commandParam: CommandParam,
            streamName: String,
            toolName: String
    ) {
        LogUtils.printLog("start process tool image output defect info")
        val startTime = System.currentTimeMillis()
        //读取文件并解析
        val inputFile = File(inputFileName)
        if (!inputFile.exists()) {
            LogUtils.printLog("input file does not exists!")
            return
        }
        val inputFileObj = try {
            JsonUtil.to(inputFile.readText(), object : TypeReference<HashCCNInputObj>() {})
        } catch (t: Throwable) {
            LogUtils.printLog("deserialize input file fail! error message: ${t.message}")
            val fileSize = inputFile.length() / 1024 / 1024 / 1024
            if (fileSize > 2) {
                LogUtils.printErrorLog("Defect file is too large! File size: $fileSize G, please scan part of the " +
                    "code first")
                throw CodeCCToolException(
                    ErrorCode.TOOL_DEFECT_SIZE_LIMIT,
                    "Defect file is too large! File size: $fileSize G, please scan part of the code first",
                    arrayOf("2G"),
                    toolName
                )
            } else {
                return
            }
        }
        val hashInputList = inputFileObj.defects
        //定义输入和输出的通道
        val ccnInputChannel = Channel<Pair<String, List<HashCCNCommonFile>>>()
        val ccnOutputChannel = Channel<Pair<String, FileProcessResult>>(10)
        //开始监听输入的channel
        val awaitList = processDefects(ccnInputChannel, ccnOutputChannel, defectSubProcessors, commandParam)
        GlobalScope.launch {
            awaitList.forEach {
                it.await()
            }
            ccnOutputChannel.close()
        }
        //将按照文件路径分类出来的数据放进channel，放入之后，关闭输入channel
        val hashFileList = hashInputList.groupBy { it.filePath }
        GlobalScope.launch {
            hashFileList.forEach { (t, u) ->
                ccnInputChannel.send(Pair(t, u))
            }
            ccnInputChannel.close()
        }
        //收集数据
        collectCCNOutDefect(ccnOutputChannel, inputFileObj, commandParam, streamName, toolName)
        LogUtils.printLog("process defect info finish! time cost: ${System.currentTimeMillis() - startTime}")

    }


    private fun processDefects(inputChannel: Channel<Pair<String, List<HashCCNCommonFile>>>,
                               outputChannel: Channel<Pair<String, FileProcessResult>>,
                               defectSubProcessors: List<AbstractDefectSubProcessor>,
                               commandParam: CommandParam): MutableList<Deferred<Boolean>> {
        val awaitList = mutableListOf<Deferred<Boolean>>()
        var j = 0
        while (!IgnoreDefectParser.continueProcess() && j < 3600) {
            j++
            Thread.sleep(500L)
        }
        LogUtils.printLog("start to process file info from the input channel")
        for (i in 0 until 8) {
            awaitList.add(GlobalScope.async {
                LogUtils.printLog("current thread: ${Thread.currentThread().name}")
                for (defectMap in inputChannel) {
                    val fileProcessResult = defectSubProcessors.fold(
                            FileProcessResult(
                                    filePath = defectMap.first,
                                    ccnDefects = defectMap.second,
                                    commandParam = commandParam,
                                    ignoreDefectInfo = IgnoreDefectParser.getIgnoreDefectMapByFilePath(defectMap.first)
                            )) { acc, abstractDefectSubProcessor ->
                        abstractDefectSubProcessor.mainDefectSubProcess(acc)
                    }
                    outputChannel.send(
                            Pair(defectMap.first, fileProcessResult)
                    )
                }
                true
            })
        }
        return awaitList
    }


    //消费处理输出内容(当前包括告警信息和md5信息)
    private fun collectCCNOutDefect(ccnOutputChannel: Channel<Pair<String, FileProcessResult>>,
                                    inputFileObj : HashCCNInputObj,
                                     commandParam: CommandParam,
                                     streamName: String,
                                     toolName: String) {
        LogUtils.printLog("collect and process output defect")
        val defectOutputFileName = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "tool_scan_output.json"
        val defectOutputFile = File(defectOutputFileName)
        val md5OutputFileName = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "md5_files"
        val md5OutputFile = File(md5OutputFileName)
        val filePathListFileName = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "file_path_list.json"
        val filePathListFile = File(filePathListFileName)
        val hashCCNOutputList = mutableListOf<HashCCNCommonFile>()
        val md5FilesJson = mutableMapOf<String, Any?>()
        val md5FilesJsonmd5FileList = mutableListOf<MutableMap<String, String>>()
        val filePathList = mutableListOf<String>()
        //将后续文件格式的拼接逻辑放在这里
        runBlocking {
            for (outputDefectList in ccnOutputChannel) {
                //1. 添加告警输出内容
                if(!outputDefectList.second.ccnDefects.isNullOrEmpty()) {
                    hashCCNOutputList.addAll(outputDefectList.second.ccnDefects!!)
                }

                //2. 添加md5内容
                val filePath = outputDefectList.second.filePath
                val fileRelPath = outputDefectList.second.fileRelPath
                val file = File(CommonUtils.changePathToWindows(filePath))
                if (file.isDirectory || ! file.exists()) {
                    continue
                }
                val md5Info = mutableMapOf<String, String>()
                md5Info["filePath"] = CommonUtils.changePathToDocker(filePath)
                md5Info["fileOriginalPath"] = filePath
                if (File(filePath).isFile){
                    md5Info["fileRelPath"] = fileRelPath?:""
                }
                md5Info["md5"] = outputDefectList.second.fileMd5?:""
                md5FilesJsonmd5FileList.add(md5Info)

                //3. 添加filaPath路径内容
                filePathList.add(outputDefectList.first)
            }
        }

        //处理大量告警文件
        var summaryGather : SummaryGatherInfo? = null
        if (!inputFileObj.lowThresholdDefects.isNullOrEmpty()) {
            val fileName = streamName + "_" + toolName + "_" + commandParam.landunParam.buildId + "_codecc_defect_detail.zip"
            try {
                LogUtils.printDebugLog("generate gather detail report...")
                val filePath = generateLowerThresholdDefectReport(commandParam, inputFileObj.lowThresholdDefects)
                LogUtils.printDebugLog("generate gather detail report success, filePath: $filePath")
                val zipFile = FileUtil.zipFile(filePath)
                LogUtils.printDebugLog("zip gather detail report success, zipFilePath: $zipFile")
                CodeccWeb.upload(landunParam = commandParam.landunParam,
                    filePath = zipFile,
                    resultName = fileName,
                    uploadType = "GATHER",
                    toolName = toolName)
                LogUtils.printDebugLog("upload gather detail report success, resultName: $fileName")
            } catch (e: Throwable) {
                LogUtils.printLog("Upload defect detail exception: ${e.message}")
            }
            summaryGather = SummaryGatherInfo(fileName,inputFileObj.lowThresholdDefects.size,
                inputFileObj.lowThresholdDefects.groupBy { it.filePath }.size)
        }

        LogUtils.printLog("process defect output, defect file size: ${hashCCNOutputList.size}")
        //处理告警输出
        val outputMap = mapOf("defects" to hashCCNOutputList, "filesTotalCCN" to inputFileObj.filesTotalCCN
            , "gather" to summaryGather)
        defectOutputFile.bufferedWriter().use { out -> out.write(JsonUtil.toJson(outputMap)) }

        LogUtils.printLog("process md5 output, md5 file size: ${md5FilesJsonmd5FileList.size}")
        //处理md5信息
        md5FilesJson["files_list"] = md5FilesJsonmd5FileList
        md5OutputFile.writeText(JsonUtil.toJson(md5FilesJson))
        if (md5OutputFile.exists()) {
            LogUtils.printDebugLog("append md5 success upload outputFile...")
            CodeccWeb.upload(landunParam = commandParam.landunParam,
                    filePath = md5OutputFileName,
                    resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_md5.json",
                    uploadType = "SCM_JSON",
                    toolName = toolName)
        }
        //处理路径信息
        filePathListFile.writeText(JsonUtil.toJson(filePathList))
    }

    //生成大报告
    private fun generateLowerThresholdDefectReport(
        commandParam: CommandParam,
        lowerThresholdDefects: List<HashCCNCommonFile>
    ): String {
        val filePathGroup = lowerThresholdDefects.groupBy { it.filePath }
        val fileCount = filePathGroup.size
        val totalDefectCount = lowerThresholdDefects.size
        val curTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val detailFile = File(commandParam.dataRootPath + File.separator + "codecc_defect_detail.html")

        val indexHtmlBody = StringBuilder()
        val baseHtml = LARGE_REPORT_HEAD_HTML.replace("large.report.head.html.title",
            I18NUtils.getMessage("large.report.head.html.title")).replace("large.report.head.html.h1",
            I18NUtils.getMessage("large.report.head.html.h1"))

        indexHtmlBody.append(baseHtml)
        if (I18NUtils.currentLanguageIsZhCN()) {
            indexHtmlBody.append("<span>共 $fileCount 个文件， $totalDefectCount 个问题</span> - 生成于 $curTime")
            indexHtmlBody.append(
                "</div>\n" +
                    "    </div>\n" +
                    "    <table>\n" +
                    "        <tbody>"
            )
            var fileNo = 0
            filePathGroup.forEach {
                val fileDefectCount = it.value.size
                indexHtmlBody.append(
                    "<tr class=\"bg-2\" data-group=\"f-${fileNo}\">\n" +
                        "                <th colspan=\"4\">\n" +
                        "                    [+] ${it.key}\n" +
                        "                    <span>$fileDefectCount 个问题</span>\n" +
                        "                </th>\n" +
                        "            </tr>\n"
                )
                it.value.forEach { defect ->
                    indexHtmlBody.append(
                        "<tr style=\"display:none\" class=\"f-${fileNo}\">\n" +
                            "                <td>${defect.startLine}-${defect.endLine}</td>\n" +
                            "                <td class=\"clr-2\">${defect.functionNames ?: ""}</td>\n" +
                            "                <td>${defect.ccn ?: ""}</td>\n" +
                            "            </tr>"
                    )
                }
                fileNo++
            }
        } else {
            indexHtmlBody.append("<span>Total $fileCount files, $totalDefectCount defects</span> - Generated" +
                " on $curTime")
            indexHtmlBody.append(
                "</div>\n" +
                    "    </div>\n" +
                    "    <table>\n" +
                    "        <tbody>"
            )
            var fileNo = 0
            filePathGroup.forEach {
                val fileDefectCount = it.value.size
                indexHtmlBody.append(
                    "<tr class=\"bg-2\" data-group=\"f-${fileNo}\">\n" +
                        "                <th colspan=\"4\">\n" +
                        "                    [+] ${it.key}\n" +
                        "                    <span>$fileDefectCount Issues</span>\n" +
                        "                </th>\n" +
                        "            </tr>\n"
                )
                it.value.forEach { defect ->
                    indexHtmlBody.append(
                        "<tr style=\"display:none\" class=\"f-${fileNo}\">\n" +
                            "                <td>${defect.startLine}-${defect.endLine}</td>\n" +
                            "                <td class=\"clr-2\">${defect.functionNames ?: ""}</td>\n" +
                            "                <td>${defect.ccn ?: ""}</td>\n" +
                            "            </tr>"
                    )
                }
                fileNo++
            }
        }
        indexHtmlBody.append(LARGE_REPORT_TAIL_HTML)
        detailFile.writeText(indexHtmlBody.toString())
        return detailFile.canonicalPath
    }

}
