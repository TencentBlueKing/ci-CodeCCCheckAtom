package com.tencent.devops.docker.tools

import com.tencent.devops.docker.LocalParam
import com.tencent.devops.docker.pojo.DefectsEntity
import com.tencent.devops.docker.utils.CommonUtils

object LogUtils {
    private var isDebug = false

    fun setDebug(debug: Boolean) {
        isDebug = debug
    }

    fun getDebug(): Boolean {
        return isDebug
    }

    fun printDefect(defect: DefectsEntity) {
        when {
            defect.ccn != null -> {
                println(CommonUtils.changePathToWindows(defect.filePath ?: "") + ":" + defect.startLine
                    + " ccn:" + defect.ccn
                    + " function_name:" + defect.functionName
                    + " function_lines:" + defect.functionLines + " ")
            }
            defect.dupRate != null -> {
                println(CommonUtils.changePathToWindows(defect.file_path ?: "") + " dup_rate:" + defect.dupRate
                    + " dup_lines:" + defect.dupLines)
            }
            else -> {
                println(CommonUtils.changePathToWindows(defect.filePath ?: "") + ":" + (defect.line
                    ?: "") + " " + (defect.checkerName ?: "") + " " + (defect.description ?: ""))
            }
        }
    }

    fun printLog(msg: Any?) {
        val toolName = LocalParam.toolName.get()
        if (!toolName.isNullOrBlank()) {
            print("[$toolName] ")
        }
        println(msg)
    }

    fun printErrorLog(msg: Any?) {
        val toolName = LocalParam.toolName.get()
        if (!toolName.isNullOrBlank()) {
            System.err.print("[$toolName] ")
        }
        System.err.println(msg)
    }


    fun printReturn() {
        println("")
    }

    fun printStr(msg: Any?) {
        print(msg)
    }

    fun printDebugLog(msg: Any?) {
        if (this.isDebug) {
            val toolName = LocalParam.toolName.get()
            if (!toolName.isNullOrBlank()) {
                print("[$toolName] ")
            }
            println(msg)
        }
    }
}