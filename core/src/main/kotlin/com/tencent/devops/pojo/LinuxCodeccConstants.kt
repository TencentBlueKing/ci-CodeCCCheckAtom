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

package com.tencent.devops.pojo

import com.tencent.devops.utils.CodeccEnvHelper
import com.tencent.devops.utils.common.AgentEnv
import java.io.File

class LinuxCodeccConstants(bkWorkspace: String) {

    companion object {
        const val SVN_USER = "SVN_USER"
        const val SVN_PASSWORD = "SVN_PASSWORD"
        val COV_TOOLS = listOf("COVERITY", "KLOCWORK", "GOCILINT")
    }

    // 1. 公共构建机参数
    val CODECC_FOLDER = File("/data/bkdevops/apps/coverity")

    // 2. 第三方构建机相关参数
    private var THIRD_CODECC_FOLDER = bkWorkspace + File.separator + ".temp" + File.separator + "codecc"


    // 2.1 第三方构建机需要下载的文件
    val THIRD_PYTHON2_TAR_FILE = File(THIRD_CODECC_FOLDER, "Python-2.7.12.tgz")
    var THIRD_PYTHON3_TAR_FILE = File(THIRD_CODECC_FOLDER, "Python-3.5.1.tgz")
    val THIRD_COVERITY_FILE = File(
        THIRD_CODECC_FOLDER, if (CodeccEnvHelper.getOS() == OSType.LINUX) {
        if (AgentEnv.is32BitSystem()) "cov-analysis-linux-2018.03.tar.gz"
        else "cov-analysis-linux64-2018.03.tar.gz"
    } else {
        "cov-analysis-macosx-2018.06.tar.gz"
    })
    val THIRD_KLOCWORK_FILE = File(
        THIRD_CODECC_FOLDER, if (CodeccEnvHelper.getOS() == OSType.LINUX) {
        if (AgentEnv.is32BitSystem()) "kw-analysis-linux-12.3.tar.gz"
        else "kw-analysis-linux64-12.3.tar.gz"
    } else {
        "kw-analysis-macosx-12.3.tar.gz"
    })
    val THIRD_PYLINT2_FILE = File(THIRD_CODECC_FOLDER, "pylint_2.7.zip")
    val THIRD_PYLINT3_FILE = File(THIRD_CODECC_FOLDER, "pylint_3.5.zip")
    val THIRD_GOROOT_FILE = File(
        THIRD_CODECC_FOLDER, if (CodeccEnvHelper.getOS() == OSType.LINUX) {
        if (AgentEnv.is32BitSystem()) "go1.9.2.linux-386.tar.gz"
        else "go1.9.2.linux-amd64.tar.gz"
    } else {
        "go1.9.2.darwin-amd64.tar.gz"
    })
    val THIRD_JDK_FILE = if (CodeccEnvHelper.getOS() == OSType.LINUX) {
        if (AgentEnv.is32BitSystem()) File(THIRD_CODECC_FOLDER, "jdk-8u191-linux-x64.tar.gz")
        else File(THIRD_CODECC_FOLDER, "jdk-8u191-linux-i586.tar.gz")
    } else {
        File(THIRD_CODECC_FOLDER, "jdk-8u191-macosx-x64.dmg")
    }
    val THIRD_NODE_FILE = File(THIRD_CODECC_FOLDER, "node-v8.9.0-linux-x64_eslint.tar.gz")
    val THIRD_GOMETALINTER_FILE = if (CodeccEnvHelper.getOS() == OSType.MAC_OS) {
        File(THIRD_CODECC_FOLDER, "gometalinter_macos.zip")
    } else {
        File(THIRD_CODECC_FOLDER, "gometalinter_linux.zip")
    }
    val THIRD_CLANG_FILE = File(THIRD_CODECC_FOLDER, "clang-8.0.zip")

    val COVRITY_HOME = if (AgentEnv.isThirdParty()) {
        THIRD_COVERITY_FILE.canonicalPath.removeSuffix(".tar.gz")
    } else {
        File(CODECC_FOLDER, if (CodeccEnvHelper.getOS() == OSType.MAC_OS) "cov-analysis-macosx" else "cov-analysis-linux").canonicalPath
    }

