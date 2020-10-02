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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.bk.devops.plugin.common.OS
import com.tencent.devops.docker.pojo.LandunParam
import com.tencent.devops.docker.utils.CodeccWeb
import com.tencent.devops.pojo.BuildType
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.CoverityProjectType
import com.tencent.devops.pojo.LinuxCodeccConstants
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.ProjectLanguage
import com.tencent.devops.pojo.WindowsCodeccConstants
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import com.tencent.devops.utils.common.AgentEnv
import com.tencent.devops.utils.common.AtomUtils

object CodeccParamsHelper {

    fun addCommonParams(map: MutableMap<String, String>, codeccExecuteConfig: CodeccExecuteConfig) {
        val param = codeccExecuteConfig.atomContext.param
        val buildId = param.pipelineBuildId
        val repoScmType = CodeccRepoHelper.getScmType(codeccExecuteConfig.repos)

//        list.add(param.codeCCTaskName ?: throw RuntimeException("codecc task name is empty"))
//        list.add("-DLANDUN_BUILDID=$buildId")
        map["LANDUN_BUILDID"] = buildId
//        list.add("-DCERT_TYPE=${CodeccRepoHelper.getCertType(codeccExecuteConfig.repos)}")
        map["CERT_TYPE"] = CodeccRepoHelper.getCertType(codeccExecuteConfig.repos)
//        list.add("-DSCM_TYPE=$repoScmType")
        map["SCM_TYPE"] = repoScmType

        val svnUerPassPair = codeccExecuteConfig.repos.firstOrNull()?.svnUerPassPair
        if (svnUerPassPair != null) {
//            list.add("-DSVN_USER=${svnUerPassPair.first}")
            map["SVN_USER"] = svnUerPassPair.first
//            list.add("-DSVN_PASSWORD='${svnUerPassPair.second}'")
            map["SVN_PASSWORD"] = svnUerPassPair.second
        }

        if (repoScmType == "svn") {
            if (svnUerPassPair == null) {
//                list.add("-DSCM_SSH_ACCESS=$repoScmType")
                map["SCM_SSH_ACCESS"] = repoScmType
            }
        }

//        list.add("-DREPO_URL_MAP='${getRepoUrlMap(codeccExecuteConfig)}'")
        map["REPO_URL_MAP"] = getRepoUrlMap(codeccExecuteConfig)
//        list.add("-DREPO_RELPATH_MAP='${getRepoRealPathMap(codeccExecuteConfig)}'")
        map["REPO_RELPATH_MAP"] = getRepoRealPathMap(codeccExecuteConfig)
//        list.add("-DREPO_SCM_RELPATH_MAP='${getRepoScmRelPathMap(codeccExecuteConfig)}'")
        map["REPO_SCM_RELPATH_MAP"] = getRepoScmRelPathMap(codeccExecuteConfig)
//        list.add("-DSUB_CODE_PATH_LIST=${parseStringToList(param.path).joinToString(",")}")
        map["SUB_CODE_PATH_LIST"] = AtomUtils.parseStringToList(param.path).joinToString(",")
//        list.add("-DLD_ENV_TYPE=${getEnvType()}")
        map["LD_ENV_TYPE"] = getEnvType()

        // 构建机信息
        val agentId = SdkEnv.getSdkHeader()["X-DEVOPS-AGENT-ID"] ?: ""
        val agentSecretKey = SdkEnv.getSdkHeader()["X-DEVOPS-AGENT-SECRET-KEY"] ?: ""
        if (AgentEnv.isThirdParty()) {
            println("[初始化] 检测到这是第三方构建机")
            map["DEVOPS_PROJECT_ID"] = param.projectName
            map["DEVOPS_BUILD_TYPE"] = BuildType.AGENT.name
            map["DEVOPS_AGENT_ID"] = agentId
            map["DEVOPS_AGENT_SECRET_KEY"] = agentSecretKey
            map["DEVOPS_AGENT_VM_SID"] = SdkEnv.getVmSeqId()

        } else if (AgentEnv.getBuildType() == BuildType.DOCKER.name) {
            println("[初始化] 检测到这是docker公共构建机")
            map["DEVOPS_PROJECT_ID"] = param.projectName
            map["DEVOPS_BUILD_TYPE"] = BuildType.DOCKER.name
            map["DEVOPS_AGENT_ID"] = agentId
            map["DEVOPS_AGENT_SECRET_KEY"] = agentSecretKey
            map["DEVOPS_AGENT_VM_SID"] = ""
        } else if (AgentEnv.getBuildType() == OS.MACOS.name) {
            println("检测到这是Macos 公共构建机")
            map["DEVOPS_PROJECT_ID"] = param.projectName
            map["DEVOPS_BUILD_TYPE"] = BuildType.MACOS.name
            map["DEVOPS_AGENT_ID"] = agentId
            map["DEVOPS_AGENT_SECRET_KEY"] = agentSecretKey
            map["DEVOPS_AGENT_VM_SID"] = ""
        }

//        list.add("-DDEVOPS_PROJECT_ID=${param.projectName}")
//        list.add("-DDEVOPS_PIPELINE_ID=${param.pipelineId}")
//        list.add("-DDEVOPS_VMSEQ_ID=${SdkEnv.getVmSeqId()}")
        map["DEVOPS_PROJECT_ID"] = param.projectName
        map["DEVOPS_PIPELINE_ID"] = param.pipelineId
        map["DEVOPS_VMSEQ_ID"] = SdkEnv.getVmSeqId()
    }

