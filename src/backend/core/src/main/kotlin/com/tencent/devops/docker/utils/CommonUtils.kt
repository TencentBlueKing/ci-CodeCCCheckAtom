package com.tencent.devops.docker.utils

import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import com.tencent.devops.utils.CodeccEnvHelper
import org.slf4j.LoggerFactory
import java.io.File

object CommonUtils {

    private val logger = LoggerFactory.getLogger(CommonUtils::class.java)

    private const val dockerHubUrl = "https://index.docker.io/v1/"

//    fun normalizeImageName(imageNameStr: String): String {
//        val (url, name, tag) = parseImage(imageNameStr)
//        return when (url) {
//            dockerHubUrl -> "$name:$tag"
//            else -> "$url/$name:$tag"
//        }
//    }

    fun parseImage(imageNameInput: String): Triple<String, String, String> {
        val imageNameStr = imageNameInput.removePrefix("http://").removePrefix("https://")
        val arry = imageNameStr.split(":")
        if (arry.size == 1) {
            val str = imageNameStr.split("/")
            return if (str.size == 1) {
                Triple(dockerHubUrl, imageNameStr, "latest")
            } else {
                Triple(str[0], imageNameStr.substringAfter(str[0] + "/"), "latest")
            }
        } else if (arry.size == 2) {
            val str = imageNameStr.split("/")
            when {
                str.size == 1 -> return Triple(dockerHubUrl, arry[0], arry[1])
                str.size >= 2 -> return if (str[0].contains(":")) {
                    Triple(str[0], imageNameStr.substringAfter(str[0] + "/"), "latest")
                } else {
                    if (str.last().contains(":")) {
                        val nameTag = str.last().split(":")
                        Triple(str[0], imageNameStr.substringAfter(str[0] + "/").substringBefore(":" + nameTag[1]), nameTag[1])
                    } else {
                        Triple(str[0], str.last(), "latest")
                    }
                }
                else -> {
                    throw CodeccUserConfigException("image name invalid: $imageNameStr")
                }
            }
        } else if (arry.size == 3) {
            val str = imageNameStr.split("/")
            if (str.size >= 2) {
                val tail = imageNameStr.removePrefix(str[0] + "/")
                val nameAndTag = tail.split(":")
                if (nameAndTag.size != 2) {
                    throw CodeccUserConfigException("image name invalid: $imageNameStr")
                }
                return Triple(str[0], nameAndTag[0], nameAndTag[1])
            } else {
                throw CodeccUserConfigException("image name invalid: $imageNameStr")
            }
        } else {
            throw CodeccUserConfigException("image name invalid: $imageNameStr")
        }
    }

    fun changePathToDocker(path: String): String {
        return if (path.isNotBlank() && CodeccEnvHelper.getOS() == OSType.WINDOWS) {
            if (path.contains(":\\") || path.contains(":/")) {
                "/" + path.replace("\\", "/").replace(":", "")
            } else {
                path.replace("\\", "/")
            }
        } else {
            path
        }
    }

    fun changePathToWindows(path: String): String {
        return if (path.isNotBlank() && CodeccEnvHelper.getOS() == OSType.WINDOWS) {
            LogUtils.printDebugLog("path is: $path")
            var tmp = path
            while (tmp.startsWith("/")) {
                tmp = tmp.removePrefix("/")
            }

            tmp = tmp.replaceFirst("/", ":/")
            LogUtils.printDebugLog("change path to $tmp")
            tmp
        } else {
            path
        }
    }
}