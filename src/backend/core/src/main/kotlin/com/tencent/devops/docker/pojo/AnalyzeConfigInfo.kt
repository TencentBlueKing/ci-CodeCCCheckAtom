/*
 * Tencent is pleased to support the open source community by making BK-CODECC 蓝鲸代码检查平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CODECC 蓝鲸代码检查平台 is licensed under the MIT license.
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

package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class AnalyzeConfigInfo(
    @JsonProperty("task_id")
    val taskId: Long,
    @JsonProperty("stream_name")
    val nameEn: String,
    val url: String?,
    @JsonProperty("password")
    val passWord: String?,
    @JsonProperty("tool_name")
    val multiToolType: String,
    @JsonProperty("skip_paths")
    val skipPaths: String?,
    @JsonProperty("skip_checkers")
    val skipCheckers: String?,
    val account: String?,
    @JsonProperty("scm_type")
    val scmType: String?,
    @JsonProperty("cert_type")
    val certType: String?,
    @JsonProperty("git_branch")
    val gitBranch: String?,
    @JsonProperty("access_token")
    val accessToken: String?,
    @JsonProperty("ssh_private_key")
    val sshPrivateKey: String?,
    @JsonProperty("scan_type")
    var scanType: ScanType,
    @JsonProperty("proj_owner")
    val projOwner: String?,
    @JsonProperty("open_checkers")
    val openCheckers: List<OpenCheckerVO>?,

    val toolOptions: List<ToolOptions>?,

    val platformIp: String?,

    val covOptions: List<String>?,

    val covPWCheckers: String?,

    val lastCodeRepos: List<CodeRepoInfoVO>?,

    val language: Long?,

    /**
     * 工具的个性化参数，专门用来给查询规则列表使用的，不在对外接口暴露
     */
    val paramJson: String?
)