    fun addCommonParams(list: MutableList<String>, codeccExecuteConfig: CodeccExecuteConfig) {
        val param = codeccExecuteConfig.atomContext.param
        val buildId = param.pipelineBuildId
        val repoScmType = CodeccRepoHelper.getScmType(codeccExecuteConfig.repos)

        list.add(param.codeCCTaskName ?: throw CodeccUserConfigException("codecc task name is empty"))
        list.add("-DLANDUN_BUILDID=$buildId")
        list.add("-DCERT_TYPE=${CodeccRepoHelper.getCertType(codeccExecuteConfig.repos)}")
        list.add("-DSCM_TYPE=$repoScmType")

        val svnUerPassPair = codeccExecuteConfig.repos.firstOrNull()?.svnUerPassPair
        if (svnUerPassPair != null) {
            list.add("-DSVN_USER=${svnUerPassPair.first}")
            list.add("-DSVN_PASSWORD='${svnUerPassPair.second}'")
        }

        if (repoScmType == "svn") {
            if (svnUerPassPair == null) list.add("-DSCM_SSH_ACCESS=$repoScmType")
        }

        list.add("-DREPO_URL_MAP='${getRepoUrlMap(codeccExecuteConfig)}'")
        list.add("-DREPO_RELPATH_MAP='${getRepoRealPathMap(codeccExecuteConfig)}'")
        list.add("-DREPO_SCM_RELPATH_MAP='${getRepoScmRelPathMap(codeccExecuteConfig)}'")
        if (param.path.isNullOrBlank()) {
            list.add("-DSUB_CODE_PATH_LIST=")
        } else {
            list.add("-DSUB_CODE_PATH_LIST='${param.path}'")
        }
        list.add("-DLD_ENV_TYPE=${getEnvType()}")

        val agentId = SdkEnv.getSdkHeader()["X-DEVOPS-AGENT-ID"] ?: ""
        val agentSecretKey = SdkEnv.getSdkHeader()["X-DEVOPS-AGENT-SECRET-KEY"] ?: ""
        // 构建机信息
        if (AgentEnv.isThirdParty()) {
            println("检测到这是第三方构建机")
            list.add("-DDEVOPS_PROJECT_ID=${param.projectName}")
            list.add("-DDEVOPS_BUILD_TYPE=${BuildType.AGENT.name}")
            list.add("-DDEVOPS_AGENT_ID=$agentId")
            list.add("-DDEVOPS_AGENT_SECRET_KEY=$agentSecretKey")
            list.add("-DDEVOPS_AGENT_VM_SID=${SdkEnv.getVmSeqId()}")
        } else if (AgentEnv.getBuildType() == BuildType.DOCKER.name) {
            println("检测到这是docker公共构建机")
            list.add("-DDEVOPS_PROJECT_ID=${param.projectName}")
            list.add("-DDEVOPS_BUILD_TYPE=${BuildType.DOCKER.name}")
            list.add("-DDEVOPS_AGENT_ID=$agentId")
            list.add("-DDEVOPS_AGENT_SECRET_KEY=$agentSecretKey")
            list.add("-DDEVOPS_AGENT_VM_SID=")
        }

        list.add("-DDEVOPS_PROJECT_ID=${param.projectName}")
        list.add("-DDEVOPS_PIPELINE_ID=${param.pipelineId}")
        list.add("-DDEVOPS_VMSEQ_ID=${SdkEnv.getVmSeqId()}")
    }

