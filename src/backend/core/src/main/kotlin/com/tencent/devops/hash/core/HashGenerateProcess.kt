package com.tencent.devops.hash.core

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.hash.pojo.*
import java.io.File

@ExperimentalUnsignedTypes
object HashGenerateProcess {

    /*fun hashMethod(range: Int, inputFileName: String, outputFileName: String) {
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
        val lineList = hashFile.readLines()
        val hashCCNOutputList = mutableListOf<HashCCNOutputFile>()
        hashFileList.forEach { (t, u) ->
            try {
                val startIndex = t.first.toInt() - 1
                val endIndex = t.second.toInt() - 1
                if (startIndex >= endIndex) {
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
                val inputStr = lineList.subList(startIndex, if (endIndex > lineList.size - 1) lineList.size - 1 else endIndex)
                        .fold(StringBuilder()) { buff, str -> buff.append(str.replace("\\s".toRegex(), "")) }
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


    *//**
     * 单个文件分组生成模糊哈希
     *//*
    @ExperimentalUnsignedTypes
    private fun generateSingleHash(filePath: String, hashLintInputList: List<HashLintInputFile>, range: Int):
            List<HashLintOutputFile> {
        val hashFile = File(filePath)
        if (!hashFile.exists()) {
            return emptyList()
        }
        val sortHashGenerateInputList = hashLintInputList.groupBy { it.line }
//        var hashedLineNum = sortedHashInputList[i].line.toInt()
        val hashGenerateOutputList = mutableListOf<HashLintOutputFile>()
        val lineList = hashFile.readLines()
        if(lineList.isNullOrEmpty()){
            return emptyList()
        }
        sortHashGenerateInputList.forEach { (t, u) ->
            try {
                var startIndex = t.toInt() - 1
                var endIndex = t.toInt() - 1
                var i = 1
                while (i <= range) {
                    if ((--startIndex) < 0) {
                        startIndex = 0
                        break
                    }
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
                    if (lineList[endIndex].trim() != "") {
                        i++
                    }
                }
                val inputStr = lineList.subList(startIndex, endIndex)
                        .fold(StringBuilder()) { buff, str -> buff.append(str.replace("\\s".toRegex(), "")) }
                val fuzzyHash = FuzzyHashGenerate.fuzzyHashGenerate(inputStr.toString())
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
    }*/
}
