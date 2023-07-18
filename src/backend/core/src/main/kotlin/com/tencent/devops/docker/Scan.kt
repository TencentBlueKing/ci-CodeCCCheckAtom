package com.tencent.devops.docker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.DefectsEntity
import com.tencent.devops.docker.pojo.FileDefects
import com.tencent.devops.docker.pojo.Gather
import com.tencent.devops.docker.pojo.ScanType
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.pojo.ToolOptions
import com.tencent.devops.docker.scan.ToolOutputItem
import com.tencent.devops.docker.scm.pojo.ScmDiffItem
import com.tencent.devops.docker.tools.FileUtil
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccConfig
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.LinuxCodeccConstants
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.scan.LARGE_REPORT_HEAD_HTML
import com.tencent.devops.pojo.scan.LARGE_REPORT_TAIL_HTML
import com.tencent.devops.pojo.scan.ScanRepo
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.CodeccParamsHelper
import com.tencent.devops.utils.I18NUtils
import com.tencent.devops.utils.ToolUtils
import com.tencent.devops.utils.script.ScriptUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Scan(
    private val commandParam: CommandParam,
    private val toolName: String,
    private val streamName: String,
    private val analyzeConfigInfo: AnalyzeConfigInfo,
    private val incrementFiles: List<String>,
    private val diffFileList: MutableList<ScmDiffItem.DiffFileItem>
) {

    // 单工具mainScan
    @ExperimentalUnsignedTypes
    fun scan(): Boolean {
        // 生成inputFile,outputFile
        val inputFile = getToolDataPath() + File.separator + "tool_scan_input.json"
        val ignoreFile = getToolDataPath() + File.separator + "tool_scan_ignore.json"
        val ocHeadFile = getToolDataPath() + File.separator + "tool_scan_headfile.json"
        val outputFile = getToolDataPath() + File.separator + "tool_scan_output.json"
        try {
            // 生成toolScanInput
            if (!generateToolScanInput(inputFile)) {
                LogUtils.printLog("There are not open checkers to scan!")
                File(inputFile).writeText("{\"defects\": []")
                return true
            }
            LogUtils.printLog("Generate tool scan input file success")
            LogUtils.printLog("toolName: $toolName")
            LogUtils.printLog("compile_tools: ${ToolConstants.COMPILE_TOOLS}")
            if (toolName in ToolConstants.COMPILE_TOOLS) {
                val toolFolder = commandParam.projectBuildPath + File.separator + ".temp" + File.separator + "codecc_scan" +
                        File.separator + "codecc_agent" + File.separator + "bin" + File.separator + toolName
                val command = CodeccConfig.getConfig("${toolName.toUpperCase()}_SCAN_COMMAND")!!
                        .replace("##", " ")
                        .replace("{input.json}", inputFile)
                        .replace("{output.json}", outputFile)
                LogUtils.printLog("command: $command")
                if (File(toolFolder).exists()) {
                    LogUtils.printLog("enter tool folder: $toolFolder")
                    LogUtils.printLog("tool scan command: $command")
                    val constants = LinuxCodeccConstants(commandParam.projectBuildPath)
                    val python3Path = CodeccParamsHelper.getPython3Path(constants)
                    ScriptUtils.executeCodecc(script = command, dir = File(toolFolder), prefix = toolName, exportEnv = mapOf("PATH" to python3Path), printScript = true)
                } else {
                    LogUtils.printLog("$toolName bin dir not exists")
                }
            } else {
                val image = CodeccConfig.getImage(toolName)
                val realCmdList = mutableListOf<String>()
                val dockerInputFile = CommonUtils.changePathToDocker(inputFile)
                val dockerOutputFile = CommonUtils.changePathToDocker(outputFile)
                image.command.forEach {
                    realCmdList.add(it.replace("{input.json}", dockerInputFile).replace("{output.json}", dockerOutputFile))
                }
                image.command = realCmdList
                LogUtils.printLog(image)
                DockerRun.runImage(image, commandParam, toolName)
            }

            if (toolName == ToolConstants.RIPS && File(outputFile).exists()) {
                skipOutputTemp(commandParam, streamName, toolName)
            }

            // diff 模式过滤掉非本次变更的文件内容
            if ((analyzeConfigInfo.scanType == ScanType.DIFF
                            || analyzeConfigInfo.scanType == ScanType.DIFF_FILE
                            || analyzeConfigInfo.scanType == ScanType.DIFF_BRANCH)
                && toolName !in ToolConstants.NO_DIFF_CODE_TOOLS) {
                LogUtils.printLog("diff tool scan output file ...")
                diffToolScanOutput(commandParam, streamName, toolName, analyzeConfigInfo.scanType)
                LogUtils.printLog("diff tool scan output file success...")
            }
        } catch (e: Throwable) {
            LogUtils.printErrorLog("scan exception: $e ${ExceptionUtils.getStackTrace(e)}")
            return false
        }

        if (toolName == ToolConstants.SCC) {
            LogUtils.printLog("scc tool name to read ignore defect info, ignore file: $ignoreFile")
            IgnoreDefectParser.getIgnoreDefectInfo(ignoreFile, analyzeConfigInfo.taskId, commandParam, streamName)
            OCHeaderFileParser.getOcHeadFileInfo(ocHeadFile, analyzeConfigInfo.taskId, commandParam, streamName)
        }
        val newDefectProcessor = commandParam.extraPrams["newDefectProcessor"] ?: "true"
        // hashOutPut
        return if (File(outputFile).exists()) {
            LogUtils.printLog("scan success, pp hash output file")
            if (toolName != "githubstatistic") {
                if(newDefectProcessor.toBoolean()) {
                    processToolOutputDefects()
                } else {
                    if (toolName !in ToolConstants.CODE_TOOLS_ACOUNT) {
                        hashOutput()
                    }
                }
            }
            LogUtils.printLog("pp hash output file success, update language")
            updateLanguage()
            LogUtils.printLog("updateLanguage success, gather the defects")
            LogUtils.printLog("gather the defects success, upload outputFile")
            if (toolName !in ToolConstants.NOLINT_TOOLS) {
                val newOutputFile = if(newDefectProcessor.toBoolean()) {
                    getToolDataPath() + File.separator + "tool_scan_output_file.json"
                } else {
                    outputFormatUpdate()
                }
                LogUtils.printLog("gather the defects success, upload new outputFile")
                CodeccWeb.upload(landunParam = commandParam.landunParam,
                    filePath = newOutputFile,
                    resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_file.json",
                    uploadType = "SCM_JSON",
                    toolName = toolName)
            } else {
                LogUtils.printLog("gather the no lint defects success, upload new outputFile")
                CodeccWeb.upload(
                    landunParam = commandParam.landunParam,
                    filePath = outputFile,
                    resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_file.json",
                    uploadType = "SCM_JSON",
                    toolName = toolName)
            }
            true
        } else {
            LogUtils.printLog("outputFile not exists, upload json: $outputFile")
            CodeccWeb.upload(
                landunParam = commandParam.landunParam,
                filePath = inputFile,
                resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_input.json",
                uploadType = "SCM_JSON",
                toolName = toolName)
            if (ToolConstants.COVERITY == toolName || ToolConstants.KLOCWORK == toolName) {
                val zipResultFile = commandParam.dataRootPath + File.separator + streamName + "_" + toolName.toUpperCase() + "_result.zip"
                LogUtils.printLog("zipResultFile is $zipResultFile")
                if (File(zipResultFile).exists()) {
                    LogUtils.printLog("zipResultFile exists, upload it: $zipResultFile")
                    CodeccWeb.upload(
                        landunParam = commandParam.landunParam,
                        filePath = zipResultFile,
                        resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_failed_result.zip",
                        uploadType = "FAIL_RESULT",
                        toolName = toolName)
                }
            }
            false
        }
    }

    private fun skipOutputTemp(commandParam: CommandParam, streamName: String, toolName: String) {
        val outputFile = File(ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "tool_scan_output.json")
        if (!outputFile.exists()) return
        val outputFileList = JsonUtil.getObjectMapper().readValue<ToolOutputItem>(outputFile.readText()).defects
        val newOutputFileList = mutableListOf<DefectsEntity>()

        outputFileList.forEach {outputEntity ->
            if (!outputEntity.filePath?.contains(".temp")!!) {
//                LogUtils.printLog("test: "+outputEntity.filePath)
                newOutputFileList.add(outputEntity)
            }
        }
        // 重新填充defects字段
        val newOutputFileMap = JsonUtil.getObjectMapper().readValue<Map<String, Any>>(outputFile.readText()).toMutableMap()
        newOutputFileMap["defects"] = newOutputFileList
        outputFile.writeText(JsonUtil.getObjectMapper().writeValueAsString(newOutputFileMap))
    }

    private fun diffToolScanOutput(
        commandParam: CommandParam, streamName: String, toolName: String, scanType: ScanType) {
        val outputFile = File(ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "tool_scan_output.json")
        val diffOutputFile = File(ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + "git_branch_diff_output.json")
        if (!outputFile.exists() || !diffOutputFile.exists()) return

        // 获取diff文件的map
        val diffFileMap = mutableMapOf<String /* file name */, MutableSet<Long> /* file line */>()
        JsonUtil.getObjectMapper().readValue<Map<String, List<ScmDiffItem>>>(diffOutputFile.readText())["scm_increment"]?.forEach { scmDiffItem ->
            scmDiffItem.diffFileList.map { diffFile ->
                val lineSet = diffFileMap[File(diffFile.filePath).canonicalPath] ?: mutableSetOf()
                lineSet.addAll(diffFile.diffLineList)
                diffFileMap[File(diffFile.filePath).canonicalPath] = lineSet
            }
        }
        val outputFileList = JsonUtil.getObjectMapper().readValue<ToolOutputItem>(outputFile.readText()).defects

        // 过滤没用的文件
        val filterOutputFileList = if (scanType == ScanType.DIFF) {
            if (toolName.equals(ToolConstants.CCN, true)) {
                outputFileList.filter { outputEntity ->
                    val startLine = outputEntity.startLine!!.toInt()
                    val endLine = outputEntity.endLine!!.toInt()
                    val diffFileLines =
                        diffFileMap[File(outputEntity.filePath!!).canonicalPath] ?: mutableSetOf()
                    diffFileLines.forEach {
                        if (it in startLine..endLine) return@filter true
                    }
                    return@filter false
                }
            } else {
                outputFileList.filter { outputEntity ->
                    val diffFileLines = diffFileMap[File(outputEntity.filePath!!).canonicalPath] ?: mutableSetOf()
                    LogUtils.printDebugLog(outputEntity.filePath + " " + outputEntity.line + " " + diffFileLines + " " + diffFileLines.contains(outputEntity.line!!.toLong()))
                    diffFileLines.contains(outputEntity.line!!.toLong())
                }
            }
        } else {
            outputFileList.filter { outputEntity ->
                diffFileMap[File(outputEntity.filePath!!).canonicalPath] != null
            }
        }


        val noDiffOutputFile = File(outputFile.parent, "tool_scan_output_no_diff.json")
        noDiffOutputFile.writeText(outputFile.readText())
        LogUtils.printLog("generate no diff output file: ${noDiffOutputFile.canonicalPath}")

        // 重新填充defects字段
        val newOutputFileMap = JsonUtil.getObjectMapper().readValue<Map<String, Any>>(outputFile.readText()).toMutableMap()
        newOutputFileMap["defects"] = filterOutputFileList
        outputFile.writeText(JsonUtil.getObjectMapper().writeValueAsString(newOutputFileMap))
    }

    private fun outputFormatUpdate(): String {
        val newOutputFile = getToolDataPath() + File.separator + "tool_scan_output_file.json"
        doNormalOutputFormatUpdate(newOutputFile)
//        if (analyzeConfigInfo.scanType == ScanType.DIFF) {
//            doDiffOutputFormatUpdate(newOutputFile)
//        } else {
//            doNormalOutputFormatUpdate(newOutputFile)
//        }
        LogUtils.printLog("new output file: $newOutputFile")
        return newOutputFile
    }

    private fun doDiffOutputFormatUpdate(newOutputFile: String) {
        try {
            val outputFile = File(getToolDataPath() + File.separator + "tool_scan_output.json")
            val incrementFilesSet = incrementFiles.toSet()
            val diffFileListMap = diffFileList.map { it.filePath to it }.toMap()

            if (outputFile.exists()) {
                val outputData = JsonUtil.to(outputFile.readText(), object : TypeReference<Map<String, Any>>() {})
                val defects = outputData["defects"]
                val filesMap = mutableMapOf<String, MutableList<DefectsEntity>>()
                if (defects is List<*>) {
                    defects.forEach {
                        val defectStr = jacksonObjectMapper().writeValueAsString(it)
                        val defect = JsonUtil.to(defectStr, object : TypeReference<DefectsEntity>() {})
                        val filePath = defect.filePath ?: (defect.file_path ?: (defect.filePathname ?: (defect.filename
                            ?: "")))

                        // 文件和行号都对应上才继续
                        if (!incrementFilesSet.contains(filePath)) return@forEach
                        if (diffFileListMap[filePath]?.diffLineList?.contains(defect.line?.toLong()) != true) return@forEach

                        if (null != filesMap[filePath]) {
                            val fileDefectsList = filesMap[filePath] as MutableList<DefectsEntity>
                            fileDefectsList.add(DefectsEntity(
                                checkerName = defect.checkerName,
                                description = defect.description,
                                line = defect.line,
                                pinpointHash = defect.pinpointHash,
                                language =  defect.language,
                                langValue = defect.langValue))
                        } else {
                            filesMap[filePath] = mutableListOf(DefectsEntity(
                                checkerName = defect.checkerName,
                                description = defect.description,
                                line = defect.line,
                                pinpointHash = defect.pinpointHash,
                                language =  defect.language,
                                langValue = defect.langValue))
                        }
                    }
                }
                val fileDefects = filesMap.map { FileDefects(it.key, it.value, null) }
                val newOutputFileText = jacksonObjectMapper().writeValueAsString(fileDefects)
                File(newOutputFile).writeText(newOutputFileText)
                LogUtils.printLog("Output format update result : $newOutputFileText")
            } else {
                LogUtils.printLog("Output format update skip, for no ${outputFile.canonicalPath} not exist")
            }
        } catch (e: Throwable) {
            LogUtils.printLog("Format defects exception: ${e.message}")
        }
    }

    private fun doNormalOutputFormatUpdate(newOutputFile: String) {
        try {
            val outputFile = File(getToolDataPath() + File.separator + "tool_scan_output.json")
            if (outputFile.exists()) {
                val outputData = JsonUtil.to(outputFile.readText(), object : TypeReference<Map<String, Any>>() {})
                val defects = outputData["defects"]
                val filesMap = mutableMapOf<String, MutableList<DefectsEntity>>()
                if (defects is List<*>) {
                    defects.forEachIndexed { _, it ->
                        val defectStr = jacksonObjectMapper().writeValueAsString(it)
                        val defect = JsonUtil.to(defectStr, object : TypeReference<DefectsEntity>() {})
                        var filePath = defect.filePath ?: (defect.file_path ?: (defect.filePathname ?: (defect.filename
                            ?: "")))
                        when (CodeccEnvHelper.getOS()) {
                            OSType.WINDOWS -> {
                                filePath = CommonUtils.changePathToWindows(filePath)
                            }
                        }
                        if (null != filesMap[filePath]) {
                            val fileDefectsList = filesMap[filePath] as MutableList<DefectsEntity>
                            fileDefectsList.add(DefectsEntity(
                                checkerName = defect.checkerName,
                                description = defect.description,
                                line = defect.line,
                                pinpointHash = defect.pinpointHash,
                                language =  defect.language,
                                langValue = defect.langValue))
                        } else {
                            filesMap[filePath] = mutableListOf(DefectsEntity(
                                checkerName = defect.checkerName,
                                description = defect.description,
                                line = defect.line,
                                pinpointHash = defect.pinpointHash,
                                language =  defect.language,
                                langValue = defect.langValue))
                        }
                    }
                }
                val fileDefects = mutableListOf<FileDefects>()
                val gatherDefects = mutableListOf<FileDefects>()
                var needGather = false
                filesMap.forEach { (key, value) ->
                    if (value.size >= commandParam.gatherDefectThreshold) {
                        fileDefects.add(FileDefects(key, null, Gather(value.size.toLong(), null)))
                        gatherDefects.add(FileDefects(key, value, null))
                        needGather = true
                    } else {
                        fileDefects.add(FileDefects(key, value, null))
                    }
                }
                if (needGather && gatherDefects.isNotEmpty()) {
                    val fileName = streamName + "_" + toolName + "_" + commandParam.landunParam.buildId + "_codecc_defect_detail.zip"
                    fileDefects.add(FileDefects(fileName, null, Gather(null, true)))
                    try {
                        LogUtils.printLog("generate gather detail report...")
                        val filePath = generateLargeReport(commandParam, gatherDefects)
                        LogUtils.printLog("generate gather detail report success, filePath: $filePath")
                        val zipFile = FileUtil.zipFile(filePath)
                        LogUtils.printLog("zip gather detail report success, zipFilePath: $zipFile")
                        CodeccWeb.upload(landunParam = commandParam.landunParam,
                            filePath = zipFile,
                            resultName = fileName,
                            uploadType = "GATHER",
                            toolName = toolName)
                        LogUtils.printLog("upload gather detail report success, resultName: $fileName")
                    } catch (e: Throwable) {
                        LogUtils.printLog("Upload defect detail exception: ${e.message}")
                    }
                }
                val newOutputFileText = jacksonObjectMapper().writeValueAsString(fileDefects)
                File(newOutputFile).bufferedWriter().use { out -> out.write(newOutputFileText) }
            }
        } catch (e: Throwable) {
            LogUtils.printLog("Format defects exception: ${e.message}")
        }
    }

    private fun updateLanguage() {
        val param = LocalParam.param.get()
        if (toolName == "scc" && param?.openScanPrj == true) {
            LogUtils.printLog("Tool is code tool acount update language.")
            val languageSet = ToolUtils.getClocLangSet(toolName, streamName, commandParam.dataRootPath).second
            LogUtils.printLog("languageSet size: ${languageSet.size}")
            if (languageSet.isNotEmpty()) {
                param.languages = jacksonObjectMapper().writeValueAsString(languageSet.toList())
                LogUtils.printLog("update language to ${param.languages}")
                CodeccSdkApi.updateTask(param, param.openScanPrj)
            }
        }
    }

    private fun hashOutput() {
        val inputFile = getToolDataPath() + File.separator + "tool_scan_output.json"
        val outputFile = getToolDataPath() + File.separator + "tool_scan_output_hash.json"

        // docker run
        val image = CodeccConfig.getImage("pphash")
        val dockerInputFile = CommonUtils.changePathToDocker(inputFile)
        val dockerOutputFile = CommonUtils.changePathToDocker(outputFile)
        val realCmdList = mutableListOf<String>()
        image.command.forEach {
            realCmdList.add(it.replace("{input.json}", dockerInputFile).replace("{output.json}", dockerOutputFile))
        }
        image.command = realCmdList
//        DockerRun.runCmd(image, commandParam.streamCodePath)
        DockerRun.runImage(image, commandParam, toolName)

        if (File(outputFile).exists()) {
            LogUtils.printLog("copy outputFile to inputFile")
            val outFile = File(outputFile)
            val inFile = File(inputFile)
            LogUtils.printLog("start to copy...")
            LogUtils.printLog(inFile.canonicalPath)
            LogUtils.printLog(outFile.canonicalPath)
            outFile.copyTo(inFile, true)

            LogUtils.printLog("copy success")
        }
    }


    @ExperimentalUnsignedTypes
    private fun processToolOutputDefects() {
        val inputFile = getToolDataPath() + File.separator + "tool_scan_output.json"
        when (toolName) {
            ToolConstants.CCN -> {
                CCNDefectProcessor.generateCCNListDefect(
                    inputFileName = inputFile,
                    commandParam = commandParam,
                    toolName = toolName,
                    streamName = streamName
                )
//            HashGenerateProcess.hashCCNMethod(inputFile, outputFile)
            }
            !in ToolConstants.NOLINT_TOOLS -> {
                LintDefectProcessor.generateLintDefectList(
                    inputFileName = inputFile,
                    commandParam = commandParam,
                    toolName = toolName,
                    streamName = streamName
                )
//            HashGenerateProcess.hashMethod(5, inputFile, outputFile)
            }
            else -> {
                CommonDefectProcessor.generateLintDefectList(
                    inputFileName = inputFile,
                    commandParam = commandParam,
                    toolName = toolName,
                    streamName = streamName
                )
            }
        }
    }

    private fun traverseWhitePath(whitePath: String): MutableSet<String> {
        var whitePathTemp = if (whitePath.endsWith("/.*")){
            whitePath.dropLast(3)
        } else if (whitePath.endsWith("/")){
            whitePath.dropLast(1)
        }else{
            whitePath
        }

        whitePathTemp = if(!whitePathTemp.startsWith(".*")){
            ".*$whitePathTemp"
        }else{
            whitePathTemp
        }

        val re = Regex(whitePathTemp)
        val whitePathList = mutableSetOf<String>()
        val fileDirList: MutableList<String> = mutableListOf()
        val fileTree: FileTreeWalk = File(commandParam.landunParam.streamCodePath).walk()
        fileTree.filter { it.isDirectory || it.isFile }.forEach {
            if (! it.canonicalPath.contains(".temp")) {
                fileDirList.add(it.canonicalPath)
            }
        }

        fileDirList.forEach{ dir ->
            if (re.matches(dir)){
                //如果是已添加路径的子路径，则无需重复添加
                var is_chird_dir = false
                whitePathList.forEach { it ->
                    if (dir.contains(it+"/")){
                        is_chird_dir = true
                        return@forEach
                    }
                }
                if (!is_chird_dir){
                    whitePathList.add(dir)
                }
            }
        }
        return whitePathList
    }

    private fun generateToolScanInput(inputFile: String): Boolean {
        val inputData = mutableMapOf<String, Any?>()
        val whitePathList = mutableListOf<String>()
        commandParam.landunParam.toolImageTypes?.split(",")?.forEach {
            val toolImageType = it.split(":")
            if (toolImageType[0].equals(toolName.toUpperCase())){
                inputData["toolImageType"] = toolImageType[1]
            }
        }
        inputData["projName"] = streamName
        inputData["projectId"] = commandParam.landunParam.devopsProjectId
        if (toolName in ToolConstants.COMPILE_TOOLS) {
            inputData["scanPath"] = commandParam.landunParam.streamCodePath
        } else {
            inputData["scanPath"] = CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath)
        }

        LogUtils.printDebugLog("set input data language param: ${analyzeConfigInfo.language}")
        if (analyzeConfigInfo.language != null) {
            var inputLanguage = analyzeConfigInfo.language
            //对于开源扫描的要去除编译型语言
            if((commandParam.openScanPrj == true || System.getProperty("checkerType") == "openScan")
                    && toolName == ToolConstants.COVERITY){
                //添加过滤条件，对于oteam项目，并且配置了ci.yml的，需要放开编译型语言
                if(!(null != System.getenv("OTeam") && System.getenv("OTeam") == "true")){
                    LogUtils.printStr("non-oteam-ci opensource project need to remove compiled language")
                    //C#
                    if((inputLanguage and 1L) > 0){
                        inputLanguage -= 1L
                    }
                    //C++
                    if((inputLanguage and 2L) > 0){
                        inputLanguage -= 2L
                    }
                    //JAVA
                    if((inputLanguage and 4L) > 0){
                        inputLanguage -= 4L
                    }
                    //OC
                    if((inputLanguage and 16L) > 0){
                        inputLanguage -= 16L
                    }
                    //GOLANG
                    if((inputLanguage and 512L) > 0){
                        inputLanguage -= 512L
                    }
                    //KOTLIN
                    if((inputLanguage and 4096L) > 0){
                        inputLanguage -= 4096L
                    }
                    LogUtils.printLog("filtered input language for open source: $inputLanguage")
                }
            }
            inputData["language"] = inputLanguage
        }

        LogUtils.printDebugLog("set input data sub code path list param: ${commandParam.subCodePathList}")
        commandParam.subCodePathList.forEach { whitePath ->
            if (StringUtils.isBlank(whitePath)) {
                return@forEach
            }
            if (File(whitePath).exists()) {
                whitePathList.add(whitePath.replace("//", "/"))
            } else {
                if (whitePath.contains(',')) {
                    whitePath.split(',').forEach { subPath ->
                        if (File(subPath).exists()) {
                            whitePathList.add(subPath.replace("//", "/"))
                        } else {
                            val subWhitePathList = traverseWhitePath(subPath)
                            whitePathList?.let { list1 -> subWhitePathList?.let(list1::addAll) }
                        }
                    }
                } else {
                    if (File(inputData["scanPath"].toString() + whitePath).exists()){
                        whitePathList.add(inputData["scanPath"].toString() + whitePath)
                    }else{
                        val subWhitePathList = traverseWhitePath(whitePath)
                        whitePathList?.let { list1 -> subWhitePathList?.let(list1::addAll) }
                    }
                }
            }
        }

        if (whitePathList.size == 0 && commandParam.repos.filterNot { it.relPath.isBlank() }.isNotEmpty() && !ToolConstants.COMPILE_TOOLS.contains(toolName)) {
            commandParam.repos.forEach { repo ->
                val relPath = when {
                    repo.relPath.startsWith("./") -> {
                        repo.relPath.substring(2)
                    }
                    repo.relPath.startsWith("/") -> {
                        repo.relPath.substring(1)
                    }
                    else -> {
                        repo.relPath
                    }
                }
                val codePath = CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath + File.separator + relPath)
                var isAddToWhite = true
                whitePathList.forEach { path ->
                    if (path.contains(codePath)) {
                        isAddToWhite = false;
                        return@forEach
                    }
                }
                if (isAddToWhite) {
                    whitePathList.add(codePath)
                }
            }
        }

        LogUtils.printDebugLog("set input data compile tool param: $toolName")
        val whitePathListTmp = mutableListOf<String>()
        if (toolName in ToolConstants.COMPILE_TOOLS) {
            whitePathListTmp.addAll(whitePathList)
        } else {
            whitePathList.forEach {
                whitePathListTmp.add(CommonUtils.changePathToDocker(it))
            }
        }
        LogUtils.printLog("onlyScanWhitePath: "+analyzeConfigInfo.onlyScanWhitePath)
        if(analyzeConfigInfo.onlyScanWhitePath == false){
            inputData["whitePathList"] = mutableListOf<String>()
        }else{
            inputData["whitePathList"] = whitePathListTmp
        }
        if (null != analyzeConfigInfo.toolOptions) {
            inputData["toolOptions"] = analyzeConfigInfo.toolOptions
            analyzeConfigInfo.toolOptions.forEach {
                if (it.optionName == "SHELL") {
                    val shellFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + commandParam.landunParam.buildId + ".sh"
                    val shellFileContent = "cd ${commandParam.landunParam.streamCodePath} \n${it.optionValue}"
                    LogUtils.printLog("shell file content is $shellFileContent")
                    File(shellFile).writeText(shellFileContent)
                    inputData["buildScript"] = shellFile
                    ScriptUtils.executeCodecc("chmod 755 $shellFile", File(commandParam.landunParam.streamCodePath))
                }
                if (it.optionName == "BAT") {
                    val shellFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + commandParam.landunParam.buildId + ".bat"
                    val shellFileContent = "cd ${commandParam.landunParam.streamCodePath} \n${it.optionValue}"
                    LogUtils.printLog("shell file content is $shellFileContent")
                    File(shellFile).writeText(shellFileContent)
                    inputData["buildScript"] = shellFile
                }
            }
        }
        if (toolName == ToolConstants.GITHUBSTATISTIC) {
            inputData["lastExecuteTime"] = analyzeConfigInfo.lastExecuteTime ?: 0
        }
        if (toolName == ToolConstants.CODEQL) {
            val option = ToolOptions(
                optionName = "subPath",
                optionValue = commandParam.codeqlHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            LogUtils.printLog("append codeql success")
        }
        if (toolName == ToolConstants.CLANG) {
            val option = ToolOptions(
                optionName = "subPath",
                optionValue = commandParam.clangHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            LogUtils.printLog("append clang success")
        }
        if (toolName == ToolConstants.SPOTBUGS) {
            val option = ToolOptions(
                    optionName = "subPath",
                    optionValue = commandParam.spotBugsHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            LogUtils.printLog("append spotbugs success")
        }
        if (toolName == ToolConstants.PINPOINT) {
            // pinpoint仅支持linux，公共机直接有包，第三方机需要挂载，所以不用下载
            val option = ToolOptions(
                optionName = "subPath",
                optionValue = commandParam.pinpointHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            LogUtils.printLog("append pinpoint success")
        }
        if (toolName == ToolConstants.COVERITY) {
            val option = ToolOptions(
                optionName = "subPath",
                optionValue = commandParam.coverityHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            val toolConfigPlatform = CodeccWeb.getSpecConfig(commandParam.landunParam, analyzeConfigInfo.taskId, toolName.toUpperCase())
            if (!toolConfigPlatform.specConfig.isNullOrBlank()) {
                inputData["specConfig"] = toolConfigPlatform.specConfig!!
            }
        }
        if (toolName == ToolConstants.PVS) {
            val option = ToolOptions(
                optionName = "subPath",
                optionValue = commandParam.pvsHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
        }
        if (toolName == ToolConstants.KLOCWORK) {
            val option = ToolOptions(
                optionName = "subPath",
                optionValue = commandParam.klockWorkHomeBin
            )
            inputData["toolOptions"] = if (analyzeConfigInfo.toolOptions == null) {
                listOf(option)
            } else {
                analyzeConfigInfo.toolOptions.plus(option)
            }
            val toolConfigPlatform = CodeccWeb.getSpecConfig(commandParam.landunParam, analyzeConfigInfo.taskId, toolName.toUpperCase())
            if (!toolConfigPlatform.specConfig.isNullOrBlank()) {
                inputData["specConfig"] = toolConfigPlatform.specConfig!!
            }
        }

        inputData["scanType"] = if (ScanType.FULL.name == analyzeConfigInfo.scanType.name.toUpperCase()) {
            ScanType.FULL.name.toLowerCase()
        } else {
            ScanType.INCREMENT.name.toLowerCase()
        }
        var skipPathList = mutableSetOf<String>()
        if (analyzeConfigInfo.skipPaths != null) {
            val skipList = mutableSetOf<String>()
            analyzeConfigInfo.skipPaths!!.split(";").toList().forEach { subskipPath ->
                val subskipList = subskipPath!!.split(",").toList()
                skipList?.let { list1 -> subskipList?.let(list1::addAll) }
            }
            skipPathList.addAll(skipList.filter { it.replace("\\.", ".").replace("+", "\\+") != "" })
        }
//        /*
//         * 兼容以下两种过滤方式：
//         * 'src/test/.*'
//         * '/src/test/.*'
//         * 以上两种过滤路径，会从项目根路径开始进行匹配。
//         */
//        val skipPathForProjectRoot = mutableSetOf<String>()
//        val refisrt = Regex("^[a-zA-Z]|[0-9]")
//        skipPathList.forEach { subSkipPath ->
//            var skipPath = subSkipPath
//            val firstLetter = subSkipPath?.firstOrNull()
//            if (refisrt.matches(firstLetter.toString())){
//                //匹配到第一种情况
//                skipPath = inputData["scanPath"].toString() + "/" + subSkipPath
//            }else if (subSkipPath?.startsWith("/")){
//                val subProjectPath = File(inputData["scanPath"].toString() + subSkipPath.subSequence(0,subSkipPath.lastIndexOf("/")))
//                if (subProjectPath.exists()){
//                    //匹配到第二种情况
//                    skipPath = inputData["scanPath"].toString() + subSkipPath
//                }
//            }
//            skipPathForProjectRoot.add(skipPath)
//        }
//        skipPathList = skipPathForProjectRoot

//        try {
//            // 支持解析.code.yml读取过滤路径
//            val codeYamlFileList = mutableListOf<String>()
//            if (commandParam.repoRelPathMap.filterNot { it.key.isBlank() }.isNotEmpty()) {
//                commandParam.repoRelPathMap.forEach { repoRelPath ->
//                    val codePath = CommonUtils.changePathToDocker(File(commandParam.landunParam.streamCodePath + File.separator + repoRelPath.value).canonicalPath)
//                    val codeymlFile = File(codePath+ File.separator+ ".code.yml")
//                    if (codeymlFile.exists()) {
//                        codeYamlFileList.add(codeymlFile.canonicalPath)
//                    }
//                }
//            }else{
//                val codeymlFile2 = File(inputData["scanPath"].toString() + File.separator + ".code.yml")
//                if (codeymlFile2.exists()) {
//                    codeYamlFileList.add(codeymlFile2.canonicalPath)
//                }
//            }
//            codeYamlFileList.forEach { codeyml ->
//                val codeYaml = YAMLParse.parseDto(codeyml, CodeYaml::class)
//                if (codeYaml?.source?.auto_generate_source?.filepath_regex != null) {
//                    skipPathList.addAll(codeYaml?.source?.auto_generate_source?.filepath_regex!!.toList())
//                }
//                if (codeYaml?.source?.test_source?.filepath_regex != null) {
//                    skipPathList.addAll(codeYaml?.source?.test_source?.filepath_regex!!.toList())
//                }
//                if (codeYaml?.source?.third_party_source?.filepath_regex != null) {
//                    skipPathList.addAll(codeYaml?.source?.third_party_source?.filepath_regex!!.toList())
//                }
//            }
//        } catch (e: Throwable) {
//            LogUtils.printLog("Format code yaml exception: ${e.message}")
//        }

        val skipDockerPathList = mutableListOf<String>()
        skipDockerPathList.addAll(skipPathList)
//        if (toolName in ToolConstants.COMPILE_TOOLS) {
//            skipDockerPathList.addAll(skipPathList)
//        } else {
//            skipPathList.forEach {
//                skipDockerPathList.add(CommonUtils.changePathToDocker(it))
//            }
//        }

        inputData["skipPaths"] = skipDockerPathList
        inputData["incrementalFiles"] = incrementFiles
        if (analyzeConfigInfo.openCheckers != null) {
            inputData["openCheckers"] = analyzeConfigInfo.openCheckers
        }

        // add repo info
        inputData["repos"] = commandParam.repos.map{
            ScanRepo(it.url, commandParam.scmType)
        }

        inputData["commitSince"] = analyzeConfigInfo.commitSince
        inputData["codeccWorkspacePath"] = commandParam.codeccWorkspacePath
        inputData["languageTag"] = I18NUtils.getLanguageTag()

        val inputFileText = jacksonObjectMapper().writeValueAsString(inputData)
        File(inputFile).writeText(inputFileText)

        return if (File(inputFile).exists()) {
            CodeccWeb.upload(
                landunParam = commandParam.landunParam,
                filePath = inputFile,
                resultName = streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_input.json",
                uploadType = "SCM_JSON",
                toolName = toolName)
            LogUtils.printLog("upload tool_scan_input.json success, return true")
            true
        } else {
            LogUtils.printLog("File:($inputFile) not exists. return false")
            false
        }
    }

    private fun getToolDataPath() = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName)

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
