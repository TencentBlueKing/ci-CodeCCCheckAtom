package com.tencent.devops.docker.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.docker.pojo.CommandParam
import com.tencent.devops.docker.pojo.ImageParam
import com.tencent.devops.docker.pojo.LandunParam
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.pojo.ToolMetaDetailVO
import com.tencent.devops.docker.tools.AESUtil
import com.tencent.devops.docker.tools.FileUtil
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.script.ScriptUtils
import java.io.File
import java.util.*


object CodeccConfig {
    private val propertiesInfo = mutableMapOf<String, String>()

    fun loadToolMeta(landunParam: LandunParam, apiWebServer: String, aesKey: String) {
        loadProperties() // 先取配置文件，再用后台配置刷新，防止后台有问题导致不能跑

        LogUtils.printDebugLog("apiWebServer: $apiWebServer")
        propertiesInfo["CODECC_API_WEB_SERVER"] = apiWebServer

        val toolMetas = CodeccWeb.getBuildToolMeta(landunParam, apiWebServer)
        if (null == toolMetas || toolMetas.isEmpty()) {
            LogUtils.printDebugLog("toolMetas is empty")
            return
        }
        LogUtils.printDebugLog("toolMetas is not empty")
        toolMetas.filterNot { it.name.isNullOrBlank() }.forEach {
            resolveToolMeta(it, aesKey)
        }
        propertiesInfo["LANDUN_CHANNEL_CODE"] = landunParam.channelCode ?: ""
    }

    private fun resolveToolMeta(toolMetaDetailVO: ToolMetaDetailVO, aesKey: String) {
        try {
            val scanCommandKey = "${toolMetaDetailVO.name!!.toUpperCase()}_SCAN_COMMAND"
            val scanCommandValue = toolMetaDetailVO.dockerTriggerShell ?: ""

            val imagePathKey = "${toolMetaDetailVO.name!!.toUpperCase()}_IMAGE_PATH"
            val imagePathValue = toolMetaDetailVO.dockerImageURL ?: ""

            val registryUserKey = "${toolMetaDetailVO.name!!.toUpperCase()}_REGISTRYUSER"
            val registryUserValue = toolMetaDetailVO.dockerImageAccount ?: ""

            val registryPwdKey = "${toolMetaDetailVO.name!!.toUpperCase()}_REGISTRYPWD"
            val registryPwdValue = if (toolMetaDetailVO.dockerImagePasswd.isNullOrBlank()) {
                ""
            } else {
                AESUtil.decrypt(aesKey, toolMetaDetailVO.dockerImagePasswd!!)
            }

            if (scanCommandValue.isNotBlank()) {
                propertiesInfo[scanCommandKey] = scanCommandValue
            }
            if (imagePathValue.isNotBlank()) {
                propertiesInfo[imagePathKey] = imagePathValue
            }

            propertiesInfo[registryUserKey] = registryUserValue
            propertiesInfo[registryPwdKey] = registryPwdValue

            if (!toolMetaDetailVO.toolHomeBin.isNullOrBlank()) {
                val toolHomeBinKey = "${toolMetaDetailVO.name!!.toUpperCase()}_HOME_BIN"
                val toolHomeBinValue = toolMetaDetailVO.toolHomeBin!!
                propertiesInfo[toolHomeBinKey] = toolHomeBinValue
            }

            if (toolMetaDetailVO.toolHistoryVersion != null && toolMetaDetailVO.toolHistoryVersion!!.isNotEmpty()) {
                val toolOldVersionKey = "${toolMetaDetailVO.name!!.toUpperCase()}_OLD_VERSION"
                val toolOldVersionValue = toolMetaDetailVO.toolHistoryVersion!!.joinToString(";")
                propertiesInfo[toolOldVersionKey] = toolOldVersionValue
            }

            if (!toolMetaDetailVO.toolVersion.isNullOrBlank()) {
                val toolNewVersionKey = "${toolMetaDetailVO.name!!.toUpperCase()}_NEW_VERSION"
                val toolNewVersionValue = toolMetaDetailVO.toolVersion!!
                propertiesInfo[toolNewVersionKey] = toolNewVersionValue
            }
        } catch (e: Throwable) {
            LogUtils.printErrorLog(e.message)
        }
    }

    private fun loadProperties(): Map<String, String> {
        try {
            val input = Thread.currentThread().contextClassLoader.getResourceAsStream("config.properties")
            val p = Properties()
            p.load(input)

            for (name in p.stringPropertyNames()) {
                propertiesInfo[name] = p.getProperty(name)
            }
        } catch (e: Exception) {
            println("Load config exception: ${e.message}")
        }
        return propertiesInfo
    }

