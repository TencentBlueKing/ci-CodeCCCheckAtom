package com.tencent.devops.hash.core

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.utils.json.JsonUtil
import com.tencent.devops.hash.pojo.HashGenerateInputFile
import com.tencent.devops.hash.pojo.HashGenerateOutputFile
import java.io.File

@ExperimentalUnsignedTypes
object HashGenerateProcess {

    fun hashMethod(range: Int, inputFileName: String, outputFileName : String){
        val startTime = System.currentTimeMillis()
        val inputFile = File(inputFileName)
        if (!inputFile.exists()) {
            println("input file does not exists!")
            return
        }
        val hashInputList = try {
            JsonUtil.fromJson<Map<String, List<HashGenerateInputFile>>>(inputFile.readText())["defects"]
        } catch (e: Exception) {
            println("deserialize input file fail!")
            return
        }
        val hashFileList = hashInputList!!.groupBy { it.filePath }
        val hashGenerateOutputList = mutableListOf<HashGenerateOutputFile>()
        hashFileList.forEach {
            hashGenerateOutputList.addAll(generateSingleHash(it.key, it.value, range))
        }
        val outputFile = File(outputFileName)
        outputFile.writeText(JsonUtil.toJson(mapOf("defects" to hashGenerateOutputList)))
        outputFile.copyTo(inputFile, true)
        println("pp hash finish! time cost: ${System.currentTimeMillis() - startTime}")
    }


    /**
     * 单个文件分组生成模糊哈希
     */
    @ExperimentalUnsignedTypes
    private fun generateSingleHash(filePath: String, hashGenerateInputList: List<HashGenerateInputFile>, range: Int):
            List<HashGenerateOutputFile> {
        val hashFile = File(filePath)
        if (!hashFile.exists()) {
            return emptyList()
        }
        val sortHashGenerateInputList = hashGenerateInputList.groupBy { it.line }
//        var hashedLineNum = sortedHashInputList[i].line.toInt()
        val hashGenerateOutputList = mutableListOf<HashGenerateOutputFile>()
        val lineList = hashFile.readLines()
        sortHashGenerateInputList.forEach { (t, u) ->
            var startIndex = t.toInt() - 1
            var endIndex = t.toInt() - 1
            var i = 1
            while (i <= range) {
                if((--startIndex) < 0){
                    startIndex = 0
                    break
                }
                if (lineList[startIndex].trim() != "") {
                    i++
                }
            }
            i = 1
            while (i <= range) {
                if((++endIndex) > lineList.size - 1){
                    endIndex = lineList.size - 1
                    break
                }
                if (lineList[endIndex].trim() != "") {
                    i++
                }
            }
            val inputStr = lineList.subList(startIndex, endIndex)
                    .fold(StringBuilder()) { buff, str -> buff.append(str.replace("\\s".toRegex(), "")) }
            val fuzzyHash = FuzzyHashGenerate.fuzzyHashGenerate(inputStr.toString())
            hashGenerateOutputList.addAll(
                    u.map {
                        HashGenerateOutputFile(
                                it.checkerName,
                                it.description,
                                it.filePath,
                                it.line,
                                fuzzyHash
                        )
                    }
            )
        }
        return hashGenerateOutputList
    }
}