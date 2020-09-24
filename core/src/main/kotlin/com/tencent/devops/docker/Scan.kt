package com.tencent.devops.docker

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.common.factory.SubProcessorFactory
import com.tencent.devops.docker.pojo.AnalyzeConfigInfo
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.DefectsEntity
import com.tencent.devops.docker.pojo.FileDefects
import com.tencent.devops.docker.pojo.Gather
import com.tencent.devops.docker.pojo.ScanType
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.scan.ToolOutputItem
import com.tencent.devops.docker.scm.pojo.ScmDiffItem
import com.tencent.devops.docker.tools.FileUtil
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CodeccConfig
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.LinuxCodeccConstants
import com.tencent.devops.utils.CodeccParamsHelper
import com.tencent.devops.utils.script.ScriptUtils
import org.apache.commons.lang.StringUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class Scan(
    private val commandParam: CommandParam,
    private val toolName: String,
    private val streamName: String,
    private val analyzeConfigInfo: AnalyzeConfigInfo,
    private val incrementFiles: List<String>,
    private val diffFileList: MutableList<ScmDiffItem.DiffFileItem>
) {

    // 单工具mainScan
    fun scan(): Boolean {
        // 生成inputFile,outputFile
        val inputFile = getToolDataPath() + File.separator + "tool_scan_input.json"
        val outputFile = getToolDataPath() + File.separator + "tool_scan_output.json"

        // 生成toolScanInput
//        LogUtils.printDebugLog("Generate tool scan input file: $inputFile")
        if (!generateToolScanInput(inputFile)) {
            LogUtils.printLog("There are not open checkers to scan!")
            File(inputFile).writeText("{\"defects\": []")
            return true
        }
        LogUtils.printDebugLog("Generate tool scan input file success")
        LogUtils.printDebugLog("toolName: $toolName")
        LogUtils.printDebugLog("compile_tools: ${ToolConstants.COMPILE_TOOLS}")
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
//            DockerRun.runCmd(image, commandParam.streamCodePath)
            LogUtils.printDebugLog(image)
            DockerRun.runImage(image, commandParam)
        }

        // diff 模式过滤掉非本次变更的文件内容
        if (analyzeConfigInfo.scanType == ScanType.DIFF && toolName != ToolConstants.CLOC) {
            LogUtils.printDebugLog("diff tool scan output file ...")
            diffToolScanOutput(commandParam, streamName, toolName)
            LogUtils.printDebugLog("diff tool scan output file success...")
        }

        // hashOutPut
        return if (File(outputFile).exists()) {
            LogUtils.printDebugLog("scan success, pp hash output file")
            hashOutput()
            LogUtils.printDebugLog("pp hash output file success, update language")
            updateLanguage()
            LogUtils.printDebugLog("updateLanguage success, gather the defects")
            LogUtils.printDebugLog("gather the defects success, upload outputFile")
//            CodeccWeb.upload(commandParam.landunParam, outputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan.json", "SCM_JSON")
            if (toolName !in ToolConstants.NOLINT_TOOLS) {
                val newOutputFile = outputFormatUpdate()
                LogUtils.printDebugLog("gather the defects success, upload new outputFile")
                CodeccWeb.upload(commandParam.landunParam, newOutputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_file.json", "SCM_JSON")
            } else {
                LogUtils.printDebugLog("gather the no lint defects success, upload new outputFile")
                CodeccWeb.upload(commandParam.landunParam, outputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_file.json", "SCM_JSON")
            }
            true
        } else {
            LogUtils.printDebugLog("outputFile not exists, upload json: $outputFile")
            CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_input.json", "SCM_JSON")
            if (ToolConstants.COVERITY == toolName || ToolConstants.KLOCWORK == toolName) {
                val zipResultFile = commandParam.dataRootPath + File.separator + streamName + "_" + toolName.toUpperCase() + "_result.zip"
                LogUtils.printDebugLog("zipResultFile is $zipResultFile")
                if (File(zipResultFile).exists()) {
                    LogUtils.printDebugLog("zipResultFile exists, upload it: $zipResultFile")
                    CodeccWeb.upload(commandParam.landunParam, zipResultFile, streamName + "_" + toolName.toUpperCase() + "_failed_result.zip", "FAIL_RESULT")
                }
            }
            false
        }
    }

    private fun diffToolScanOutput(commandParam: CommandParam, streamName: String, toolName: String) {
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
        val filterOutputFileList = if (toolName.equals(ToolConstants.CCN, true)) {
            outputFileList.filter { outputEntity ->
                val startLine = outputEntity.startLine!!.toInt()
                val endLine = outputEntity.endLine!!.toInt()
                val diffFileLines = diffFileMap[File(outputEntity.filePath!!).canonicalPath] ?: mutableSetOf()
                diffFileLines.forEach {
                    if (it in startLine..endLine) return@filter true
                }
                return@filter false
            }
        } else {
            outputFileList.filter { outputEntity ->
                val diffFileLines = diffFileMap[File(outputEntity.filePath!!).canonicalPath] ?: mutableSetOf()
                diffFileLines.contains(outputEntity.line!!.toLong())
            }
        }

        val noDiffOutputFile = File(outputFile.parent, "tool_scan_output_no_diff.json")
        noDiffOutputFile.writeText(outputFile.readText())
        LogUtils.printDebugLog("generate no diff output file: ${noDiffOutputFile.canonicalPath}")

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
        LogUtils.printDebugLog("new output file: $newOutputFile")
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
                                pinpointHash = defect.pinpointHash))
                        } else {
                            filesMap[filePath] = mutableListOf(defect)
                        }
                    }
                }
                val fileDefects = filesMap.map { FileDefects(it.key, it.value, null) }
                val newOutputFileText = jacksonObjectMapper().writeValueAsString(fileDefects)
                File(newOutputFile).writeText(newOutputFileText)
                LogUtils.printDebugLog("Output format update result : $newOutputFileText")
            } else {
                LogUtils.printDebugLog("Output format update skip, for no ${outputFile.canonicalPath} not exist")
            }
        } catch (e: Throwable) {
            LogUtils.printDebugLog("Format defects exception: ${e.message}")
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
                        val filePath = defect.filePath ?: (defect.file_path ?: (defect.filePathname ?: (defect.filename
                            ?: "")))

                        if (null != filesMap[filePath]) {
                            val fileDefectsList = filesMap[filePath] as MutableList<DefectsEntity>
                            fileDefectsList.add(DefectsEntity(
                                checkerName = defect.checkerName,
                                description = defect.description,
                                line = defect.line,
                                pinpointHash = defect.pinpointHash))
                        } else {
                            filesMap[filePath] = mutableListOf(DefectsEntity(
                                checkerName = defect.checkerName,
                                description = defect.description,
                                line = defect.line,
                                pinpointHash = defect.pinpointHash))
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
                        LogUtils.printDebugLog("generate gather detail report...")
                        val filePath = generateLargeReport(commandParam, gatherDefects)
                        LogUtils.printDebugLog("generate gather detail report success, filePath: $filePath")
                        val zipFile = FileUtil.zipFile(filePath)
                        LogUtils.printDebugLog("zip gather detail report success, zipFilePath: $zipFile")
                        CodeccWeb.upload(commandParam.landunParam, zipFile, fileName, "GATHER")
                        LogUtils.printDebugLog("upload gather detail report success, resultName: $fileName")
                    } catch (e: Throwable) {
                        LogUtils.printLog("Upload defect detail exception: ${e.message}")
                    }
                }
                val newOutputFileText = jacksonObjectMapper().writeValueAsString(fileDefects)
                File(newOutputFile).writeText(newOutputFileText)
            }
        } catch (e: Throwable) {
            LogUtils.printDebugLog("Format defects exception: ${e.message}")
        }
    }

    private fun updateLanguage() {
        val param = LocalParam.param.get()
        if (toolName.toUpperCase() == "CLOC" && param?.openScanPrj == true) {
            LogUtils.printDebugLog("Tool is cloc update language.")
            val outputFile = File(getToolDataPath() + File.separator + "tool_scan_output.json")
            if (outputFile.exists()) {
                LogUtils.printDebugLog("outputFile exist: $outputFile")
                val outputData = JsonUtil.to(outputFile.readText(), object : TypeReference<Map<String, Any>>() {})
                val defects = outputData["defects"]
                if (defects is List<*>) {
                    LogUtils.printDebugLog("defects is list, size: ${defects.size}")
                    val languageSet = mutableSetOf<String>()
                    defects.forEachIndexed { _, it ->
                        val defectStr = jacksonObjectMapper().writeValueAsString(it)
                        val defect = JsonUtil.to(defectStr, object : TypeReference<DefectsEntity>() {})
                        if (defect.language != null) {
                            languageSet.add(defect.language)
                        }
                    }
                    LogUtils.printDebugLog("languageSet size: ${languageSet.size}")
                    if (languageSet.isNotEmpty()) {
                        param.languages = jacksonObjectMapper().writeValueAsString(languageSet.toList())
                        LogUtils.printDebugLog("update language to ${param.languages}")
                        CodeccSdkApi.updateTask(param, param.openScanPrj)
                    }
                }
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
        DockerRun.runImage(image, commandParam)

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

    private fun traverseWhitePath(whitePath: String): MutableSet<String> {
        var whitePathTemp = if (whitePath.endsWith("/.*")){
            whitePath.dropLast(3)
        } else if (whitePath.endsWith("/")){
            whitePath.dropLast(1)
        } else if(!whitePath.startsWith(".*")){
            ".*$whitePath"
        }else{
            whitePath
        }

        val re = Regex(whitePathTemp)
        val whitePathList = mutableSetOf<String>()
        val fileDirList: MutableList<String> = mutableListOf()
        val fileTree: FileTreeWalk = File(commandParam.landunParam.streamCodePath).walk()
        fileTree.filter { it.isDirectory || it.isFile }.forEach { fileDirList.add(it.canonicalPath) }
        fileDirList.forEach{ dir ->
            if (re.matches(dir)){
                whitePathList.add(dir)
            }
        }
        return whitePathList
    }

    private fun generateToolScanInput(inputFile: String): Boolean {
        val inputData = mutableMapOf<String, Any?>()
        val whitePathList = mutableListOf<String>()
        val SubScan = SubProcessorFactory().createSubScanByToolName(toolName)

        inputData["projName"] = streamName
        if (toolName in ToolConstants.COMPILE_TOOLS) {
            inputData["scanPath"] = commandParam.landunParam.streamCodePath
        } else {
            inputData["scanPath"] = CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath)
        }

        if (analyzeConfigInfo.language != null) {
            var inputLanguage = analyzeConfigInfo.language
            //对于开源扫描的要去除编译型语言
            val subScan = SubProcessorFactory().createSubScan(commandParam.openScanPrj,toolName)
            inputLanguage = subScan.removeCompiledLanguages(inputLanguage)
            inputData["language"] = inputLanguage
        }
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
                    val subWhitePathList = traverseWhitePath(whitePath)
                    whitePathList?.let { list1 -> subWhitePathList?.let(list1::addAll) }
                }
            }
        }

        if (whitePathList.size == 0 && commandParam.repoRelPathMap.filterNot { it.key.isBlank() }.isNotEmpty() && !ToolConstants.COMPILE_TOOLS.contains(toolName)) {
            commandParam.repoRelPathMap.forEach { repoRelPath ->
                val codePath = CommonUtils.changePathToDocker(commandParam.landunParam.streamCodePath + File.separator + repoRelPath.value)
                whitePathList.add(codePath)
            }
        }

        val whitePathListTmp = mutableListOf<String>()
        if (toolName in ToolConstants.COMPILE_TOOLS) {
            whitePathListTmp.addAll(whitePathList)
        } else {
            whitePathList.forEach {
                whitePathListTmp.add(CommonUtils.changePathToDocker(it))
            }
        }
        inputData["whitePathList"] = whitePathListTmp
        if (null != analyzeConfigInfo.toolOptions) {
            inputData["toolOptions"] = analyzeConfigInfo.toolOptions
            analyzeConfigInfo.toolOptions.forEach {
                if (it.optionName == "SHELL") {
                    val shellFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + commandParam.landunParam.buildId + ".sh"
                    val shellFileContent = "cd ${commandParam.landunParam.streamCodePath} \n${it.optionValue}"
                    LogUtils.printDebugLog("shell file content is $shellFileContent")
                    File(shellFile).writeText(shellFileContent)
                    inputData["buildScript"] = shellFile
                    ScriptUtils.executeCodecc("chmod 755 $shellFile", File(commandParam.landunParam.streamCodePath))
                }
                if (it.optionName == "BAT") {
                    val shellFile = ScanComposer.generateToolDataPath(commandParam.dataRootPath, streamName, toolName) + File.separator + commandParam.landunParam.buildId + ".bat"
                    val shellFileContent = "cd ${commandParam.landunParam.streamCodePath} \n${it.optionValue}"
                    LogUtils.printDebugLog("shell file content is $shellFileContent")
                    File(shellFile).writeText(shellFileContent)
                    inputData["buildScript"] = shellFile
                }
            }
        }
        inputData["toolOptions"] = SubScan.toolOptionsPro(toolName,commandParam,analyzeConfigInfo,inputData)

        inputData["scanType"] = if (ScanType.FULL.name == analyzeConfigInfo.scanType.name.toUpperCase()) {
            ScanType.FULL.name.toLowerCase()
        } else {
            ScanType.INCREMENT.name.toLowerCase()
        }
        val skipPathList = mutableSetOf<String>()
        if (CodeccConfig.getConfig("SKIP_ITEMS") != null) {
            LogUtils.printDebugLog("SKIP_ITEMS value is: ${CodeccConfig.getConfig("SKIP_ITEMS")}")
            skipPathList.addAll(CodeccConfig.getConfig("SKIP_ITEMS")!!.split(";").toList())
        }
        if (analyzeConfigInfo.skipPaths != null) {
            val skipList = mutableSetOf<String>()
            analyzeConfigInfo.skipPaths!!.split(";").toList().forEach { subskipPath ->
                val subskipList = subskipPath!!.split(",").toList()
                skipList?.let { list1 -> subskipList?.let(list1::addAll) }
            }
            skipPathList.addAll(skipList.filter { it.replace("\\.", ".").replace("+", "\\+") != "" })
        }
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
//            LogUtils.printDebugLog("Format code yaml exception: ${e.message}")
//        }

        val skipDockerPathList = mutableListOf<String>()
        if (toolName in ToolConstants.COMPILE_TOOLS) {
            skipDockerPathList.addAll(skipPathList)
        } else {
            skipPathList.forEach {
                skipDockerPathList.add(CommonUtils.changePathToDocker(it))
            }
        }

        inputData["skipPaths"] = skipDockerPathList
        inputData["incrementalFiles"] = incrementFiles
        if (analyzeConfigInfo.openCheckers != null) {
            inputData["openCheckers"] = analyzeConfigInfo.openCheckers
        }

        val inputFileText = jacksonObjectMapper().writeValueAsString(inputData)
        File(inputFile).writeText(inputFileText)

        return if (File(inputFile).exists()) {
            CodeccWeb.upload(commandParam.landunParam, inputFile, streamName + "_" + toolName.toUpperCase() + "_" + commandParam.landunParam.buildId + "_tool_scan_input.json", "SCM_JSON")
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

        System.err.println("[${LocalParam.toolName.get()}] 共 $fileCount 个文件告警数超过阈值${commandParam.gatherDefectThreshold}，已经将这些文件共 $totalDefectCount 个问题收敛归档。可前往CodeCC工具分析记录下载文件查看详情")
        val curTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val detailFile = File(commandParam.dataRootPath + File.separator + "codecc_defect_detail.html")
        val indexHtmlBody = StringBuilder()
        indexHtmlBody.append("<!DOCTYPE html>\n" +
            "<html>\n" +
            "\n" +
            "<head>\n" +
            "    <meta charset=\"UTF-8\">\n" +
            "    <title>CodeCC问题收敛详情</title>\n" +
            "\t<style>\n" +
            "        body {\n" +
            "            font-family: Arial, \"Helvetica Neue\", Helvetica, sans-serif;\n" +
            "            font-size: 16px;\n" +
            "            font-weight: normal;\n" +
            "            margin: 0;\n" +
            "            padding: 0;\n" +
            "            color: #333\n" +
            "        }\n" +
            "\n" +
            "        #overview {\n" +
            "            padding: 20px 30px\n" +
            "        }\n" +
            "\n" +
            "        td,\n" +
            "        th {\n" +
            "            padding: 5px 10px\n" +
            "        }\n" +
            "\n" +
            "        h1 {\n" +
            "            margin: 0\n" +
            "        }\n" +
            "\n" +
            "        table {\n" +
            "            margin: 30px;\n" +
            "            width: calc(100% - 60px);\n" +
            "            max-width: 1000px;\n" +
            "            border-radius: 5px;\n" +
            "            border: 1px solid #ddd;\n" +
            "            border-spacing: 0px;\n" +
            "        }\n" +
            "\n" +
            "        th {\n" +
            "            font-weight: 400;\n" +
            "            font-size: medium;\n" +
            "            text-align: left;\n" +
            "            cursor: pointer\n" +
            "        }\n" +
            "\n" +
            "        td.clr-1,\n" +
            "        td.clr-2,\n" +
            "        th span {\n" +
            "            font-weight: 700\n" +
            "        }\n" +
            "\n" +
            "        th span {\n" +
            "            float: right;\n" +
            "            margin-left: 20px\n" +
            "        }\n" +
            "\n" +
            "        th span:after {\n" +
            "            content: \"\";\n" +
            "            clear: both;\n" +
            "            display: block\n" +
            "        }\n" +
            "\n" +
            "        tr:last-child td {\n" +
            "            border-bottom: none\n" +
            "        }\n" +
            "\n" +
            "\n" +
            "\n" +
            "        #overview.bg-0,\n" +
            "        tr.bg-0 th {\n" +
            "            color: #468847;\n" +
            "            background: #dff0d8;\n" +
            "            border-bottom: 1px solid #d6e9c6\n" +
            "        }\n" +
            "\n" +
            "        #overview.bg-1,\n" +
            "        tr.bg-1 th {\n" +
            "            color: #f0ad4e;\n" +
            "            background: #fcf8e3;\n" +
            "            border-bottom: 1px solid #fbeed5\n" +
            "        }\n" +
            "\n" +
            "        #overview.bg-2,\n" +
            "        tr.bg-2 th {\n" +
            "            color: #b94a48;\n" +
            "            background: #f2dede;\n" +
            "            border-bottom: 1px solid #eed3d7\n" +
            "        }\n" +
            "\n" +
            "        td {\n" +
            "            border-bottom: 1px solid #ddd\n" +
            "        }\n" +
            "\n" +
            "        td.clr-1 {\n" +
            "            color: #f0ad4e\n" +
            "        }\n" +
            "\n" +
            "        td.clr-2 {\n" +
            "            color: #b94a48\n" +
            "        }\n" +
            "\n" +
            "        td a {\n" +
            "            color: #3a33d1;\n" +
            "            text-decoration: none\n" +
            "        }\n" +
            "\n" +
            "        td a:hover {\n" +
            "            color: #272296;\n" +
            "            text-decoration: underline\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "\n" +
            "<body>\n" +
            "    <div id=\"overview\">\n" +
            "        <h1>CodeCC问题收敛详情</h1>\n" +
            "\t\t<br/>\n" +
            "        <div>\n")
        indexHtmlBody.append("<span>共 $fileCount 个文件， $totalDefectCount 个问题</span> - 生成于 $curTime")
        indexHtmlBody.append("</div>\n" +
            "    </div>\n" +
            "    <table>\n" +
            "        <tbody>")
        var fileNo = 0
        largeFileDefects.forEach {
            var fileDefectCount = if (it.defects == null) 0 else it.defects.size
            indexHtmlBody.append("<tr class=\"bg-2\" data-group=\"f-${fileNo}\">\n" +
                "                <th colspan=\"4\">\n" +
                "                    [+] ${it.file}\n" +
                "                    <span>$fileDefectCount 个问题</span>\n" +
                "                </th>\n" +
                "            </tr>\n")
            if (it.defects != null && it.defects.isNotEmpty()) {
                it.defects.forEach { defect ->
                    indexHtmlBody.append("<tr style=\"display:none\" class=\"f-${fileNo}\">\n" +
                        "                <td>${defect.line ?: ""}</td>\n" +
                        "                <td class=\"clr-2\">${defect.checkerName ?: ""}</td>\n" +
                        "                <td>${defect.description ?: ""}</td>\n" +
                        "            </tr>")
                }
            }
            fileNo++
        }
        indexHtmlBody.append("\n" +
            "        </tbody>\n" +
            "    </table>\n" +
            "    <script type=\"text/javascript\">\n" +
            "        var groups = document.querySelectorAll(\"tr[data-group]\");\n" +
            "        for (i = 0; i < groups.length; i++) {\n" +
            "            groups[i].addEventListener(\"click\", function () {\n" +
            "                var inGroup = document.getElementsByClassName(this.getAttribute(\"data-group\"));\n" +
            "                this.innerHTML = (this.innerHTML.indexOf(\"+\") > -1) ? this.innerHTML.replace(\"+\", \"-\") : this.innerHTML.replace(\"-\", \"+\");\n" +
            "                for (var j = 0; j < inGroup.length; j++) {\n" +
            "                    inGroup[j].style.display = (inGroup[j].style.display !== \"none\") ? \"none\" : \"table-row\";\n" +
            "                }\n" +
            "            });\n" +
            "        }\n" +
            "    </script>\n" +
            "</body>\n" +
            "</html>")
        detailFile.writeText(indexHtmlBody.toString())
        return detailFile.canonicalPath
    }
}