    fun getConfig(key: String): String? = propertiesInfo[key]

    fun getServerHost() = propertiesInfo["CODECC_API_WEB_SERVER"]

    fun getImage(toolName: String): ImageParam {
        val toolNameUpperCase = toolName.toUpperCase()
        val cmd = (propertiesInfo["${toolNameUpperCase}_SCAN_COMMAND"] ?: "").split("##")
        val imageName = propertiesInfo["${toolNameUpperCase}_IMAGE_PATH"] ?: ""
        val registerUser = propertiesInfo["${toolNameUpperCase}_REGISTRYUSER"] ?: ""
        val registerPwd = propertiesInfo["${toolNameUpperCase}_REGISTRYPWD"] ?: ""
        val env = if (propertiesInfo["${toolNameUpperCase}_ENV"] != null && propertiesInfo["${toolNameUpperCase}_ENV"]!!.isNotBlank()) {
            jacksonObjectMapper().readValue<Map<String, String>>(propertiesInfo["${toolNameUpperCase}_ENV"]!!)
        } else {
            emptyMap()
        }

        return ImageParam(cmd, imageName, registerUser, registerPwd, env)
    }

    fun fileListFromDefects(inputPath: String): List<String> {
        val filePathSet = mutableSetOf<String>()
        if (File(inputPath).exists()) {
            val inputFileText = File(inputPath).readText()
            val inputFileObj = jacksonObjectMapper().readValue<Map<String, Any?>>(inputFileText)
            val defectsDataList = inputFileObj["defects"] as? List<Map<String, String>>
            defectsDataList?.forEach { defect ->
                when {
                    defect["filePath"] != null -> filePathSet.add(CommonUtils.changePathToDocker(defect["filePath"] as String))
                    defect["filePathname"] != null -> filePathSet.add(CommonUtils.changePathToDocker(defect["filePathname"] as String))
                    defect["filename"] != null -> filePathSet.add(CommonUtils.changePathToDocker(defect["filename"] as String))
                    defect["file_path"] != null -> filePathSet.add(CommonUtils.changePathToDocker(defect["file_path"] as String))
                }
            }
        }

        return filePathSet.toList()
    }

