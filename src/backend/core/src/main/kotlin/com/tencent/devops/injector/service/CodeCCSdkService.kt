package com.tencent.devops.injector.service

interface CodeCCSdkService : InjectorService {

    fun getCreateTaskUrl(openScanProj: Boolean?): String


    fun getUpdateTaskUrl(openScanProj: Boolean?): String


}