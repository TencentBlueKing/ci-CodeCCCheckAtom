package com.tencent.devops.injector.service.impl

import com.tencent.devops.injector.service.CodeCCSdkService

open class CodeCCSdkServiceImpl : CodeCCSdkService {

    private val createPath: String = "/ms/task/api/build/task"
    private val updatePath: String = "/ms/task/api/build/task"

    override fun getCreateTaskUrl(openScanProj: Boolean?): String {
        return createPath
    }

    override fun getUpdateTaskUrl(openScanProj: Boolean?): String {
        return updatePath
    }


}