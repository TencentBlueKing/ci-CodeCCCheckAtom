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

import com.fasterxml.jackson.annotation.JsonIgnore

data class Result<out T>(
    val status: Int,
    val code: String?,
    val message: String? = null,
    val data: T? = null
) {
    constructor(data: T) : this(0, "0", null, data)
    constructor(status: Int, message: String?) : this(status, "0", message, null)
    constructor(errCode: String, message: String?) : this(0, errCode, message, null)
    constructor(status: Int, message: String?, data: T?) : this(status, "0", message, data)
    constructor(status: Int, errCode: String, message: String?) : this(status, errCode, message, null)

    @JsonIgnore
    fun isOk(): Boolean {
        return (status == 0 || (null != code && code == "0"))
    }

    @JsonIgnore
    fun isNotOk(): Boolean {
        return (status != 0 || (null != code && code != "0"))
    }
}
