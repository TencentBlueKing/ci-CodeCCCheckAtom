package com.tencent.devops.utils.script

import com.tencent.devops.pojo.exception.CodeccTaskExecException
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.Charset

object BatScriptUtil {
    private const val setEnv = ":setEnv\r\n" +
        "    set file_save_dir=\"##resultFile##\"\r\n" +
        "    echo %~1=%~2 >>%file_save_dir%\r\n" +
        "    set %~1=%~2\r\n" +
        "    goto:eof\r\n"

    private const val setGateValue = ":setGateValue\r\n" +
        "    set file_save_dir=\"##gateValueFile##\"\r\n" +
        "    echo %~1=%~2 >>%file_save_dir%\r\n" +
        "    set %~1=%~2\r\n" +
        "    goto:eof\r\n"
    private const val GATEWAY_FILE = "gatewayValueFile.ini"
    private const val WORKSPACE_ENV = "WORKSPACE"

    private val specialVariableKey = setOf("languageRuleSetMap", "script", "tools", "path", "BK_CI_SVN_REPO_HEAD_REVERSION_COMMENT", "DEVOPS_SVN_REPO_HEAD_REVERSION_COMMENT", "BK_CI_PIPELINE_MATERIAL_NEW_COMMIT_COMMENT")

    private val logger = LoggerFactory.getLogger(BatScriptUtil::class.java)

    fun execute(
        script: String,
        runtimeVariables: Map<String, String>,
        dir: File,
        prefix: String = "",
        printErrorLog: Boolean = true
    ): String {
        val file = getCommandFile(script, dir, runtimeVariables)
        try {
            return CommandLineUtils.execute(
                command = "cmd.exe /C \"${file.canonicalPath}\"",
                workspace = dir,
                print2Logger = true,
                prefix = prefix,
                printException = printErrorLog
            )
        } catch (e: Throwable) {
            if (printErrorLog) logger.error("Fail to execute bat script: ${e.message}")
            throw CodeccTaskExecException("Fail to execute bat script: ${e.message}")
        }
    }

    fun getCommandFile(
        script: String,
        dir: File,
        runtimeVariables: Map<String, String>
    ): File {
        val tmpDir = System.getProperty("java.io.tmpdir")
        val file = if (tmpDir.isNullOrBlank()) {
            File.createTempFile("paas_build_script_", ".bat")
        } else {
            File(tmpDir).mkdirs()
            File.createTempFile("paas_build_script_", ".bat", File(tmpDir))
        }
        file.deleteOnExit()

        val command = StringBuilder()

        command.append("@echo off")
            .append("\r\n")
            .append("set $WORKSPACE_ENV=${dir.absolutePath}\r\n")
            .append("set DEVOPS_BUILD_SCRIPT_FILE=${file.absolutePath}\r\n")
            .append("\r\n")

        runtimeVariables.forEach OUTER@{ (name, value) ->
            specialVariableKey.forEach {key ->
                if (name.contains(key)) return@OUTER
            }

            // 特殊保留字符转义
            val clean = value.replace("\"", "\\\"")
                .replace("&", "^&")
                .replace("<", "^<")
                .replace(">", "^>")
                .replace("|", "^|")
            command.append("set $name=\"$clean\"\r\n") // 双引号防止变量值有空格而意外截断定义
            command.append("set $name=%$name:~1,-1%\r\n") // 去除双引号，防止被程序读到有双引号的变量值
        }

        command.append(script.replace("\n", "\r\n"))
            .append("\r\n")
            .append("exit")
            .append("\r\n")
            .append(setEnv.replace("##resultFile##", File(dir, "result.log").absolutePath))
            .append(setGateValue.replace("##gateValueFile##", File(dir, GATEWAY_FILE).canonicalPath))

        val charset = Charset.defaultCharset()
//        logger.info("The default charset is $charset")

        file.writeText(command.toString(), charset)
//        logger.info("start to run windows script")
        return file
    }
}