    fun getRepoScmRelPathMap(codeccExecuteConfig: CodeccExecuteConfig): String {
        return toMapString(
            codeccExecuteConfig.repos.map {
                it.repoHashId to it.relativePath
            }.toMap()
        )
    }

    private fun getRepoRealPathMap(codeccExecuteConfig: CodeccExecuteConfig): String {
        return toMapString(
            codeccExecuteConfig.repos.map {
                it.repoHashId to it.relPath
            }.toMap()
        )
    }

    fun getRepoUrlMap(codeccExecuteConfig: CodeccExecuteConfig): String {
        return toMapString(
            codeccExecuteConfig.repos.map {
                it.repoHashId to it.url.removeSuffix("/")
            }.toMap()
        )
    }

    fun transferStrToMap(json: String): Map<String, String> {
        return json.trim().removePrefix("{").removeSuffix("}").split(",").map { pairStr ->
            val pair = pairStr.split(":")
            val key = pair.first().trim().removePrefix("\\").removePrefix("\"").removeSuffix("\"").removeSuffix("\\")
            val value = pairStr.trim().removePrefix(pair.first()).trim().removePrefix(":").trim()
                .removePrefix("\\").removePrefix("\"").removeSuffix("\"").removeSuffix("\\")
            key to value
        }.toMap()
    }

    private fun toMapString(toMap: Map<String, String>): String {
        val point = if (CodeccEnvHelper.getOS() == OSType.WINDOWS) "\\\""
        else "\""

        val sb = StringBuilder()
        sb.append("{")
        toMap.entries.forEach {
            sb.append("$point${it.key}$point:$point${it.value}$point")
            sb.append(",")
        }
        if (toMap.entries.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
        sb.append("}")
        return sb.toString()
    }

    fun getEnvType(): String {
        // 第三方机器
        return if (AgentEnv.isThirdParty()) {
            when (CodeccEnvHelper.getOS()) {
                OSType.MAC_OS -> "MAC_THIRD_PARTY"
                OSType.WINDOWS -> "WIN_THIRD_PARTY"
                else -> "LINUX_THIRD_PARTY"
            }
        } else {
            "PUBLIC"
        }
    }

    private val map = mapOf(
        ProjectLanguage.C.name to CoverityProjectType.COMPILE,
        ProjectLanguage.C_PLUS_PLUSH.name to CoverityProjectType.COMPILE,
        ProjectLanguage.C_CPP.name to CoverityProjectType.COMPILE,
        ProjectLanguage.OBJECTIVE_C.name to CoverityProjectType.COMPILE,
        ProjectLanguage.OC.name to CoverityProjectType.COMPILE,
        ProjectLanguage.C_SHARP.name to CoverityProjectType.COMPILE,
        ProjectLanguage.JAVA.name to CoverityProjectType.COMPILE,
        ProjectLanguage.PYTHON.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.JAVASCRIPT.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.JS.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.PHP.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.RUBY.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.LUA.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.GOLANG.name to CoverityProjectType.COMBINE,
        ProjectLanguage.SWIFT.name to CoverityProjectType.COMBINE,
        ProjectLanguage.TYPESCRIPT.name to CoverityProjectType.UN_COMPILE,
        ProjectLanguage.KOTLIN.name to CoverityProjectType.COMPILE,
        ProjectLanguage.OTHERS.name to CoverityProjectType.UN_COMPILE
    )

    /**
     * C/C++                	编译型
     * Objective-C/C++			编译型
     * C#						编译型
     * Java 					编译型
     * Python					非编译型
     * JavaScript				非编译型
     * PHP						非编译型
     * Ruby					    非编译型
     */

    fun getProjectType(languages: List<String>?): CoverityProjectType {
        // 此处为了新引擎兼容，新引擎传递的参数是真实类型json，而不是单纯的String
        // 而CodeCC是用x,y,z这种方式对待List，所以在这强转并写入params中供CodeCC读取

        if (languages == null || languages.isEmpty()) {
            return CoverityProjectType.UN_COMPILE
        }

        var type = map[languages[0]]

        languages.forEach {
            val currentType = map[it]
            if (type != null) {
                if (currentType != null && type != currentType) {
                    return CoverityProjectType.COMBINE
                }
            } else {
                type = currentType
            }
        }

        return type ?: CoverityProjectType.UN_COMPILE
    }

    fun getPython3Path(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.PYTHON3_PATH.canonicalPath
        } else {
            WindowsCodeccConstants.WINDOWS_PYTHON3_PATH.canonicalPath
        }
    }