    @Synchronized
    fun checkThirdEnv(commandParam: CommandParam, toolName: String) {
        if (commandParam.landunParam.ldEnvType == "LINUX_THIRD_PARTY") {
            val softwareRootPath = "/data/codecc_software"
            if (!File("$softwareRootPath/check.txt").exists()) {
                val currentUser = System.getProperty("user.name")
                if (currentUser == "root") {
                    ScriptUtils.execute(
                        script = "mkdir -p $softwareRootPath",
                        dir = File("/root/"),
                        runtimeVariables = emptyMap()
                    )
                    ScriptUtils.execute(
                        script = "mount -t nfs -o nolock xxxxx.com:/data/codecc_software $softwareRootPath",
                        dir = File("/root/"),
                        runtimeVariables = emptyMap()
                    )

                    if (!File("$softwareRootPath/check.txt").exists()) {
                        val setupInfo = """######################################################################################################## \
                            #                                                                                                      # \
                            #   check your env is :${commandParam.landunParam.ldEnvType} \
                            #   Please use root or admin account run blew step under your server! \
                            #   step-1: sudo mkdir -p $softwareRootPath \
                            #   step-2: sudo mount -t nfs -o nolock software.codecc.com:/data/codecc_software $softwareRootPath \
                            #                                                                                                      # \
                            ######################################################################################################## \
                            """
                        println(setupInfo)
                        throw CodeccUserConfigException("checkThirdEnv failed")
                    }
                }
            }

            thirdPartyEnvUpdate(softwareRootPath, commandParam)
        } else if (commandParam.landunParam.ldEnvType == "WIN_THIRD_PARTY") {
            val softwareRootPath = "C:/codecc_software"
            if (!File(softwareRootPath).exists()) {
                File(softwareRootPath).mkdirs()
            }
            if ((toolName in ToolConstants.COMPILE_TOOLS) && !checkPythonAll(softwareRootPath)) {
                throw CodeccUserConfigException("Codecc need install python3")
            }
            if (ToolConstants.COVERITY == toolName) {
                val coverityHome = "$softwareRootPath/cov-analysis-win64-${getConfig("COVERITY_NEW_VERSION")}"
                commandParam.coverityHomeBin = setupPath(softwareRootPath, coverityHome, "zip", commandParam.landunParam)
            }
            if (ToolConstants.KLOCWORK == toolName) {
                val klocWorkHome = "$softwareRootPath/kw-analysis-win64-${getConfig("KLOCWORK_NEW_VERSION")}"
                commandParam.klockWorkHomeBin = setupPath(softwareRootPath, klocWorkHome, "zip", commandParam.landunParam)
            }
            if (ToolConstants.CODEQL == toolName) {
                val codeqlHome = "$softwareRootPath/codeql-${getConfig("CODEQL_NEW_VERSION")}"
                commandParam.codeqlHomeBin = setupPath(softwareRootPath, codeqlHome, "zip", commandParam.landunParam)
            }
        } else if (commandParam.landunParam.ldEnvType == "MAC_THIRD_PARTY") {
            val rootPath = System.getenv("HOME") ?: "~"
            if ((toolName in ToolConstants.COMPILE_TOOLS) && !checkPython(rootPath)) {
                LogUtils.printLog("Codecc need python3")
                throw CodeccUserConfigException("Check env failed need python2")
            }
            val softwareRootPath = "$rootPath/codecc_software"
            if (ToolConstants.COVERITY == toolName) {
                val coverityHome = "$softwareRootPath/cov-analysis-macosx-${getConfig("COVERITY_NEW_VERSION")}"
                commandParam.coverityHomeBin = setupPath(softwareRootPath, coverityHome, "tar.gz", commandParam.landunParam)
                ScriptUtils.executeCodecc("chmod -R 755 ${commandParam.coverityHomeBin}", File(commandParam.landunParam.streamCodePath))
            }
            if (ToolConstants.KLOCWORK == toolName) {
                val klocWorkHome = "$softwareRootPath/kw-analysis-macosx-${getConfig("KLOCWORK_NEW_VERSION")}"
                commandParam.klockWorkHomeBin = setupPath(softwareRootPath, klocWorkHome, "tar.gz", commandParam.landunParam)
                ScriptUtils.executeCodecc("chmod -R 755 ${commandParam.klockWorkHomeBin}", File(commandParam.landunParam.streamCodePath))
            }
            if (ToolConstants.CODEQL == toolName) {
                val codeqlHome = "$softwareRootPath/codeql-${getConfig("CODEQL_NEW_VERSION")}"
                commandParam.codeqlHomeBin = setupPath(softwareRootPath, codeqlHome, "zip", commandParam.landunParam)
                ScriptUtils.executeCodecc("chmod -R 755 ${commandParam.codeqlHomeBin}", File(commandParam.landunParam.streamCodePath))
            }
        }
        if (CodeccEnvHelper.getOS() == OSType.MAC_OS) {
            val rootPath = System.getenv("HOME") ?: "~"
            if (!checkPython(rootPath)) {
                LogUtils.printLog("Codecc need python3")
                throw CodeccUserConfigException("Check env failed need python3")
            }
            val softwareRootPath = "$rootPath/codecc_software"
            if (ToolConstants.CLANG == toolName) {
                val clangHome = "$softwareRootPath/clang-${getConfig("CLANG_NEW_VERSION")}"
                commandParam.clangHomeBin = setupPath(softwareRootPath, clangHome, "zip", commandParam.landunParam)
                ScriptUtils.executeCodecc("chmod -R 755 ${commandParam.clangHomeBin}/..", File(commandParam.landunParam.streamCodePath))
            }
            if (ToolConstants.SPOTBUGS == toolName) {
                val spotBugsHome = "$softwareRootPath/spotbugs-${getConfig("SPOTBUGS_NEW_VERSION")}"
                commandParam.spotBugsHomeBin = setupPath(softwareRootPath, spotBugsHome, "zip", commandParam.landunParam)
                ScriptUtils.executeCodecc("chmod -R 755 ${commandParam.spotBugsHomeBin}", File(commandParam.landunParam.streamCodePath))
            }
        } else if (CodeccEnvHelper.getOS() == OSType.WINDOWS) {
            val softwareRootPath = "C:/codecc_software"
            if (!File(softwareRootPath).exists()) {
                File(softwareRootPath).mkdirs()
            }
            if ((toolName in ToolConstants.COMPILE_TOOLS) && !checkPythonAll(softwareRootPath)) {
                throw CodeccUserConfigException("Codecc need install python3")
            }
            if (ToolConstants.SPOTBUGS == toolName) {
                val spotBugsHome = "$softwareRootPath/spotbugs-${getConfig("SPOTBUGS_NEW_VERSION")}"
                commandParam.spotBugsHomeBin = setupPath(softwareRootPath, spotBugsHome, "zip", commandParam.landunParam)
            }
        }
    }

