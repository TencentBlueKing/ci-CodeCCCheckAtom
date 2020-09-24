/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.utils

import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.LinuxCodeccConstants.Companion.COV_TOOLS
import com.tencent.devops.pojo.exception.CodeccTaskExecException
import com.tencent.devops.pojo.exception.CodeccTimeOutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KFunction1

object CodeccExecuteHelper {

    fun executeCodecc(
        codeccExecuteConfig: CodeccExecuteConfig,
        covFun: KFunction1<CodeccExecuteConfig, String>,
        toolFun: KFunction1<CodeccExecuteConfig, String>
    ): String {
        val runTypes = getRunTypes(codeccExecuteConfig)
        val runCoverity = runTypes.first
        val runTools = runTypes.second

        val result = StringBuilder()

        var expectCount = 0
        if (runCoverity) expectCount++
        if (runTools) expectCount++

        val lock = CountDownLatch(expectCount)
        val successCount = AtomicInteger(0)
        val errorMsg = StringBuilder()
        // 按照旧的逻辑执行COVERITY
        val executor = Executors.newFixedThreadPool(expectCount)
        try {
            if (runCoverity) {
                // 保证coverity跑失败不影响多工具
                executor.execute {
                    try {
                        result.append(covFun(codeccExecuteConfig))
                        successCount.getAndIncrement()
                        println("run coverity or klocwork successful")
                    } catch (e: Exception) {
                        errorMsg.append("run coverity or klocwork fail: ${e.message}\n")
                    } finally {
                        lock.countDown()
                    }
                }
            }
            // 其他类型扫描走新的逻辑
            if (runTools) {
                executor.execute {
                    try {
                        result.append(toolFun(codeccExecuteConfig))
                        successCount.getAndIncrement()
                        println("run codecc tools successful")
                    } catch (e: Exception) {
                        errorMsg.append("run codecc tools fail: ${e.message}\n")
                    } finally {
                        lock.countDown()
                    }
                }
            }
            // 判断最后结果
            // 4个小时当做超时
            lock.await(codeccExecuteConfig.timeOut, TimeUnit.MINUTES)
            if (successCount.get() != expectCount) throw CodeccTaskExecException("运行codecc任务失败: $errorMsg")

            return result.toString()
        } catch (e: InterruptedException) {
            throw CodeccTimeOutException("运行codecc任务超时: ${e.message}")
        } finally {
            executor.shutdownNow()
        }
    }

    private fun getRunTypes(codeccExecuteConfig: CodeccExecuteConfig): Pair<Boolean, Boolean> {
        with(codeccExecuteConfig) {
            val tools = codeccExecuteConfig.tools
            val runCoverity = (filterTools.isEmpty() && COV_TOOLS.minus(tools).size != COV_TOOLS.size) ||
                (filterTools.isNotEmpty() && COV_TOOLS.minus(filterTools).size != COV_TOOLS.size)
            val runTools = (codeccExecuteConfig.filterTools.isEmpty() && tools.minus(COV_TOOLS).isNotEmpty()) ||
                (filterTools.isNotEmpty() && filterTools.minus(COV_TOOLS).isNotEmpty())
            return Pair(runCoverity, runTools)
        }
    }
}