    fun getPython2Path(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.PYTHON2_PATH.canonicalPath
        } else {
            WindowsCodeccConstants.WINDOWS_PYTHON2_PATH.canonicalPath
        }
    }

    fun getCovToolPath(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.COVRITY_HOME
        } else {
            WindowsCodeccConstants.WINDOWS_COVRITY_HOME.canonicalPath
        }
    }

    fun getKlocToolPath(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.KLOCWORK_PATH.canonicalPath
        } else {
            WindowsCodeccConstants.WINDOWS_KLOCWORK_HOME.canonicalPath
        }
    }

    fun getClangToolPath(constants: LinuxCodeccConstants): String {
        return constants.CLANG_PATH.canonicalPath
    }

    fun getPyLint2Path(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.PYLINT2_PATH
        } else {
            WindowsCodeccConstants.WINDOWS_PYLINT2_PATH.canonicalPath
        }
    }

    fun getPyLint3Path(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.PYLINT3_PATH
        } else {
            WindowsCodeccConstants.WINDOWS_PYLINT3_PATH.canonicalPath
        }
    }

    fun getGoRootPath(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.GOROOT_PATH
        } else {
            WindowsCodeccConstants.WINDOWS_GOROOT_PATH.canonicalPath
        }
    }

    fun getJdkPath(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.JDK_PATH
        } else {
            WindowsCodeccConstants.WINDOWS_JDK_PATH.canonicalPath
        }
    }

    fun getNodePath(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.NODE_PATH
        } else {
            WindowsCodeccConstants.WINDOWS_NODE_PATH.canonicalPath
        }
    }

    fun getGoMetaLinterPath(constants: LinuxCodeccConstants): String {
        return if (CodeccEnvHelper.getOS() != OSType.WINDOWS) {
            constants.GOMETALINTER_PATH
        } else {
            WindowsCodeccConstants.WINDOWS_GOMETALINTER_PATH.canonicalPath
        }
    }

    fun getCodeccExecuteConfig(atomContext: AtomContext<CodeccCheckAtomParamV3>): CodeccExecuteConfig {
        val variable = CodeccEnvHelper.getVariable()
        val repos = CodeccRepoHelper.getCodeccRepos(variable)
        val coverityConfig = CodeccExecuteConfig(
            scriptType = CodeccEnvHelper.getScriptType(),
            repos = repos,
            atomContext = atomContext,
            tools = listOf(),
            variable = variable,
            filterTools = AtomUtils.parseStringToList(atomContext.param.filterTools)
        )
        println("[初始化] buildVariables coverityConfig: $coverityConfig")
        return coverityConfig
    }
}
