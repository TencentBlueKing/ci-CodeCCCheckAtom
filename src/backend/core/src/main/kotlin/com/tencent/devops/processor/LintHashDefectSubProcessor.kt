package com.tencent.devops.processor

import com.tencent.devops.docker.LintDefectProcessor
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.hash.core.FuzzyHashGenerate
import com.tencent.devops.pojo.FileProcessResult
import com.tencent.devops.pojo.OSType
import com.tencent.devops.processor.annotation.ProcessAnnotation
import com.tencent.devops.utils.CodeccEnvHelper
import java.io.File
import java.nio.charset.Charset

@ExperimentalUnsignedTypes
@ProcessAnnotation(name = "lintPPHash", type = "lint", order = 3)
class LintHashDefectSubProcessor: AbstractDefectSubProcessor() {

    companion object{
        private const val range = 5
    }

    override fun realSubProcess(inputDefectInfo: FileProcessResult): FileProcessResult {
        val hashFile =
            if (CodeccEnvHelper.getOS() == OSType.WINDOWS && !inputDefectInfo.fileAbsolutePath.isNullOrBlank())
                File(inputDefectInfo.fileAbsolutePath!!)
            else
                File(inputDefectInfo.filePath)
        if (!hashFile.exists()) {
            LogUtils.printLog("hash file not exists")
            inputDefectInfo.lintDefects = emptyList()
            return inputDefectInfo
        }
        val hashLintInputList = inputDefectInfo.lintDefects
        if (hashLintInputList.isNullOrEmpty()) {
            LogUtils.printLog("input hash list is empty")
            return inputDefectInfo
        }
        if (hashLintInputList.size > inputDefectInfo.commandParam.gatherDefectThreshold) {
            LogUtils.printLog("large size hash file do not need to pp hash")
            return inputDefectInfo
        }
        val sortHashGenerateInputList = hashLintInputList.groupBy { it.line }
        val lineList = hashFile.readLines(Charset.forName("ascii")).toMutableList()
        if(lineList.isNullOrEmpty()){
            inputDefectInfo.lintDefects = emptyList()
            return inputDefectInfo
        }
        sortHashGenerateInputList.forEach { (t, u) ->
            try {
                //要将注释忽略这部分文字从pphash判断中去掉，防止影响告警聚类
                val ignoreCommentDefect = if (!u.isNullOrEmpty()) u[0].ignoreCommentDefect == true else false
                var startIndex = t!!.toInt() - 1
                if (startIndex < 0) {
                    startIndex = 0
                }
                if (startIndex > lineList.size - 1) {
                    startIndex = lineList.size - 1
                }
                var endIndex = t.toInt() - 1
                if (endIndex < 0) {
                    endIndex = 0
                }
                if (endIndex > lineList.size - 1) {
                    endIndex = lineList.size - 1
                }
                if (ignoreCommentDefect) {
                    lineList[startIndex] = lineList[startIndex].replace(Regex("//\\s*NOC[A|C]:.*$"), "")
                }
                lineList[startIndex] = lineList[startIndex].filter { it !in filterCharList }
                var i = 1
                while (i <= range) {
                    if ((--startIndex) < 0) {
                        startIndex = 0
                        break
                    }
                    if (ignoreCommentDefect && startIndex == t.toInt() - 2) {
                        lineList[startIndex] = lineList[startIndex].replace(Regex("^\\s*//\\s*NOC[A|C]:.*$"), "")
                    }
                    lineList[startIndex] = lineList[startIndex].filter { it !in filterCharList }
                    if (lineList[startIndex].trim() != "") {
                        i++
                    }
                }
                i = 1
                while (i <= range) {
                    if ((++endIndex) > lineList.size - 1) {
                        endIndex = lineList.size - 1
                        break
                    }
                    lineList[endIndex] = lineList[endIndex].filter { it !in filterCharList }
                    if (lineList[endIndex].trim() != "") {
                        i++
                    }
                }
                val inputStr = lineList.subList(startIndex, endIndex + 1).joinToString(separator = "")
                val fuzzyHash = FuzzyHashGenerate.fuzzyHashGenerate(inputStr.toString())
                u.forEach {
                    it.pinpointHash = fuzzyHash
                }
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.printErrorLog("generate lint fuzzy hash fail! file path: $t, error message: ${e.message}")
                u.forEach {
                    it.pinpointHash = null
                }
            }
        }
        return inputDefectInfo
    }
}
