package com.tencent.devops.utils.script

import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.CodeccTaskExecException
import com.tencent.devops.utils.CodeccEnvHelper
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.LogOutputStream
import org.apache.commons.exec.PumpStreamHandler
import org.slf4j.LoggerFactory
import java.io.File

object CommandLineUtils {

    private val logger = LoggerFactory.getLogger(CommandLineUtils::class.java)

    private val specialChars = if (CodeccEnvHelper.getOS() == OSType.WINDOWS) {
        listOf('(', ')', '[', ']', '{', '}', '^', ';', '!', ',', '`', '~', '\'', '"')
    } else {
        listOf('|', ';', '&', '$', '>', '<', '`', '!', '\\', '"', '*', '?', '[', ']', '(', ')', '\'')
    }

    fun execute(command: String, workspace: File?, print2Logger: Boolean, prefix: String = "", printException: Boolean = false): String {

        val result = StringBuffer()

        val cmdLine = CommandLine.parse(command)
        val executor = CommandLineExecutor()
        if (workspace != null) {
            executor.workingDirectory = workspace
        }

        val outputStream = object : LogOutputStream() {
            override fun processLine(line: String?, level: Int) {
                if (line == null)
                    return

                val tmpLine = SensitiveLineParser.onParseLine(prefix + line)
                if (print2Logger) {
                    println(tmpLine)
                }
                result.append(tmpLine).append("\n")
            }
        }

        val errorStream = object : LogOutputStream() {
            override fun processLine(line: String?, level: Int) {
                if (line == null) {
                    return
                }

                val tmpLine = SensitiveLineParser.onParseLine(prefix + line)
                if (print2Logger) {
                    System.err.println(tmpLine)
                }
                result.append(tmpLine).append("\n")
            }
        }
        executor.streamHandler = PumpStreamHandler(outputStream, errorStream)
        try {
            val exitCode = executor.execute(cmdLine)
            if (exitCode != 0) {
                throw CodeccTaskExecException("$prefix Script command execution failed with exit code($exitCode)")
            }
        } catch (t: Throwable) {
            if (printException) logger.warn("Fail to execute the command($command)", t)
            if (print2Logger) logger.error("$prefix Fail to execute the command($command): ${t.message}")
            throw t
        }
        return result.toString()
    }

    fun solveSpecialChar(str: String): String {
        val solveStr = StringBuilder()
        val isWindows = CodeccEnvHelper.getOS() == OSType.WINDOWS
        val encodeChar = if (isWindows) '^' else '\\'
        val charArr = str.toCharArray()
        charArr.forEach { ch ->
            if (ch in specialChars) {
                solveStr.append(encodeChar)
            }

            // windows的%还要特殊处理下
            if (isWindows && ch == '%') {
                solveStr.append('%')
            }

            solveStr.append(ch)
        }

        return solveStr.toString()
    }
}
