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

package com.tencent.devops.utils.common

import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.devops.pojo.BuildType
import com.tencent.devops.pojo.OSType
import com.tencent.devops.pojo.env.Env
import com.tencent.devops.pojo.env.LogMode
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.*

object AgentEnv {

    private val logger = LoggerFactory.getLogger(AgentEnv::class.java)

    private const val PROJECT_ID = "devops.project.id"
    private const val DOCKER_PROJECT_ID = "devops_project_id"
    private const val AGENT_ID = "devops.agent.id"
    private const val DOCKER_AGENT_ID = "devops_agent_id"
    private const val AGENT_SECRET_KEY = "devops.agent.secret.key"
    private const val DOCKER_AGENT_SECRET_KEY = "devops_agent_secret_key"
    private const val AGENT_GATEWAY = "landun.gateway"
    private const val DOCKER_GATEWAY = "devops_gateway"
    private const val AGENT_ENV = "landun.env"
    private const val AGENT_LOG_SAVE_MODE = "devops_log_save_mode"
    private const val BUILD_TYPE = "build.type"

    private var projectId: String? = null
    private var agentId: String? = null
    private var secretKey: String? = null
    private var gateway: String? = null
    private var os: OSType? = null
    private var env: Env? = null
    private var logMode: LogMode? = null

    private var property: Properties? = null

    private val propertyFile = File(getLandun(), ".agent.properties")

    fun isProd() = true

    fun isTest() = false

    fun isDev() = false

    fun getLandun() = File(".")

    private fun getProperty(prop: String): String? {
        val buildType = getBuildType()
        if (buildType == BuildType.DOCKER.name) {
            return getEnv(prop)
        }

        if (property == null) {
            if (!propertyFile.exists()) {
                throw CodeccUserConfigException("The property file(${propertyFile.absolutePath}) is not exist")
            }
            property = Properties()
            property!!.load(FileInputStream(propertyFile))
        }
        return property!!.getProperty(prop, null)
    }

    private fun getEnv(prop: String): String? {
        var value = System.getenv(prop)
        if (value.isNullOrBlank()) {
            // Get from java properties
            value = System.getProperty(prop)
        }
        return value
    }

    fun isThirdParty() = getBuildType() == BuildType.AGENT.name

    fun getBuildType(): String {
        val buildType = SdkEnv.getSdkHeader()["X-DEVOPS-BUILD-TYPE"]
        if (buildType == null || !buildTypeContains(buildType)) {
            return BuildType.AGENT.name
        }
        return buildType
    }

    private fun buildTypeContains(env: String): Boolean {
        BuildType.values().forEach {
            if (it.name == env) {
                return true
            }
        }
        return false
    }


    fun isDockerEnv(): Boolean {
        return getBuildType() == BuildType.DOCKER.name
    }

    fun is32BitSystem() = System.getProperty("sun.arch.data.model") == "32"
}
