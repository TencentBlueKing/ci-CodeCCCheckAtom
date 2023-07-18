package com.tencent.devops.docker

import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.DefectsEntity
import com.tencent.devops.docker.pojo.FileDefects
import com.tencent.devops.docker.pojo.Gather
import com.tencent.devops.docker.tools.FileUtil
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.hash.pojo.HashLintCommonFile
import com.tencent.devops.hash.pojo.LintCommonOutputFile
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCToolException
import com.tencent.devops.pojo.scan.LARGE_REPORT_HEAD_HTML
import com.tencent.devops.pojo.scan.LARGE_REPORT_TAIL_HTML
import com.tencent.devops.processor.AbstractDefectSubProcessor
import com.tencent.devops.processor.annotation.ProcessAnnotation
import com.tencent.devops.utils.I18NUtils
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.reflections.Reflections
import java.io.BufferedReader
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LintDefectProcessor {

    private var defectSubProcessors = listOf<AbstractDefectSubProcessor>()

    private val defaultSubProcessors = listOf("lintPPHash", "md5", "lintIgnore")

    /**
     * 初始化处理器清单
     */
    fun init(executeList: List<String>) {
        if (defectSubProcessors.isNullOrEmpty()) {
            synchronized(LintDefectProcessor::class) {
                if (defectSubProcessors.isNullOrEmpty()) {
                    val reflection = Reflections("com.tencent.devops")
                    val defectSubProcessorList = reflection.getSubTypesOf(AbstractDefectSubProcessor::class.java)
                    defectSubProcessors = defectSubProcessorList.filter {
                        val processAnnotation = it.getAnnotation(ProcessAnnotation::class.java)
                        null != processAnnotation && executeList.contains(processAnnotation.name) && processAnnotation.type in listOf(
                            "lint",
                            "common"
                        )
                    }.sortedBy { it.getAnnotation(ProcessAnnotation::class.java).order }.map { it.newInstance() }
                }
            }
        }
    }


    /**
     * 采用协程的方法对工具的输出文件进行二次处理
     * 1.分别定义输入和输出channel，输入channel负责解析告警后新起固定数量的协程
     */
    fun generateLintDefectList(
        inputFileName: String,
        commandParam: CommandParam,
        streamName: String,
        toolName: String
    ) {
        LogUtils.printLog("start get tool meta info $toolName")
        val toolMetaDetailVO = CodeccWeb.getToolMeta(commandParam.landunParam, toolName)
        val processList = toolMetaDetailVO?.processList ?: defaultSubProcessors
        LogUtils.printLog("end get tool meta info $toolName $processList")
        val startTime = System.currentTimeMillis()
        //读取文件并解析
        val inputFile = File(inputFileName)
        if (!inputFile.exists()) {
            LogUtils.printLog("input file does not exists!")
            return
        }
        val fileSize = inputFile.length() / 1024 / 1024
        LogUtils.printLog("Defect file: $inputFileName, file size: $fileSize M")
        if (fileSize > 1024) {
            LogUtils.printErrorLog("Defect file is too large! It has exceeded 1G, please scan part of the code first," +
                " such as adding a whitelist to specify the code path that needs to be scanned")
            throw CodeCCToolException(
                ErrorCode.TOOL_DEFECT_SIZE_LIMIT,
                "tool defect size limit reached: limit: 1024 M",
                arrayOf("1024M"),
                toolName
            )
        }
        val hashInputMap = try {
            JsonUtil.to(inputFile.bufferedReader().use(BufferedReader::readText), LintCommonOutputFile::class.java)
        } catch (t: Throwable) {
            throw CodeCCToolException(
                ErrorCode.TOOL_DEFECT_DESERIALIZE_ERROR,
                "deserialize input file fail! error message: ${t.message}",
                emptyArray(),
                toolName
            )
        }
        val hashInputList = hashInputMap.defects
        //定义输入和输出的通道
        val lintInputChannel = Channel<Pair<String, List<HashLintCommonFile>>>()
        val lintOutputChannel = Channel<Pair<String, FileProcessResult>>(10)
        //开始监听输入的channel
        val awaitList =
            processDefects(lintInputChannel, lintOutputChannel, defectSubProcessors, commandParam, processList)
        //等待协程返回的值,判断处理是否结束，结束则关闭输出channel
        GlobalScope.launch {
            awaitList.forEach {
                it.await()
            }
            lintOutputChannel.close()
        }
        //将按照文件路径分类出来的数据放进channel，放入之后，关闭输入channel
        val hashFileList = hashInputList.groupBy { it.filePath }
        GlobalScope.launch {
            hashFileList.forEach { (t, u) ->
                if (Build.headFileFiter) {
                    if (toolMetaDetailVO?.lang == 2L && OCHeaderFileParser.ocHeadFileContains(t)) {
                        LogUtils.printLog("$t is oc head file, no need to process cpplint")
                        return@forEach
                    }
                    if (toolMetaDetailVO?.lang == 16L && t.endsWith(".h") &&
                        !OCHeaderFileParser.ocHeadFileContains(t)
                    ) {
                        LogUtils.printLog("$t is cpp head file, no need to process bkcheck-oc")
                        return@forEach
                    }
                }
                lintInputChannel.send(Pair(t, u))
            }
            lintInputChannel.close()
        }
        //收集数据
        collectLintOutDefect(lintOutputChannel, commandParam, streamName, toolName)
        LogUtils.printLog("process defect info finish! time cost: ${System.currentTimeMillis() - startTime}")
    }

    /**
     * 处理告警的方法
     * 1. 固定协程数进行处理
     * 2. 每个工具都单独开一个channel，防止空转损耗性能
     */
    private fun processDefects(
        inputChannel: Channel<Pair<String, List<HashLintCommonFile>>>,
        outputChannel: Channel<Pair<String, FileProcessResult>>,
        defectSubProcessors: List<AbstractDefectSubProcessor>,
        commandParam: CommandParam,
        processList: List<String>
    ): MutableList<Deferred<Boolean>> {
        val awaitList = mutableListOf<Deferred<Boolean>>()
        var j = 0
        while (!IgnoreDefectParser.continueProcess() && j < 3600) {
            j++
            Thread.sleep(500L)
        }
        LogUtils.printLog("start to process file info from the input channel $defectSubProcessors")
        for (i in 0 until 8) {
            awaitList.add(GlobalScope.async {
                LogUtils.printStr("current thread: ${Thread.currentThread().name}")
                for (defectMap in inputChannel) {
                    val fileProcessResult = defectSubProcessors.filter {
                        processList.contains(
                            it.javaClass.getDeclaredAnnotation(ProcessAnnotation::class.java).name
                        )
                    }.fold(
                        FileProcessResult(
                            filePath = defectMap.first,
                            lintDefects = defectMap.second,
                            commandParam = commandParam,
                            ignoreDefectInfo = IgnoreDefectParser.getIgnoreDefectMapByFilePath(defectMap.first)
                        )
                    ) { acc, abstractDefectSubProcessor ->
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
    private fun collectLintOutDefect(
        lintOutputChannel: Channel<Pair<String, FileProcessResult>>,
        commandParam: CommandParam,
        streamName: String,
        toolName: String
    ) {
        LogUtils.printDebugLog("collect and process output defect")
        val defectOutputFileName = ScanComposer.generateToolDataPath(
            commandParam.dataRootPath,
            streamName,
            toolName
        ) + File.separator + "tool_scan_output_file.json"
        val defectOutputFile = File(defectOutputFileName)
        val md5OutputFileName = ScanComposer.generateToolDataPath(
            commandParam.dataRootPath,
            streamName,
            toolName
        ) + File.separator + "md5_files"
        val md5OutputFile = File(md5OutputFileName)
        val filePathListFileName = ScanComposer.generateToolDataPath(
            commandParam.dataRootPath,
            streamName,
            toolName
        ) + File.separator + "file_path_list.json"
        val filePathListFile = File(filePathListFileName)
        val fileDefects = mutableListOf<FileDefects>()
        val gatherDefects = mutableListOf<FileDefects>()
        val md5FilesJson = mutableMapOf<String, Any?>()
        val md5FilesJsonmd5FileList = mutableListOf<MutableMap<String, String>>()
        val filePathList = mutableSetOf<String>()
        var needGather = false
        //将后续文件格式的拼接逻辑放在这里
        runBlocking {
            for (outputDefectList in lintOutputChannel) {
                //1. 添加告警输出内容
                val resultDefectList = outputDefectList.second.lintDefects?.map {
                    DefectsEntity(
                        checkerName = it.checkerName,
                        description = it.description,
                        line = it.line,
                        ignoreCommentDefect = it.ignoreCommentDefect,
                        ignoreCommentReason = it.ignoreCommentReason,
                        pinpointHash = it.pinpointHash,
                        langValue = it.langValue,
                        language = it.language,
                        author = it.author,
                        revision = it.revision,
                        lineUpdateTime = it.lineUpdateTime,
                        branch = it.branch,
                        relPath = it.relPath,
                        url = it.url,
                        defectInstances = it.defectInstances
                    )
                }
                if (!resultDefectList.isNullOrEmpty()) {
                    if (resultDefectList.size > commandParam.gatherDefectThreshold) {
                        fileDefects.add(
                            FileDefects(
                                outputDefectList.first,
                                null,
                                Gather(resultDefectList.size.toLong(), null)
                            )
                        )
                        gatherDefects.add(FileDefects(outputDefectList.first, resultDefectList, null))
                        needGather = true
                    } else {
                        fileDefects.add(
                            FileDefects(
                                CommonUtils.changePathToDocker(outputDefectList.first),
                                resultDefectList,
                                null
                            )
                        )
                    }
                }

                //2. 添加md5内容
                var defectTraceFileMap = outputDefectList.second.defectTraceFileMap
                if (defectTraceFileMap != null) {
                    for ((filePath, fileMd5Info) in defectTraceFileMap) {
                        val file = File(CommonUtils.changePathToWindows(filePath))
                        if (file.isDirectory || !file.exists()) {
                            continue
                        }
                        val md5Info = mutableMapOf<String, String>()
                        md5Info["filePath"] = CommonUtils.changePathToDocker(filePath)
                        md5Info["fileOriginalPath"] = CommonUtils.changePathToWindows(filePath)
                        if (File(md5Info["fileOriginalPath"]).isFile) {
                            md5Info["fileRelPath"] = fileMd5Info.fileRelPath ?: ""
                        }
                        md5Info["md5"] = fileMd5Info.fileMd5 ?: ""
                        md5FilesJsonmd5FileList.add(md5Info)
                    }
                }

                //3. 添加filaPath路径内容
                val file = File(CommonUtils.changePathToWindows(outputDefectList.first))
                if (file.isDirectory || !file.exists()) {
                    continue
                }
                filePathList.add(outputDefectList.first)
            }
        }

        //处理大量告警文件
        if (needGather && gatherDefects.isNotEmpty()) {
            val fileName =
                streamName + "_" + toolName + "_" + commandParam.landunParam.buildId + "_codecc_defect_detail.zip"
            fileDefects.add(FileDefects(fileName, null, Gather(null, true)))
            try {
                LogUtils.printDebugLog("generate gather detail report...")
                val filePath = generateLargeReport(commandParam, gatherDefects)
                LogUtils.printDebugLog("generate gather detail report success, filePath: $filePath")
                val zipFile = FileUtil.zipFile(filePath)
                LogUtils.printDebugLog("zip gather detail report success, zipFilePath: $zipFile")
                CodeccWeb.upload(
                    landunParam = commandParam.landunParam,
                    filePath = zipFile,
                    resultName = fileName,
                    uploadType = "GATHER",
                    toolName = toolName
                )
                LogUtils.printDebugLog("upload gather detail report success, resultName: $fileName")
            } catch (e: Throwable) {
                LogUtils.printLog("Upload defect detail exception: ${e.message}")
            }
        }

        LogUtils.printDebugLog("process defect output, defect file size: ${fileDefects.size}")
        //处理告警输出
        defectOutputFile.bufferedWriter().use { out -> out.write(JsonUtil.toJson(fileDefects)) }

        LogUtils.printDebugLog("process md5 output, md5 file size: ${md5FilesJsonmd5FileList.size}")
        //处理md5信息
        md5FilesJson["files_list"] = md5FilesJsonmd5FileList
        md5OutputFile.writeText(JsonUtil.toJson(md5FilesJson))
        if (md5OutputFile.exists()) {
            LogUtils.printDebugLog("append md5 success upload outputFile...")
            CodeccWeb.upload(
                landunParam = commandParam.landunParam,
                filePath = md5OutputFileName,
                resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_md5.json",
                uploadType = "SCM_JSON",
                toolName = toolName
            )
        }
        //处理路径信息
        filePathListFile.writeText(JsonUtil.toJson(filePathList))
    }

    //生成大报告
    private fun generateLargeReport(commandParam: CommandParam, largeFileDefects: MutableList<FileDefects>): String {
        val fileCount = largeFileDefects.size
        val totalDefectCount = largeFileDefects.sumBy { it.defects!!.size }

        LogUtils.printErrorLog(" A total of $fileCount defect files, the threshold ${commandParam
            .gatherDefectThreshold} has been exceeded, these files have been converged and archived for a total of " +
            "$totalDefectCount defects. You can go to the CodeCC tool analysis record download file to view details")

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
            largeFileDefects.forEach {
                val fileDefectCount = if (it.defects == null) 0 else it.defects.size
                indexHtmlBody.append(
                    "<tr class=\"bg-2\" data-group=\"f-${fileNo}\">\n" +
                        "                <th colspan=\"4\">\n" +
                        "                    [+] ${it.file}\n" +
                        "                    <span>$fileDefectCount 个问题</span>\n" +
                        "                </th>\n" +
                        "            </tr>\n"
                )
                if (it.defects != null && it.defects.isNotEmpty()) {
                    it.defects.forEach { defect ->
                        indexHtmlBody.append(
                            "<tr style=\"display:none\" class=\"f-${fileNo}\">\n" +
                                "                <td>${defect.line ?: ""}</td>\n" +
                                "                <td class=\"clr-2\">${defect.checkerName ?: ""}</td>\n" +
                                "                <td>${defect.description ?: ""}</td>\n" +
                                "            </tr>"
                        )
                    }
                }
                fileNo++
            }
        } else {
            indexHtmlBody.append("<span>Total $fileCount files， $totalDefectCount defects</span> - Generated" +
                " on $curTime")
            indexHtmlBody.append(
                "</div>\n" +
                    "    </div>\n" +
                    "    <table>\n" +
                    "        <tbody>"
            )
            var fileNo = 0
            largeFileDefects.forEach {
                val fileDefectCount = if (it.defects == null) 0 else it.defects.size
                indexHtmlBody.append(
                    "<tr class=\"bg-2\" data-group=\"f-${fileNo}\">\n" +
                        "                <th colspan=\"4\">\n" +
                        "                    [+] ${it.file}\n" +
                        "                    <span>$fileDefectCount Issues</span>\n" +
                        "                </th>\n" +
                        "            </tr>\n"
                )
                if (it.defects != null && it.defects.isNotEmpty()) {
                    it.defects.forEach { defect ->
                        indexHtmlBody.append(
                            "<tr style=\"display:none\" class=\"f-${fileNo}\">\n" +
                                "                <td>${defect.line ?: ""}</td>\n" +
                                "                <td class=\"clr-2\">${defect.checkerName ?: ""}</td>\n" +
                                "                <td>${defect.description ?: ""}</td>\n" +
                                "            </tr>"
                        )
                    }
                }
                fileNo++
            }
        }
        indexHtmlBody.append(LARGE_REPORT_TAIL_HTML)
        detailFile.writeText(indexHtmlBody.toString())
        return detailFile.canonicalPath
    }


}
