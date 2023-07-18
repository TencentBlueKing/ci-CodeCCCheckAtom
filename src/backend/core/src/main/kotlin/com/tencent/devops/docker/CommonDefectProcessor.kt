package com.tencent.devops.docker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.FileDefects
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.processor.AbstractDefectSubProcessor
import com.tencent.devops.processor.annotation.ProcessAnnotation
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.reflections.Reflections
import java.io.BufferedReader
import java.io.File

object CommonDefectProcessor {

    private var defectSubProcessors = listOf<AbstractDefectSubProcessor>()

    /**
     * 初始化处理器清单
     */
    fun init(executeList: List<String>) {
        if (defectSubProcessors.isNullOrEmpty()) {
            synchronized(CommonDefectProcessor::class) {
                if (defectSubProcessors.isNullOrEmpty()) {
                    val reflection = Reflections("com.tencent.devops")
                    val defectSubProcessorList = reflection.getSubTypesOf(AbstractDefectSubProcessor::class.java)
                    defectSubProcessors = defectSubProcessorList.filter {
                        val processAnnotation = it.getAnnotation(ProcessAnnotation::class.java)
                        null != processAnnotation && executeList.contains(processAnnotation.name) && processAnnotation.type in listOf("common")
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
            toolName: String) {
        LogUtils.printLog("start process tool image output defect info")
        val startTime = System.currentTimeMillis()
        //读取文件并解析
        val inputFile = File(inputFileName)
        if (!inputFile.exists()) {
            LogUtils.printLog("input file does not exists!")
            return
        }
        val hashInputMap = try {
            jacksonObjectMapper().readValue(
                inputFile.bufferedReader().use (BufferedReader::readText), object : TypeReference<Map<String, Any?>>() {})
        } catch (e: Exception) {
            LogUtils.printLog("deserialize input file fail!")
            return
        } ?: return
        if(!hashInputMap.containsKey("defects") || null == hashInputMap["defects"]) {
            LogUtils.printLog("input map has no key defects")
            return
        }
        val hashInputList = hashInputMap.getValue("defects") as List<Map<String, String>>
        //定义输入和输出的通道
        val commonInputChannel = Channel<String>()
        val commonOutputChannel = Channel<Pair<String, FileProcessResult>>(10)
        //开始监听输入的channel
        val awaitList = processDefects(commonInputChannel, commonOutputChannel, defectSubProcessors, commandParam)
        //等待协程返回的值,判断处理是否结束，结束则关闭输出channel
        GlobalScope.launch {
            awaitList.forEach {
                it.await()
            }
            commonOutputChannel.close()
        }
        //将按照文件路径分类出来的数据放进channel，放入之后，关闭输入channel
        GlobalScope.launch {
            hashInputList.forEach {defect ->
                when {
                    defect["filePath"] != null -> commonInputChannel.send(CommonUtils.changePathToDocker(defect["filePath"] as String))
                    defect["filePathname"] != null -> commonInputChannel.send(CommonUtils.changePathToDocker(defect["filePathname"] as String))
                    defect["filename"] != null -> commonInputChannel.send(CommonUtils.changePathToDocker(defect["filename"] as String))
                    defect["file_path"] != null -> commonInputChannel.send(CommonUtils.changePathToDocker(defect["file_path"] as String))
                }
            }
            commonInputChannel.close()
        }
        //收集数据
        collectLintOutDefect(commonOutputChannel, commandParam, streamName, toolName)
        LogUtils.printLog("process defect info finish! time cost: ${System.currentTimeMillis() - startTime}")
    }


    /**
     * 处理告警的方法
     * 1. 固定协程数进行处理
     * 2. 每个工具都单独开一个channel，防止空转损耗性能
     */
    private fun processDefects(inputChannel: Channel<String>,
                               outputChannel: Channel<Pair<String, FileProcessResult>>,
                               defectSubProcessors: List<AbstractDefectSubProcessor>,
                               commandParam: CommandParam): MutableList<Deferred<Boolean>> {
        val awaitList = mutableListOf<Deferred<Boolean>>()
        for (i in 0 until 3) {
            awaitList.add(GlobalScope.async {
                LogUtils.printLog("current thread: ${Thread.currentThread().name}")
                for (defectMap in inputChannel) {
                    val fileProcessResult = defectSubProcessors.fold(
                            FileProcessResult(
                                    filePath = defectMap,
                                    commandParam = commandParam
                            )) { acc, abstractDefectSubProcessor ->
                        abstractDefectSubProcessor.mainDefectSubProcess(acc)
                    }
                    outputChannel.send(
                            Pair(defectMap, fileProcessResult)
                    )
                }
                true
            })
        }
        return awaitList
    }


    //消费处理输出内容(当前包括告警信息和md5信息)
    private fun collectLintOutDefect(commonOutputChannel: Channel<Pair<String, FileProcessResult>>,
                                     commandParam: CommandParam,
                                     streamName: String,
                                     toolName: String) {
        LogUtils.printLog("collect and process output defect")
        val md5OutputFileName = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "md5_files"
        val md5OutputFile = File(md5OutputFileName)
        val filePathListFileName = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "file_path_list.json"
        val filePathListFile = File(filePathListFileName)
        val fileDefects = mutableListOf<FileDefects>()
        val md5FilesJson = mutableMapOf<String, Any?>()
        val md5FilesJsonmd5FileList = mutableListOf<MutableMap<String, String>>()
        val filePathList = mutableListOf<String>()
        //将后续文件格式的拼接逻辑放在这里
        runBlocking {
            for (outputDefectList in commonOutputChannel) {
                // 添加md5内容
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

        LogUtils.printLog("process defect output, defect file size: ${fileDefects.size}")
        //处理md5信息
        md5FilesJson["files_list"] = md5FilesJsonmd5FileList
        md5OutputFile.writeText(JsonUtil.toJson(md5FilesJson))
        if (md5OutputFile.exists()) {
            LogUtils.printLog("append md5 success upload outputFile...")
            CodeccWeb.upload(landunParam = commandParam.landunParam,
                    filePath = md5OutputFileName,
                    resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_md5.json",
                    uploadType = "SCM_JSON",
                    toolName = toolName)
        }
        //处理路径信息
        filePathListFile.writeText(JsonUtil.toJson(filePathList))
    }
}
