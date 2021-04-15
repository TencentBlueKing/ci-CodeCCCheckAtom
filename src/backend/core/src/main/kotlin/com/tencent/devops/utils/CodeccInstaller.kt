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

import com.tencent.bk.devops.plugin.utils.OkhttpUtils
import com.tencent.devops.api.CodeccApi
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.LinuxCodeccConstants
import com.tencent.devops.utils.common.AgentEnv
import com.tencent.devops.utils.script.CommandLineUtils
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File

object CodeccInstaller {

    private val api = CodeccApi()

    fun setUpPython3(param: CodeccCheckAtomParamV3) {
        // 安装python 3.x
        println("[初始化] download python 3.x")
        val constants = LinuxCodeccConstants(param.bkWorkspace)
        getTool(constants.THIRD_PYTHON3_TAR_FILE, Runnable {
            setupPython3(constants.THIRD_PYTHON3_TAR_FILE)
        })
    }

    // toolFile: 工具文件下载到绝对路径
    // callback: 下载完工具执行的操作
    private fun getTool(toolFile: File, callback: Runnable = Runnable { }) {
        val md5File = File(toolFile.canonicalPath + ".md5")
        val md5 = if (md5File.exists()) md5File.readText() else ""
        val response = api.downloadTool("PYTHON3", CodeccEnvHelper.getOS(), md5, AgentEnv.is32BitSystem())
        OkhttpUtils.downloadFile(response, toolFile)
        if (response.code() != 304) {
            callback.run()
            // 写入md5
            md5File.writeText(getMD5(toolFile))
        } else {
            println("PYTHON3 is newest, do not install")
        }
    }

    private fun getMD5(file: File): String {
        if (!file.exists()) return ""
        file.inputStream().use {
            return DigestUtils.md5Hex(it)
        }
    }

    private fun setupPython3(pythonFile: File) {
        // 解压tgz
        val pythonPath = pythonFile.canonicalPath.removeSuffix(".tgz")
        println("解压${pythonFile.name}到: $pythonFile")
        unzipTgzFile(pythonFile.canonicalPath, pythonPath)

        // 执行相关命令
        CommandLineUtils.execute("chmod -R 755 $pythonPath/bin/python", File("."), true)
        CommandLineUtils.execute("chmod -R 755 $pythonPath/bin/python3", File("."), true)
    }

    private fun unzipTgzFile(tgzFile: String, destDir: String = "./") {
        val blockSize = 4096
        val inputStream = TarArchiveInputStream(GzipCompressorInputStream(File(tgzFile).inputStream()), blockSize)
        while (true) {
            val entry = inputStream.nextTarEntry ?: break
            if (entry.isDirectory) { // 是目录
                val dir = File(destDir, entry.name)
                if (!dir.exists()) dir.mkdirs()
            } else { // 是文件
                File(destDir, entry.name).outputStream().use { outputStream ->
                    while (true) {
                        val buf = ByteArray(4096)
                        val len = inputStream.read(buf)
                        if (len == -1) break
                        outputStream.write(buf, 0, len)
                    }
                }
            }
        }
    }

}