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

import com.tencent.devops.api.PipelineTaskResourceApi
import com.tencent.devops.pojo.CodeccExecuteConfig
import com.tencent.devops.pojo.exception.CodeccDependentException
import com.tencent.devops.pojo.exception.CodeccUserConfigException
import com.tencent.devops.pojo.process.PipelineBuildTaskInfo
import com.tencent.devops.pojo.repo.CodeGitRepository
import com.tencent.devops.pojo.repo.CodeGitlabRepository
import com.tencent.devops.pojo.repo.CodeSvnRepository
import com.tencent.devops.pojo.repo.GithubRepository
import com.tencent.devops.pojo.repo.RepositoryConfig
import com.tencent.devops.pojo.repo.RepositoryType
import com.tencent.devops.utils.common.CredentialUtils
import com.tencent.devops.utils.common.EnvUtils
import com.tencent.devops.utils.common.RepositoryUtils
import com.tencent.devops.utils.common.SvnUtil

object CodeccRepoHelper {

    private val repoElementTypes = setOf(
        "CODE_SVN",
        "CODE_GIT",
        "CODE_GITLAB",
        "GITHUB"
    )
    private val codeccElementType = "CodeccCheckAtomDebug"
    private val pipelineBuildTaskApi = PipelineTaskResourceApi()

    fun getCodeccRepos(variables: Map<String, String>): List<CodeccExecuteConfig.RepoItem> {
        val repoItemList = mutableSetOf<CodeccExecuteConfig.RepoItem>()
        val buildTasks = pipelineBuildTaskApi.getAllBuildTask().data ?: throw CodeccDependentException("get build task fail")

        val containerId = variables["BK_CI_BUILD_JOB_ID"]

        val repoElements = buildTasks.filter { it.containerId == containerId && it.taskType in repoElementTypes }
        repoElements.forEach {
            val item = when (it.taskType) {
                "CODE_SVN" -> {
                    CodeccExecuteConfig.RepoItem(
                        repositoryConfig = replaceCodeProp(buildConfig(it), variables),
                        type = "SVN",
                        relPath = EnvUtils.parseEnv(it.getTaskParam("path"), variables),
                        relativePath = EnvUtils.parseEnv(it.getTaskParam("svnPath"), variables)
                    )
                }
                "CODE_GIT" -> {
                    CodeccExecuteConfig.RepoItem(
                        repositoryConfig = replaceCodeProp(buildConfig(it), variables),
                        type = "GIT",
                        relPath = EnvUtils.parseEnv(it.getTaskParam("path"), variables)
                    )
                }
                "CODE_GITLAB" -> {
                    CodeccExecuteConfig.RepoItem(
                        repositoryConfig = replaceCodeProp(buildConfig(it), variables),
                        type = "GIT",
                        relPath = EnvUtils.parseEnv(it.getTaskParam("path"), variables)
                    )
                }
                "GITHUB" -> {
                    CodeccExecuteConfig.RepoItem(
                        repositoryConfig = replaceCodeProp(buildConfig(it), variables),
                        type = "GIT",
                        relPath = EnvUtils.parseEnv(it.getTaskParam("path"), variables)
                    )
                }
                else -> {
                    throw CodeccUserConfigException("get codecc task fail with repo type: ${it.taskType}")
                }
            }
            repoItemList.add(item)
        }

        // 新的拉代码插件接入模式
        val newRepoTaskIds = variables.filter { it.key.contains("bk_repo_taskId_") }.values
        newRepoTaskIds.filter { taskId ->
            val repoContainerId = getEndWithValue(variables, "bk_repo_container_id_$taskId")
            repoContainerId.isNullOrBlank() || repoContainerId == containerId
        }.forEach { taskId ->
            val repoConfigType = getEndWithValue(variables, "bk_repo_config_type_$taskId")
            val repoType = getEndWithValue(variables, "bk_repo_type_$taskId") ?: ""
            val localPath = getEndWithValue(variables, "bk_repo_local_path_$taskId") ?: ""
            val relativePath = getEndWithValue(variables, "bk_repo_code_path_$taskId") ?: ""

            val item = if (repoConfigType.isNullOrBlank()) {
                val url = getEndWithValue(variables, "bk_repo_code_url_$taskId")!!
                val authType = getEndWithValue(variables, "bk_repo_auth_type_$taskId")!!
                CodeccExecuteConfig.RepoItem(
                    repositoryConfig = null,
                    type = repoType,
                    relPath = localPath,
                    relativePath = relativePath,
                    url = url,
                    authType = authType
                )
            } else {
                val value = if (repoConfigType == RepositoryType.ID.name) {
                    getEndWithValue(variables, "bk_repo_hashId_$taskId")
                } else {
                    getEndWithValue(variables, "bk_repo_name_$taskId")
                }
                CodeccExecuteConfig.RepoItem(
                    repositoryConfig = buildConfig(value!!, RepositoryType.valueOf(repoConfigType!!)),
                    type = repoType,
                    relPath = localPath,
                    relativePath = relativePath
                )
            }
            repoItemList.add(item)
        }

        val newRepoItemList = repoItemList.map {
            if (it.repositoryConfig != null) {
                val repo = RepositoryUtils.getRepository(it.repositoryConfig)
                val authType = when (repo) {
                    is CodeGitRepository -> {
                        val authType = repo.authType?.name?.toUpperCase()
                        if (authType.isNullOrBlank()) "HTTP" else authType!!
                    }
                    is CodeSvnRepository -> {
                        val authType = repo.svnType?.toUpperCase()
                        if (authType.isNullOrBlank()) "SSH" else authType!!
                    }
                    is CodeGitlabRepository -> "HTTP"
                    is GithubRepository -> "HTTP"
                    else -> "SSH"
                }
                it.url = repo.url
                it.authType = authType
                it.repoHashId = repo.repoHashId ?: ""

                if (repo is CodeSvnRepository && authType == "HTTP") {
                    val credentialsWithType = CredentialUtils.getCredentialWithType(repo.credentialId)
                    val credentials = credentialsWithType.first
                    val credentialType = credentialsWithType.second
                    val svnCredential = SvnUtil.getSvnCredential(repo, credentials, credentialType)
                    it.svnUerPassPair = Pair(svnCredential.username, svnCredential.password)
                }
            }
            it
        }

        if (newRepoItemList.isEmpty()) {
            System.err.println("repo list is empty...")
        }

        return newRepoItemList
    }

