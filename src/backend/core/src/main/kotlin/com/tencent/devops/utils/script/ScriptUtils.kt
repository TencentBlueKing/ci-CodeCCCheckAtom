package com.tencent.devops.utils.script

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.script.BuildEnv
import com.tencent.devops.utils.CodeccEnvHelper
import java.io.File

object ScriptUtils {

    fun execute(
        script: String,
        dir: File?,
        buildEnvs: List<BuildEnv> = listOf(),
        runtimeVariables: Map<String, String> = emptyMap(),
        continueNoneZero: Boolean = false,
        prefix: String = "",
        printErrorLog: Boolean = true,
        print2Logger: Boolean = true
    ): String {
        return when (CodeccEnvHelper.getOS()) {
            OSType.LINUX, OSType.MAC_OS -> {
                ShellUtil.execute(script, dir, buildEnvs, runtimeVariables, continueNoneZero, prefix, printErrorLog, print2Logger)
            }
            OSType.WINDOWS -> {
                BatScriptUtil.execute(script, runtimeVariables, dir, prefix, printErrorLog, print2Logger)
            }
            else -> {
                ShellUtil.execute(script, dir, buildEnvs, runtimeVariables, continueNoneZero, prefix, printErrorLog, print2Logger)
            }
        }
    }

    fun executeCodecc(
        script: String,
        dir: File,
        prefix: String = "codecc",
        exportEnv: Map<String, String> = emptyMap(),
        printScript: Boolean = false,
        failExit: Boolean = true
    ): String {
        val list = mutableListOf<String>()
        if (CodeccEnvHelper.getOS() == OSType.LINUX || CodeccEnvHelper.getOS() == OSType.MAC_OS) {
            if (exportEnv.isNotEmpty()) {
                exportEnv.forEach {
                    if (it.key == "PATH") {
                        list.add("export PATH=${it.value}" + File.pathSeparator + "\$PATH\n")
                    } else {
                        list.add("export ${it.key}=${it.value}")
                    }
                }
            }
            list.add("export LANG=zh_CN.UTF-8\n")
            list.add("export PATH=\$PATH\n")
        }

        list.add(script)

        if (printScript) LogUtils.printLog("execute script is: ${list.joinToString(" ")}")

        return execute(
            script = list.joinToString(" "),
            dir = dir,
            runtimeVariables = CodeccEnvHelper.getVariable(),
            prefix = "[$prefix] "
        )
    }
}
