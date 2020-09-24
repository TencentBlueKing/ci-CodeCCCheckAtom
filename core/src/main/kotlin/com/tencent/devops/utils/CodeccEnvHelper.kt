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

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.atom.utils.http.SdkUtils
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccApi
import com.tencent.devops.pojo.BuildScriptType
import com.tencent.devops.pojo.CodeccCheckAtomParam
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.OSType
import com.tencent.devops.utils.common.AgentEnv
import java.io.File
import java.util.*

object CodeccEnvHelper {

    private val api = CodeccApi()

    private val ENV_FILES = arrayOf("result.log", "result.ini")

    private var codeccWorkspace: File? = null

    init {
        // 第三方构建机
        if (AgentEnv.isThirdParty()) {
            println("[初始化] 检测到这是第三方构建机")
        }
    }

    fun getCodeccEnv(workspace: String): Map<String, StringData> {
        val result = mutableMapOf<String, StringData>()
        ENV_FILES.forEach { result.putAll(readScriptEnv(File(workspace), it)) }
        return result
    }

    private fun readScriptEnv(workspace: File, file: String): Map<String, StringData> {
        val f = File(workspace, file)
        if (!f.exists()) {
            return mapOf()
        }
        if (f.isDirectory) {
            return mapOf()
        }

        val lines = f.readLines()
        if (lines.isEmpty()) {
            return mapOf()
        }
        // KEY-VALUE
        return lines.filter { it.contains("=") }.map {
            val split = it.split("=", ignoreCase = false, limit = 2)
            split[0].trim() to StringData(split[1].trim())
        }.toMap()
    }

    fun saveTask(atomContext: AtomContext<out CodeccCheckAtomParam>) {
        with(atomContext.param) {
            api.saveTask(projectName, pipelineId, pipelineBuildId)
        }
    }

    fun getScriptType(): BuildScriptType {
        return when (getOS()) {
            OSType.MAC_OS, OSType.LINUX -> BuildScriptType.SHELL
            OSType.WINDOWS -> BuildScriptType.BAT
            else -> BuildScriptType.SHELL
        }
    }

    fun getOS(): OSType {
        val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
        return if (osName.indexOf(string = "mac") >= 0 || osName.indexOf("darwin") >= 0) {
            OSType.MAC_OS
        } else if (osName.indexOf("win") >= 0) {
            OSType.WINDOWS
        } else if (osName.indexOf("nux") >= 0) {
            OSType.LINUX
        } else {
            OSType.OTHER
        }
    }

    fun getVariable(): Map<String, String> {
        val map = JsonUtil.to(File(SdkUtils.getInputFile()).readText(), object : TypeReference<Map<String, Any>>() {})
        return map.map { it.key to it.value.toString() }.toMap()
    }

    // 第三方构建机初始化
    fun thirdInit(codeccExecuteConfig: CodeccExecuteConfig) {
        // 第三方构建机安装环境
        if (AgentEnv.isThirdParty()) {
            when (getOS()) {
                OSType.LINUX -> {
                    CodeccInstaller.setUpPython3(codeccExecuteConfig.atomContext.param)
                }
                else -> {
                }
            }
        }
    }

    fun getCodeccWorkspace(param: CodeccCheckAtomParamV3): File {
        if (codeccWorkspace != null) return codeccWorkspace!!

        val workspace = File(param.bkWorkspace)
        val buildId = param.pipelineBuildId

        // Copy the nfs coverity file to workspace
        println("[初始化] get the workspace: ${workspace.canonicalPath}")
        println("[初始化] get the workspace parent: ${workspace.parentFile?.canonicalPath} | '${File.separatorChar}'")
        println("[初始化] get the workspace parent string: ${workspace.parent}")

        val tempDir = File(workspace, ".temp")
        println("[初始化] get the workspace path parent: ${tempDir.canonicalPath}")
        codeccWorkspace = File(tempDir, "codecc_$buildId")
        if (!codeccWorkspace!!.exists()) {
            codeccWorkspace!!.mkdirs()
        }

        return codeccWorkspace!!
    }
}
