package com.tencent.devops.processor

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.hash.core.FuzzyHashGenerate
import com.tencent.devops.hash.pojo.HashCCNOutputFile
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.pojo.OSType
import com.tencent.devops.processor.annotation.ProcessAnnotation
import com.tencent.devops.utils.CodeccEnvHelper
import java.io.File
import java.nio.charset.Charset

@ExperimentalUnsignedTypes
@ProcessAnnotation(name = "ccnPPHash", type = "ccn", order = 3)
class CCNHashDefectSubProcessor : AbstractDefectSubProcessor() {

    override fun realSubProcess(inputDefectInfo: FileProcessResult): FileProcessResult {
        val hashFile =
            if (CodeccEnvHelper.getOS() == OSType.WINDOWS && !inputDefectInfo.fileAbsolutePath.isNullOrBlank())
                File(inputDefectInfo.fileAbsolutePath!!)
            else
                File(inputDefectInfo.filePath)
        if (!hashFile.exists()) {
            inputDefectInfo.ccnDefects = emptyList()
            return inputDefectInfo
        }
        val hashCCNInputList = inputDefectInfo.ccnDefects
        if (hashCCNInputList.isNullOrEmpty()) {
            return inputDefectInfo
        }

        val hashFileList = hashCCNInputList.groupBy { it.startLine to it.endLine }
        val lineList = hashFile.readLines(Charset.forName("ascii")).toMutableList()
        val hashCCNOutputList = mutableListOf<HashCCNOutputFile>()
        hashFileList.forEach { (t, u) ->
            try {
                val ignoreCommentDefect = if (!u.isNullOrEmpty()) u[0].ignoreCommentDefect == true else false
                var startIndex = t.first.toInt() - 1
                if (startIndex < 0) {
                    startIndex = 0
                }
                if (startIndex > lineList.size - 1) {
                    startIndex = lineList.size - 1
                }
                var endIndex = t.second.toInt() - 1
                if (endIndex < 0) {
                    endIndex = 0
                }
                if (endIndex > lineList.size - 1) {
                    endIndex = lineList.size - 1
                }
                if (startIndex > endIndex) {
                    u.forEach {
                        it.pinpointHash = null
                    }
                    return@forEach
                }
                if (ignoreCommentDefect) {
                    lineList[startIndex] = lineList[startIndex].replace(Regex("//\\s*NOC[A|C]:.*$"), "")
                }
                val inputStr = lineList.subList(startIndex, if (endIndex > lineList.size - 1) lineList.size else endIndex + 1)
                    .fold(StringBuilder()) { buff, str -> buff.append(str.filter { it !in filterCharList }) }
                val fuzzyHash = FuzzyHashGenerate.fuzzyHashGenerate(inputStr.toString())
                u.forEach {
                    it.pinpointHash = fuzzyHash
                }
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.printErrorLog("generate ccn fuzzy hash fail! line range : $t, error message: ${e.message}")
                hashCCNOutputList.addAll(
                        u.map {
                            HashCCNOutputFile(
                                    ccn = it.ccn,
                                    filePath = it.filePath,
                                    conditionLines = it.conditionLines,
                                    functionLines = it.functionLines,
                                    functionNames = it.functionNames,
                                    longName = it.longName,
                                    totalLines = it.totalLines,
                                    startLine = it.startLine,
                                    endLine = it.endLine,
                                    pinpointHash = null
                            )
                        }
                )
            }
        }
        return inputDefectInfo
    }
}