    fun getScmType(repos: List<CodeccExecuteConfig.RepoItem>): String {
        if (repos.isEmpty()) {
            return ""
        }
        return repos.map { it.type }.first().toLowerCase() // 每次扫描支持一种类型代码库，其他情况先不考虑
    }

    private fun getEndWithValue(variables: Map<String, String>, subKey: String): String? {
        val key = variables.keys.firstOrNull { it.endsWith(subKey) }
        return variables[key]
    }

    fun getCertType(repos: List<CodeccExecuteConfig.RepoItem>): String {
        if (repos.isEmpty()) {
            return ""
        }
        return repos.map { it.authType }.first() // 每次扫描支持一种类型代码库认证类型，其他情况先不考虑
    }

    private fun buildConfig(task: PipelineBuildTaskInfo): RepositoryConfig {
        return when (task.taskType) {
            in repoElementTypes ->
                RepositoryConfig(
                    task.getTaskParam("repositoryHashId"),
                    task.getTaskParam("repositoryName"),
                    RepositoryType.parseType(task.getTaskParam("repositoryType"))
                )
            else -> throw CodeccUserConfigException("Unknown code element -> ${task.taskType}")
        }
    }

    private fun buildConfig(repositoryId: String, repositoryType: RepositoryType?) =
        if (repositoryType == null || repositoryType == RepositoryType.ID) {
            RepositoryConfig(
                repositoryHashId = repositoryId,
                repositoryName = null,
                repositoryType = RepositoryType.ID
            )
        } else {
            RepositoryConfig(
                repositoryHashId = null,
                repositoryName = repositoryId,
                repositoryType = RepositoryType.NAME
            )
        }

    private fun replaceCodeProp(repositoryConfig: RepositoryConfig, variables: Map<String, String>): RepositoryConfig {
        if (repositoryConfig.repositoryType == RepositoryType.NAME) {
            if (!repositoryConfig.repositoryName.isNullOrBlank()) {
                return RepositoryConfig(
                    repositoryHashId = repositoryConfig.repositoryHashId,
                    repositoryName = EnvUtils.parseEnv(repositoryConfig.repositoryName!!, variables),
                    repositoryType = repositoryConfig.repositoryType
                )
            }
        }
        return repositoryConfig
    }
}