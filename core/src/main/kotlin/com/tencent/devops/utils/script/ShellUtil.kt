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

package com.tencent.devops.utils.script

import com.tencent.devops.pojo.exception.CodeccTaskExecException
import com.tencent.devops.pojo.script.BuildEnv
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files

object ShellUtil {

    private const val setEnv = "setEnv(){\n" +
        "        local key=\$1\n" +
        "        local val=\$2\n" +
        "\n" +
        "        if [[ -z \"\$@\" ]]; then\n" +
        "            return 0\n" +
        "        fi\n" +
        "\n" +
        "        if ! echo \"\$key\" | grep -qE \"^[a-zA-Z_][a-zA-Z0-9_]*\$\"; then\n" +
        "            echo \"[\$key] is invalid\" >&2\n" +
        "            return 1\n" +
        "        fi\n" +
        "\n" +
        "        echo \$key=\$val  >> ##resultFile##\n" +
        "        export \$key=\"\$val\"\n" +
        "    }\n"

    private const val setGateValue = "setGateValue(){\n" +
        "        local key=\$1\n" +
        "        local val=\$2\n" +
        "\n" +
        "        if [[ -z \"\$@\" ]]; then\n" +
        "            return 0\n" +
        "        fi\n" +
        "\n" +
        "        if ! echo \"\$key\" | grep -qE \"^[a-zA-Z_][a-zA-Z0-9_]*\$\"; then\n" +
        "            echo \"[\$key] is invalid\" >&2\n" +
        "            return 1\n" +
        "        fi\n" +
        "\n" +
        "        echo \$key=\$val  >> ##gateValueFile##\n" +
        "    }\n"
    const val GATEWAY_FILE = "gatewayValueFile.ini"

    private val specialKey = listOf(".", "-", "languageRuleSetMap", "script", "tools", "path", "BK_CI_SVN_REPO_HEAD_REVERSION_COMMENT", "DEVOPS_SVN_REPO_HEAD_REVERSION_COMMENT", "BK_CI_PIPELINE_MATERIAL_NEW_COMMIT_COMMENT")
    private val specialValue = listOf("|", "&", "(", ")")
    private val specialCharToReplace = Regex("['\n]") // --bug=75509999 Agent环境变量中替换掉破坏性字符
    private const val WORKSPACE_ENV = "WORKSPACE"
    private val logger = LoggerFactory.getLogger(ShellUtil::class.java)

    fun execute(
        script: String,
        dir: File,
        buildEnvs: List<BuildEnv>,
        runtimeVariables: Map<String, String>,
        continueNoneZero: Boolean = false,
        prefix: String = "",
        printErrorLog: Boolean = true
    ): String {
        return try {
            executeUnixCommand(
                command = getCommandFile(script, dir, buildEnvs, runtimeVariables, continueNoneZero).canonicalPath,
                sourceDir = dir,
                prefix = prefix
            )
        } catch (e: Throwable) {
            if (printErrorLog) logger.error("Fail to execute shell script: ${e.message}")
            throw CodeccTaskExecException("Fail to execute shell script: ${e.message}")
        }
    }

    fun getCommandFile(
        script: String,
        dir: File,
        buildEnvs: List<BuildEnv>,
        runtimeVariables: Map<String, String>,
        continueNoneZero: Boolean = false
    ): File {
        val file = Files.createTempFile("devops_script", ".sh").toFile()
//        file.deleteOnExit()

        val command = StringBuilder()
        val bashStr = script.split("\n")[0]
        if (bashStr.startsWith("#!/")) {
            command.append(bashStr).append("\n")
        }

        command.append("export $WORKSPACE_ENV=${dir.absolutePath}\n")
            .append("export DEVOPS_BUILD_SCRIPT_FILE=${file.absolutePath}\n")
        val commonEnv = runtimeVariables.filter {
            !specialEnv(it.key, it.value)
        }
        if (commonEnv.isNotEmpty()) {
            commonEnv.forEach { (name, value) ->
                // --bug=75509999 Agent环境变量中替换掉破坏性字符
                val clean = value.replace(specialCharToReplace, "")
                command.append("export $name='$clean'\n")
            }
        }
        if (buildEnvs.isNotEmpty()) {
            var path = ""
            buildEnvs.forEach { buildEnv ->
                val home = File(getEnvironmentPathPrefix(), "${buildEnv.name}/${buildEnv.version}/")
                if (!home.exists()) {
                    println("环境变量路径(${home.absolutePath})不存在")
                }
                val envFile = File(home, buildEnv.binPath)
                if (!envFile.exists()) {
                    println("环境变量路径(${envFile.absolutePath})不存在")
                    return@forEach
                }
                // command.append("export $name=$path")
                path = if (path.isEmpty()) {
                    envFile.absolutePath
                } else {
                    "${envFile.absolutePath}:$path"
                }
                if (buildEnv.env.isNotEmpty()) {
                    buildEnv.env.forEach { (name, path) ->
                        val p = File(home, path)
                        command.append("export $name=${p.absolutePath}\n")
                    }
                }
            }
            if (path.isNotEmpty()) {
                path = "$path:\$PATH"
                command.append("export PATH=$path\n")
            }
        }

        if (!continueNoneZero) {
            command.append("set -e\n")
        } else {
            println("每行命令运行返回值非零时，继续执行脚本")
            command.append("set +e\n")
        }

        command.append(setEnv.replace("##resultFile##", File(dir, "result.log").absolutePath))
        command.append(setGateValue.replace("##gateValueFile##", File(dir, GATEWAY_FILE).absolutePath))
        command.append(script)

        file.writeText(command.toString())
        executeUnixCommand("chmod +x ${file.absolutePath}", dir)

        return file
    }

    private fun executeUnixCommand(command: String, sourceDir: File, prefix: String = ""): String {
        return CommandLineUtils.execute(command, sourceDir, true, prefix)
    }

    private fun specialEnv(key: String, value: String): Boolean {
        specialKey.forEach {
            if (key.contains(it)) {
                return true
            }
        }

        specialValue.forEach {
            if (value.contains(it)) {
                return true
            }
        }
        return false
    }

    fun getEnvironmentPathPrefix(): String {
        val os = System.getProperty("os.name")
        if (os.isNullOrEmpty()) {
            return ENVIRONMENT_LINUX_PATH_PREFIX
        }
        if (os.startsWith("mac", true)) {
            return ENVIRONMENT_MAC_PATH_PREFIX
        }
        return ENVIRONMENT_LINUX_PATH_PREFIX
    }

    private const val ENVIRONMENT_LINUX_PATH_PREFIX = "/data/bkdevops/apps/"

    private const val ENVIRONMENT_MAC_PATH_PREFIX = "/data/bkdevops/apps/"
}
