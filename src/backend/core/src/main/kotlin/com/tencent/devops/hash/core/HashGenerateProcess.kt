package com.tencent.devops.hash.core

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.hash.pojo.*
import java.io.File
import java.nio.charset.Charset

@ExperimentalUnsignedTypes
object HashGenerateProcess {

    private val filterCharList = listOf(32.toChar(), 12.toChar(), 10.toChar(), 13.toChar(), 9.toChar(), 11.toChar(), 65533.toChar())

    fun hashMethod(range: Int, inputFileName: String, outputFileName: String) {
        val startTime = System.currentTimeMillis()
        val inputFile = File(inputFileName)
        if (!inputFile.exists()) {
            println("input file does not exists!")
            return
        }
        val hashInputList = try {
            JsonUtil.to(inputFile.readText(), object : TypeReference<Map<String, List<HashLintInputFile>>>() {})["defects"]
        } catch (e: Exception) {
            println("deserialize input file fail!")
            return
        }
        val hashFileList = hashInputList!!.groupBy { it.filePath }
        val hashGenerateOutputList = mutableListOf<HashLintOutputFile>()
        hashFileList.forEach {
            hashGenerateOutputList.addAll(generateSingleHash(it.key, it.value, range))
        }
        val outputFile = File(outputFileName)
        outputFile.writeText(JsonUtil.toJson(mapOf("defects" to hashGenerateOutputList)))
        outputFile.copyTo(inputFile, true)
        println("pp hash finish! time cost: ${System.currentTimeMillis() - startTime}")
    }


    fun hashCCNMethod(inputFileName: String, outputFileName: String) {
        val startTime = System.currentTimeMillis()
        val inputFile = File(inputFileName)
        if (!inputFile.exists()) {
            println("input file does not exists!")
            return
        }
        val inputFileObj = try {
            JsonUtil.to(inputFile.readText(), object : TypeReference<HashCCNInputObj>() {})
        } catch (e: Exception) {
            println("deserialize input file fail!")
            return
        }
        val hashInputList = try {
            inputFileObj.defects
        } catch (e: Exception) {
            println("get defects property fail!")
            return
        }
        val hashFileList = hashInputList.groupBy { it.filePath }
        val hashCCNOutputList = mutableListOf<HashCCNOutputFile>()
        hashFileList.forEach {
            hashCCNOutputList.addAll(generateSingleCCNHash(it.key, it.value))
        }
        val outputFile = File(outputFileName)
        val outputMap = mapOf("defects" to hashCCNOutputList, "filesTotalCCN" to inputFileObj.filesTotalCCN)
        outputFile.writeText(JsonUtil.toJson(outputMap))
        outputFile.copyTo(inputFile, true)
        println("ccn pp hash finish! time cost: ${System.currentTimeMillis() - startTime}")
    }


    private fun generateSingleCCNHash(filePath: String, hashCCNInputList: List<HashCCNInputFile>): List<HashCCNOutputFile> {
        val hashFile = File(filePath)
        if (!hashFile.exists()) {
            return emptyList()
        }
        val hashFileList = hashCCNInputList.groupBy { it.startLine to it.endLine }
        val lineList = hashFile.readLines(Charset.forName("ascii")).toMutableList()
        val hashCCNOutputList = mutableListOf<HashCCNOutputFile>()
        hashFileList.forEach { (t, u) ->
            try {
                var startIndex = t.first.toInt() - 1
                if (startIndex < 0) {
                    startIndex = 0
                }
                var endIndex = t.second.toInt() - 1
                if (endIndex < 0) {
                    endIndex = 0
                }
                if (startIndex > endIndex) {
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
                    return@forEach
                }
                val inputStr = lineList.subList(startIndex, if (endIndex > lineList.size - 1) lineList.size else endIndex + 1)
                    .fold(StringBuilder()) { buff, str -> buff.append(str.filter { it !in filterCharList }) }
                val fuzzyHash = FuzzyHashGenerate.fuzzyHashGenerate(inputStr.toString())
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
                                    pinpointHash = fuzzyHash
                            )
                        }
                )


            } catch (e: Exception) {
                println("generate ccn fuzzy hash fail! line range : $t")
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
        return hashCCNOutputList
    }


    @ExperimentalUnsignedTypes
    private fun generateSingleHash(filePath: String, hashLintInputList: List<HashLintInputFile>, range: Int):
            List<HashLintOutputFile> {
        val hashFile = File(filePath)
        if (!hashFile.exists()) {
            return emptyList()
        }
        val sortHashGenerateInputList = hashLintInputList.groupBy { it.line }
        val lineList = hashFile.readLines(Charset.forName("ascii")).toMutableList()
//        var hashedLineNum = sortedHashInputList[i].line.toInt()
        val hashGenerateOutputList = mutableListOf<HashLintOutputFile>()
        if(lineList.isNullOrEmpty()){
            return emptyList()
        }
        sortHashGenerateInputList.forEach { (t, u) ->
            try {
                var startIndex = t.toInt() - 1
                if (startIndex < 0) {
                    startIndex = 0
                }
                var endIndex = t.toInt() - 1
                if (endIndex < 0) {
                    endIndex = 0
                }
                lineList[startIndex] = lineList[startIndex].filter { it !in filterCharList }
                var i = 1
                while (i <= range) {
                    if ((--startIndex) < 0) {
                        startIndex = 0
                        break
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
                val fuzzyHash = FuzzyHashGenerate.fuzzyHashGenerate(inputStr)
                hashGenerateOutputList.addAll(
                        u.map {
                            HashLintOutputFile(
                                    it.checkerName,
                                    it.description,
                                    it.filePath,
                                    it.line,
                                    fuzzyHash
                            )
                        }
                )
            } catch (e: Exception) {
                println("generate lint fuzzy hash fail! file path: $t")
                hashGenerateOutputList.addAll(
                        u.map {
                            HashLintOutputFile(
                                    it.checkerName,
                                    it.description,
                                    it.filePath,
                                    it.line,
                                    null
                            )
                        }
                )
            }

        }
        return hashGenerateOutputList
    }
}
