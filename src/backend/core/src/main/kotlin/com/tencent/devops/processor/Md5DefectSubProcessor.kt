package com.tencent.devops.processor

import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.Trace
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.docker.utils.CommonUtils
import com.tencent.devops.pojo.FileMD5Info
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.processor.annotation.ProcessAnnotation
import com.tencent.devops.utils.FilePathUtils
import org.apache.commons.codec.digest.DigestUtils
import java.io.File

@ProcessAnnotation(name = "md5", type = "common", order = 2)
class Md5DefectSubProcessor : AbstractDefectSubProcessor() {

    override fun realSubProcess(inputDefectInfo: FileProcessResult): FileProcessResult {
        var commandParam = inputDefectInfo.commandParam
        val fileMD5Info = generateFileMd5(inputDefectInfo.filePath, commandParam)
        inputDefectInfo.fileMd5 = fileMD5Info.fileMd5
        inputDefectInfo.fileRelPath = fileMD5Info.fileRelPath
        inputDefectInfo.fileAbsolutePath = fileMD5Info.fileAbsolutePath

        var defectTraceFileMap = mutableMapOf(inputDefectInfo.filePath to fileMD5Info)
        inputDefectInfo.lintDefects?.forEach { defect ->
            defect.defectInstances?.forEach {
                extractFilesFromTraces(it.traces, defectTraceFileMap, commandParam)
            }
        }
        inputDefectInfo.defectTraceFileMap = defectTraceFileMap
        return inputDefectInfo
    }

    private fun extractFilesFromTraces(
        traces: List<Trace>?,
        defectTraceFileMap: MutableMap<String, FileMD5Info>,
        commandParam: CommandParam
    ) {
        if (traces == null) {
            return
        }
        traces.forEach {
            defectTraceFileMap.putIfAbsent(it.filePath, generateFileMd5(it.filePath, commandParam))
            extractFilesFromTraces(it.linkTrace, defectTraceFileMap, commandParam)
        }
    }

    //生成文件md5信息
    private fun generateFileMd5(filePath: String, commandParam: CommandParam): FileMD5Info {
        val file = File(CommonUtils.changePathToWindows(filePath))
        val absoluteFilePath = file.absolutePath.replace("\\", "/")
        val fileRelPath = FilePathUtils.getRelPath(absoluteFilePath)
        LogUtils.printDebugLog("fileRelPath path to $fileRelPath")
        return if (file.exists()) {
            val md5 = file.inputStream().use {
                DigestUtils.md5Hex(it)
            }
            FileMD5Info(fileRelPath, absoluteFilePath, md5)
        } else {
            FileMD5Info(fileRelPath, absoluteFilePath, null)
        }
    }

}
