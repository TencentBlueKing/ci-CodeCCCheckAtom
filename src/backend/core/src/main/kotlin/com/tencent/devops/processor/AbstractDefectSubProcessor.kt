package com.tencent.devops.processor

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.FileProcessResult

/**
 * 子告警逻辑处理抽象类
 */
abstract class AbstractDefectSubProcessor {

    protected val filterCharList = listOf(32.toChar(), 12.toChar(), 10.toChar(), 13.toChar(), 9.toChar(), 11.toChar(), 65533.toChar())

    /**
     * 主要处理方法
     */
    fun mainDefectSubProcess(inputDefectInfo: FileProcessResult) : FileProcessResult{
        return try {
            realSubProcess(inputDefectInfo)
        } catch (t : Throwable) {
            t.printStackTrace()
            LogUtils.printLog("process defect fail! message: ${t.message}")
            inputDefectInfo
        }
    }


    /**
     * 真正处理方法，除了主要方法抽离部分公共逻辑
     */
    abstract fun realSubProcess(inputDefectInfo: FileProcessResult) : FileProcessResult
}
