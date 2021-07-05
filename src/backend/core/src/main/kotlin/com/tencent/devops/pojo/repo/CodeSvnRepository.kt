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

package com.tencent.devops.pojo.repo

data class CodeSvnRepository(
    override val aliasName: String,
    override val url: String,
    override val credentialId: String,
    val region: CodeSvnRegion? = CodeSvnRegion.TC,
    override val projectName: String,
    override var userName: String,
    override val projectId: String?,
    override val repoHashId: String?,
    val svnType: String? = SVN_TYPE_SSH // default is ssh svn type
) : Repository {

    companion object {
        const val classType = "codeSvn"
        const val SVN_TYPE_HTTP = "http"
        const val SVN_TYPE_SSH = "ssh"
    }

    override fun isLegal(): Boolean {
        if (svnType == SVN_TYPE_HTTP) {
            return url.startsWith("http://") ||
                url.startsWith("https://")
        }
        return url.startsWith(getStartPrefix())
    }

    override fun getFormatURL(): String {
        var fixUrl = url
        if (fixUrl.startsWith("svn+ssh://")) {
            val split = fixUrl.split("://")
            if (split.size == 2) {
                val index = split[1].indexOf("@")
                val suffix = if (index >= 0) {
                    split[1].substring(index + 1)
                } else {
                    split[1]
                }
                fixUrl = split[0] + "://" + suffix
            }
        }
        return fixUrl
    }

    override fun getStartPrefix() = "svn+ssh://"
}