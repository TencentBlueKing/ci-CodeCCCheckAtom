package com.tencent.devops.scm

import com.tencent.devops.docker.tools.LogUtils
import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths

object Utils {

    /**
     * @Description ["判断文件是否为二进制"]
     * @date 2021/9/7
     */
    fun isBinary(filePath: File): Boolean {
        var isBinary = false
        try {
            val fin = FileInputStream(filePath)
            val len = filePath.length()
            for (j in len.rangeTo(len)) {
                val t = fin.read()
                //通过判断文件内容是否含有ascii码值小于32，且不等于9（tab）10（\n）13(\r),则判定为二进制文件
                if (t < 32 && t != 9 && t != 10 && t != 13) {
                    isBinary = true
                    break
                }
            }
        } catch (e: Exception) {
            LogUtils.printLog("the filePath $filePath check binary failed, please review it")
            return false
        }
        return isBinary
    }

    /**
     * @Description ["查找git根路径"]
     * @date 2021/9/7
     */
    fun findGitDirPath(fileFolderPath: String): String {
        var gitDir = fileFolderPath
        while (true) {
            gitDir += "/.git"
            if (File(gitDir).isDirectory) {
                break
            } else if (File(gitDir).isFile) {
                break
            } else {
                val upperFolder = Paths.get(gitDir).parent.toAbsolutePath().toString()
                gitDir = Paths.get(upperFolder).parent.toAbsolutePath().toString()
            }
        }
        return Paths.get(gitDir).parent.toAbsolutePath().toString()
    }

    /**
     * @Description ["检查路径SCM类型"]
     * @date 2021/9/7
     */
    fun checkScmDirPath(fileFolderPath: String): String {
        var gitDir = fileFolderPath
        var scmType = ""
        while (true) {
            gitDir += "/.git"
            if (File(gitDir).isDirectory) {
                scmType = "git"
                break
            } else if (File(gitDir).isFile) {
                scmType = "git"
                break
            } else {
                val upperFolder = Paths.get(gitDir).parent.toAbsolutePath().toString()
                if (upperFolder.equals("/") || Regex(".:.").matches(upperFolder)){
                    break
                }
                gitDir = Paths.get(upperFolder).parent.toAbsolutePath().toString()
            }
        }

        if (scmType != ""){
            return scmType
        }

        var svnDir = fileFolderPath
        while (true) {
            svnDir += "/.svn"
            if (File(svnDir).isDirectory) {
                scmType = "svn"
                break
            } else if (File(svnDir).isFile) {
                scmType = "svn"
                break
            } else {
                val upperFolder = Paths.get(svnDir).parent.toAbsolutePath().toString()
                if (upperFolder.equals("/") || Regex(".:.").matches(upperFolder)){
                    break
                }
                svnDir = Paths.get(upperFolder).parent.toAbsolutePath().toString()
            }
        }

        if (scmType != ""){
            return scmType
        }

        var perforceDir = fileFolderPath
        perforceDir += "/.p4config.cfg"
        LogUtils.printLog("perforceDir $perforceDir")
        val file = File(perforceDir)
        if (file.exists()) {
            scmType = "perforce"
        }

        return scmType
    }
}
