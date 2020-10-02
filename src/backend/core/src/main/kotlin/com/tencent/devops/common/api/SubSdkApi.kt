package com.tencent.devops.common.api

interface SubSdkApi {
    fun getUrl():String

    fun addInputParam(codeccOpenSourceJson:String): Map<String, String>
}