    private fun checkPython(home: String): Boolean {
        val cmd = "python3 --version"
        val output = try {
            ScriptUtils.execute(cmd, File(home)) // Python 3.x.x
        } catch (e: Throwable) {
            LogUtils.printLog("check python version failed: ${e.message}")
            return false
        }
        LogUtils.printLog("python version: $output")
        if (output.isNotBlank()) {
            val outputArray = output.trim().split(" ")
            if (outputArray[1].isNotBlank()) {
                val versionArray = outputArray[1].split(".")
                if (versionArray[0].isNotBlank()) {
                    if (versionArray[0].toInt() >= 3) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun checkPythonAll(home: String): Boolean {
        val cmd = "python --version"
        var output = try {
            ScriptUtils.execute(cmd, File(home)) // Python 3.x.x
        } catch (e: Throwable) {
            LogUtils.printLog("check python version failed: ${e.message}")
            ""
        }
        LogUtils.printLog("$cmd: $output")
        if (checkOutput(output)) return true

        val cmd3 = "python3 --version"
        output = try {
            ScriptUtils.execute(cmd3, File(home)) // Python 3.x.x
        } catch (e: Throwable) {
            LogUtils.printLog("check python version failed: ${e.message}")
            ""
        }
        LogUtils.printLog("$cmd3: $output")
        if (checkOutput(output)) return true

        return false
    }

    private fun checkOutput(output: String): Boolean {
        if (output.isNotBlank()) {
            val outputArray = output.trim().split(" ")
            if (outputArray[1].isNotBlank()) {
                val versionArray = outputArray[1].split(".")
                if (versionArray[0].isNotBlank()) {
                    if (versionArray[0].toInt() >= 3) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun setupPath(softwareRootPath: String, toolHome: String, suffix: String, landunParam: LandunParam): String {
        if (File(toolHome).exists() && File(toolHome).list()?.isNotEmpty() == true) {
            return "$toolHome/bin"
        }
        LogUtils.printLog("can not find the path $toolHome, download and install it...")
        // toolHome: /Users/johuang/codecc_software/cov-analysis-win64-2019.06
        // softwareRootPath: /Users/johuang/codecc_software
        File(toolHome).mkdirs()
        val fileName = toolHome.substringAfterLast("/") + "." + suffix
        val filePathName = "$toolHome.$suffix"
        if (!CodeccWeb.download(filePathName, fileName, "TOOL_CLIENT", landunParam)) {
            LogUtils.printLog("ERROR: the $fileName download failed! please contact the CodeCC")
            return ""
        }
        return if (File(filePathName).exists()) {
            if ("tar.gz" == suffix) {
                FileUtil.unzipTgzFile(filePathName, softwareRootPath)
            } else if ("zip" == suffix) {
                FileUtil.unzipFile(filePathName, softwareRootPath)
            }
            File(filePathName).delete()
            "$toolHome/bin"
        } else {
            ""
        }
    }

    private fun thirdPartyEnvUpdate(softwareRootPath: String, commandParam: CommandParam) {
        val py2BinPath = "$softwareRootPath/python2.7/bin"
        val py3BinPath = "$softwareRootPath/python3.5/bin"
        val pylint2BinPath = "$softwareRootPath/pylint_2.7"
        val pylint3BinPath = "$softwareRootPath/pylint_3.5"
        val nodeBinPath = "$softwareRootPath/node/bin"
        val jdkBinPath = "$softwareRootPath/jdk/bin"
        val goBinPath = "$softwareRootPath/go/bin"
        val goPath = "$softwareRootPath/go"
        val gometalinterBinPath = "$softwareRootPath/gometalinter/bin"
        val monoBinPath = "$softwareRootPath/mono/bin"
        val phpBinPath = "$softwareRootPath/php/bin"
        val gitBinPath = "$softwareRootPath/git/bin"
        val codeQLBinPath = "$softwareRootPath/codeql"
        val clangBinPath = "$softwareRootPath/clang"
        var spotBugsBinPath = "$softwareRootPath/spotbugs/bin"
        val pinpointBinPath = "$softwareRootPath/pinpoint"
        val coverityBinPath = "$softwareRootPath/cov-analysis-linux/bin"
        val klocworkBinPath = "$softwareRootPath/kw-analysis-linux/bin"
        if (File(codeQLBinPath).exists()) {
            println("third party env CODEQL_HOME_BIN: $codeQLBinPath")
            commandParam.codeqlHomeBin = codeQLBinPath
        }
        if (File(clangBinPath).exists()) {
            println("third party env CLANG_HOME_BIN: $clangBinPath")
            commandParam.clangHomeBin = clangBinPath
        }
        if (File(spotBugsBinPath).exists()) {
            println("third party env SPOTBUGS_HOME_BIN: $spotBugsBinPath")
            commandParam.spotBugsHomeBin = spotBugsBinPath
        }
        if (File(pinpointBinPath).exists()) {
            println("third party env PINPOINT_HOME_BIN: $pinpointBinPath")
            commandParam.pinpointHomeBin = pinpointBinPath
        }
        if (File(coverityBinPath).exists()) {
            println("third party env COVERITY_HOME_BIN: $coverityBinPath")
            commandParam.coverityHomeBin = coverityBinPath
        }
        if (File(klocworkBinPath).exists()) {
            println("third party env KLOCWORK_HOME_BIN: $klocworkBinPath")
            commandParam.klockWorkHomeBin = klocworkBinPath
        }
        if (File(py2BinPath).exists()) {
            println("third party env PY27_PATH: $py2BinPath")
            commandParam.py27Path = py2BinPath
        }
        if (File(py3BinPath).exists()) {
            println("third party env PY35_PATH: $py3BinPath")
            commandParam.py35Path = py3BinPath
        }
        if (File(pylint2BinPath).exists()) {
            println("third party env PY27_PYLINT_PATH: $pylint2BinPath")
            commandParam.py27PyLintPath = pylint2BinPath
        }
        if (File(pylint3BinPath).exists()) {
            println("third party env PY35_PYLINT_PATH: $pylint3BinPath")
            commandParam.py35PyLintPath = pylint3BinPath
        }

        val subPath = StringBuilder()
        subPath.append(propertiesInfo["SUB_PATH"])
        if (File(nodeBinPath).exists()) {
            subPath.append(File.pathSeparator + nodeBinPath)
        }
        if (File(jdkBinPath).exists()) {
            subPath.append(File.pathSeparator + jdkBinPath)
        }
        if (File(goBinPath).exists()) {
            subPath.append(File.pathSeparator + goBinPath)
        }
        if (File(gometalinterBinPath).exists()) {
            subPath.append(File.pathSeparator + gometalinterBinPath)
        }
        if (File(monoBinPath).exists()) {
            subPath.append(File.pathSeparator + monoBinPath)
        }
        if (File(phpBinPath).exists()) {
            subPath.append(File.pathSeparator + phpBinPath)
        }
        if (File(gitBinPath).exists()) {
            subPath.append(File.pathSeparator + gitBinPath)
        }
        if (File(goPath).exists()) {
            subPath.append(goPath)
        }
//        propertiesInfo["SUB_PATH"] = subPath.toString()
        commandParam.subPath = subPath.toString()
    }

    fun setupPropertiesInfo(commandParam: CommandParam): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (commandParam.subPath.isNotBlank()) {
            println("SUB_PATH=${System.getenv("PATH") + File.pathSeparator + commandParam.subPath}")
            setEnv("PATH", System.getenv("PATH") + File.pathSeparator + commandParam.subPath)
            result["PATH"] = System.getenv("PATH") + File.pathSeparator + commandParam.subPath
        }
        if (commandParam.py27Path.isNotBlank()) {
            setEnv("PY27_PATH", commandParam.py27Path)
            result["PY27_PATH"] = propertiesInfo["PY27_PATH"]!!
        }
        if (commandParam.py35Path.isNotBlank()) {
            setEnv("PY35_PATH", commandParam.py35Path)
            result["PY35_PATH"] = propertiesInfo["PY35_PATH"]!!
        }
        return result
    }

    private fun setEnv(key: String, value: String) {
        try {
            val env = System.getenv()
            val cl = env.javaClass
            val field = cl.getDeclaredField("m")
            field.isAccessible = true
            val writableEnv = field.get(env) as MutableMap<String, String>
            writableEnv[key] = value
            System.setProperty(key, value)
        } catch (e: Exception) {
            throw CodeccUserConfigException("Failed to set environment variable: ${e.message}")
        }
    }
}