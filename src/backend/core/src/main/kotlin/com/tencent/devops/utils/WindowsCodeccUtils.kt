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
import com.tencent.devops.utils.script.BatScriptUtil
import java.io.File

class WindowsCodeccUtils : CodeccUtils() {

    override fun doPreCodeccSingleCommand(command: MutableList<String>, codeccExecuteConfig: CodeccExecuteConfig) {
        command.add("python -V\n")
        command.add("cd\n")
        command.add("whoami\n")
        command.add("echo %PATH%\n")
        command.add("where python\n")
        command.add("where py\n")
        command.add("py -0\n")
        command.add("py -V\n")
        command.add("py -3 -V\n")

        command.add("py -3")
    }

    override fun doOldPreCodeccSingleCommand(command: MutableList<String>, codeccExecuteConfig: CodeccExecuteConfig) {
        val python3 = getPython3Command(codeccExecuteConfig)
        command.add("$python3 -V\n")
        command.add("cd\n")
        command.add("whoami\n")
        command.add(python3)
        command.add(codeccStartFile)
    }

    // py -3在安装python3时，没装到用户路径下，会报找不到默认python的错误
    private fun getPython3Command(codeccExecuteConfig: CodeccExecuteConfig): String {
        val workspace = File(codeccExecuteConfig.atomContext.param.bkWorkspace)
        try {
            BatScriptUtil.execute(
                script = "py -3 -V",
                runtimeVariables = codeccExecuteConfig.variable,
                dir = workspace,
                printErrorLog = false
            )
            return "py -3"
        } catch (e: Exception) {
            System.err.println(e.message)
        }

        try {
            BatScriptUtil.execute(
                script = "python3 -V",
                runtimeVariables = codeccExecuteConfig.variable,
                dir = workspace,
                printErrorLog = false
            )
            return "python3"
        } catch (e: Exception) {
            System.err.println(e.message)
        }

        return "python"
    }
}
