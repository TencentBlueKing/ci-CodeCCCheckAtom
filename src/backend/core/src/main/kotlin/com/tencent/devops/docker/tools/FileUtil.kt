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

package com.tencent.devops.docker.tools

import com.tencent.devops.pojo.exception.ErrorCode
import com.tencent.devops.pojo.exception.plugin.CodeCCPluginException
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.*
import java.util.zip.*


object FileUtil {

    fun convertTempFile(inputStream: InputStream): File {
        val logo = Files.createTempFile("default_", ".png").toFile()

        logo.outputStream().use {
            inputStream.copyTo(it)
        }

        return logo
    }

    /**
     * 获取文件MD5值
     * @param file 文件对象
     * @return 文件MD5值
     */
    fun getMD5(file: File): String {
        if (!file.exists()) return ""
        return DigestUtils.md5Hex(file.inputStream())
    }

    /**
     * 获取文件内容的MD5值
     * @param content 文件内容
     * @return 文件MD5值
     */
    fun getMD5(content: String): String {
        return DigestUtils.md5Hex(content)
    }

    /**
     * 获取文件内容的MD5值
     * @param bytes 文件字节
     * @return 文件MD5值
     */
    fun getMD5(bytes: ByteArray): String {
        return DigestUtils.md5Hex(bytes)
    }