    val KLOCWORK_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, THIRD_KLOCWORK_FILE.name)
    } else {
        File("/data/bkdevops/apps/codecc/kw-analysis/bin")
    }

    val CLANG_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, THIRD_CLANG_FILE.name)
    } else {
        File("/data/bkdevops/apps/codecc/clang-8.0/bin")
    }

    val PYTHON2_PATH = if (AgentEnv.isThirdParty()) {
        if (CodeccEnvHelper.getOS() == OSType.MAC_OS) {
            File("/usr/bin")
        } else {
            File(THIRD_CODECC_FOLDER, "Python-2.7.12/bin")
        }
    } else {
        if (CodeccEnvHelper.getOS() == OSType.MAC_OS) {
            File("/usr/bin")
        } else {
            File("/data/bkdevops/apps/python/2.7.12/bin")
        }
    }

    val PYTHON3_PATH = if (AgentEnv.isThirdParty()) {
        if (CodeccEnvHelper.getOS() == OSType.MAC_OS) {
            File("/data/bkdevops/apps/python/3.5/IDLE.app/Contents/MacOS")
        } else {
            File(THIRD_CODECC_FOLDER, "Python-3.5.1/bin")
        }
    } else {
        if (CodeccEnvHelper.getOS() == OSType.MAC_OS) {
            File("/data/bkdevops/apps/python/3.5/IDLE.app/Contents/MacOS")
        } else {
            File("/data/bkdevops/apps/python/3.5.1/bin")
        }
    }

    val JDK_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "jdk1.8.0_191/bin").canonicalPath
    } else {
        if (CodeccEnvHelper.getOS() == OSType.MAC_OS) {
            "/data/soda/apps/jdk/1.8.0_161/Contents/Home/bin"
        } else {
            "/data/bkdevops/apps/jdk/1.8.0_161/bin"
        }
    }
    val NODE_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "node-v8.9.0-linux-x64_eslint/bin").canonicalPath
    } else {
        "/data/bkdevops/apps/codecc/node-v8.9.0-linux-x64/bin"
    }
    val GOMETALINTER_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "gometalinter/bin").canonicalPath
    } else {
        "/data/bkdevops/apps/codecc/gometalinter/bin"
    }
    val PYLINT2_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "mypylint_2.7").canonicalPath
    } else {
        "/data/bkdevops/apps/codecc/pylint_2.7"
    }
    val PYLINT3_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "mypylint_3.5").canonicalPath
    } else {
        "/data/bkdevops/apps/codecc/pylint_3.5"
    }
    val GOROOT_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "go/bin").canonicalPath
    } else {
        "/data/bkdevops/apps/codecc/go/bin"
    }
    val STYLE_TOOL_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "").canonicalPath // 暂时不支持第三方机器
    } else {
        "/data/bkdevops/apps/codecc/mono/bin"
    }
    val PHPCS_TOOL_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "").canonicalPath // 暂时不支持第三方机器
    } else {
        "/data/bkdevops/apps/codecc/php/bin"
    }
    val GOROOT_PATH_12 = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "golang1.12.9/bin").canonicalPath
    } else {
        "/data/bkdevops/apps/codecc/golang1.12.9/bin"
    }
    val GO_CI_LINT_PATH = if (AgentEnv.isThirdParty()) {
        File(THIRD_CODECC_FOLDER, "gocilint1.17.1").canonicalPath
    } else {
        "/data/bkdevops/apps/codecc/gocilint1.17.1"
    }

    fun getCovPyFile(): File {
        val covPyFile = when {
            AgentEnv.isDev() -> "build_external_dev.py"
            AgentEnv.isTest() -> "build_external_test.py"
            else -> "build_external_prod.py"
        }
        return if (AgentEnv.isThirdParty()) File(THIRD_CODECC_FOLDER, covPyFile)
        else File(CODECC_FOLDER, covPyFile)
    }

    fun getToolPyFile(): File {
        val toolPyFile = when {
            AgentEnv.isDev() -> "build_tool_external_dev.py"
            AgentEnv.isTest() -> "build_tool_external_test.py"
            else -> "build_tool_external_prod.py"
        }
        return if (AgentEnv.isThirdParty()) File(THIRD_CODECC_FOLDER, toolPyFile)
        else File(CODECC_FOLDER, toolPyFile)
    }

    init {
        THIRD_CODECC_FOLDER = bkWorkspace + File.separator + ".temp" + File.separator + "codecc"
    }
}