    /**
     * zip文件到当前路径
     * @param file 文件对象
     * @return zip文件
     */
    fun zipToCurrentPath(file: File): File {
        val dest = File(file.canonicalPath + ".zip")
        val sourcePath = Paths.get(file.canonicalPath)
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            val bufSize = 4096
            val buf = ByteArray(bufSize)
            file.walk().filter { return@filter it.isFile }.forEach {
                val relativePath = sourcePath.relativize(Paths.get(it.canonicalPath)).toString()
                zos.putNextEntry(ZipEntry(relativePath))
                FileInputStream(it).use {
                    var len = it.read(buf)
                    while (len != -1) {
                        zos.write(buf, 0, len)
                        len = it.read(buf)
                    }
                }
                zos.closeEntry()
            }
        }
        return dest
    }

    fun zipFile(srcfile: String): String {
        val out = ZipOutputStream(BufferedOutputStream(FileOutputStream("${srcfile}.zip")))

        val fi = FileInputStream(srcfile)
        val origin = BufferedInputStream(fi)
        try {
            val entry = ZipEntry(srcfile.substring(srcfile.lastIndexOf("/")))
            out.putNextEntry(entry)
            origin.copyTo(out, 4096)
        } finally {
            try {
                origin.close()
            } catch (e: IOException) {
            }
            try {
                out.close()
            } catch (e: IOException) {
            }
        }

        return "${srcfile}.zip"
    }

    fun matchFiles(workspace: File, filePath: String): List<File> {
        // 斜杠开头的，绝对路径
        val absPath = filePath.startsWith("/") || (filePath[0].isLetter() && filePath[1] == ':')

        val fileList: List<File>
        // 文件夹返回所有文件
        if (filePath.endsWith("/")) {
            // 绝对路径
            fileList = if (absPath) File(filePath).listFiles().filter { return@filter it.isFile }.toList()
            else File(workspace, filePath).listFiles().filter { return@filter it.isFile }.toList()
        } else {
            // 相对路径
            // get start path
            val file = File(filePath)
            val startPath = if (file.parent.isNullOrBlank()) "." else file.parent
            val regexPath = file.name

            // return result
            val pattern = Pattern.compile(transfer(regexPath))
            val startFile = if (absPath) File(startPath) else File(workspace, startPath)
            val path = Paths.get(startFile.canonicalPath)
            fileList = startFile.listFiles()?.filter {
                val rePath = path.relativize(Paths.get(it.canonicalPath)).toString()
                it.isFile && pattern.matcher(rePath).matches()
            }?.toList() ?: listOf()
        }
        val resultList = mutableListOf<File>()
        fileList.forEach { f ->
            // 文件名称不允许带有空格
            if (!f.name.contains(" ")) {
                resultList.add(f)
            }
        }
        return resultList
    }

    fun unzipTgzFile(tgzFile: String, destDir: String = "./") {
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

    fun unzipTxzFile(txzFile: String, destDir: String = "./") {
        val blockSize = 4096
        val inputStream = TarArchiveInputStream(XZCompressorInputStream(File(txzFile).inputStream()), blockSize)
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

    fun unzipFile(zipFile: String, destDir: String = "./") {
        val blockSize = 4096
        ZipArchiveInputStream(BufferedInputStream(FileInputStream(File(zipFile)), blockSize)).use { inputStream ->
            while (true) {
                val entry = inputStream.nextZipEntry ?: break
                if (entry.isDirectory) { // 是目录
                    val dir = File(destDir, entry.name)
                    if (!dir.exists()) dir.mkdirs()
                } else { // 是文件
                    val parentDir = File(File(destDir, entry.name).parent)
                    if (!parentDir.exists()) parentDir.mkdirs()
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

    fun getSHA1(file: File): String {
        if (!file.exists()) return ""
        return DigestUtils.sha1Hex(file.inputStream())
    }

    private fun transfer(regexPath: String): String {
        var resultPath = regexPath
        resultPath = resultPath.replace(".", "\\.")
        resultPath = resultPath.replace("*", ".*")
        return resultPath
    }

    /**
     * 加密压缩目录或文件
     * @param needZipPath 需压缩的路径（指定文件或目录）
     * @param toFilePath  zip压缩后的存放路径
     * @param password    设置解压保护密码（可选）
     * @param overwrite   默认覆盖已存在zip的文件
     * @return zip文件完整路径
     */
    fun zipWithPassword(
        needZipPath: String,
        toFilePath: String,
        zipFileName: String? = null,
        password: String? = null,
        overwrite: Boolean = false
    ): String? {
        val pathToZipFile: String
        var zipFile: ZipFile? = null
        try {
            val needZipPathFile = File(needZipPath)
            if (!needZipPathFile.exists()) {
                throw CodeCCPluginException(ErrorCode.PLUGIN_FILE_NOT_FOUNT, "path is not found: $needZipPath")
            }
            // 如果zip文件名为空则以needZipPath最后一级路径为文件名
            pathToZipFile = "$toFilePath/${(zipFileName ?: needZipPath.split(File.separator).last().plus(".zip"))}"
            LogUtils.printLog("genera zip file name: $pathToZipFile")

            // 生成的压缩文件（密码可选填）
            zipFile = ZipFile(pathToZipFile, password?.toCharArray())
            if (zipFile.file.exists() && overwrite) {
                throw CodeCCPluginException(ErrorCode.PLUGIN_FILE_EXISTS, "path is exist: $pathToZipFile")
            }

            val parameters = ZipParameters()
            // 压缩方式
            parameters.compressionMethod = CompressionMethod.DEFLATE
            parameters.compressionLevel = CompressionLevel.NORMAL
            // 是否设置加密文件
            parameters.isEncryptFiles = true
            parameters.encryptionMethod = EncryptionMethod.AES
            parameters.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256

            // 要打包的路径
            val listFiles: Array<File> = if (needZipPathFile.isDirectory) {
                needZipPathFile.listFiles()!!
            } else {
                arrayOf(needZipPathFile)
            }

            listFiles.forEach { f ->
                LogUtils.printLog("zip file add: ${f.path}")
                if (f.isDirectory) {
                    zipFile.addFolder(f, parameters)
                } else {
                    zipFile.addFile(f, parameters)
                }
            }
            LogUtils.printLog("total file: ${listFiles.size}")
        } catch (e: Exception) {
            LogUtils.printErrorLog("executed zipWithPassword failed! ${e.message}", e)
            return null
        } finally {
            zipFile?.close()
        }

        return pathToZipFile
    }

    /**
     * 解压带密码的zip文件
     * @param zipFilePath     zip文件路径
     * @param destinationPath 解压目标路径
     * @param password        密码（可选）
     */
    fun unzipWithPassword(
        zipFilePath: String,
        destinationPath: String,
        password: String? = null
    ) {
        var zipFile: ZipFile? = null
        try {
            zipFile = ZipFile(zipFilePath, password?.toCharArray())
            zipFile.extractAll(destinationPath)
            LogUtils.printLog("unzipWithPassword success! $zipFilePath unzip to $destinationPath")
        } catch (e: Exception) {
            LogUtils.printErrorLog("unzipWithPassword error: ${e.message}", e)
        } finally {
            zipFile?.close()
        }
    }
}