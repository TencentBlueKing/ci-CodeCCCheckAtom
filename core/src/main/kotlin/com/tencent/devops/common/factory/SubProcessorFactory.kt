package com.tencent.devops.common.factory

import com.tencent.devops.common.api.*
import com.tencent.devops.common.docker.CommonSubBuild
import com.tencent.devops.common.docker.CommonSubScan
import com.tencent.devops.common.docker.CommonSubScanComposer
import com.tencent.devops.common.utils.CommonSubReport
import com.tencent.devops.common.utils.CommonSubSdkApi
import com.tencent.devops.docker.pojo.TaskBaseVO
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.pojo.exception.CodeccException

class SubProcessorFactory {

    fun createSubBuild(codeccTaskInfo: TaskBaseVO?): SubBuild {
        val gongFengSubBuild = "com.tencent.devops.docker.GongFengSubBuild"
        return if (codeccTaskInfo !== null && codeccTaskInfo.createFrom == "gongfeng_scan" && getProcessor(gongFengSubBuild) != null) {
            getProcessor(gongFengSubBuild) as SubBuild
        } else {
            CommonSubBuild
        }
    }

    fun createSubReport(openScanPrj: Boolean?): SubReport {
        val gongFengSubReport = "com.tencent.devops.utils.GongFengSubReport"
        return if (null != openScanPrj && openScanPrj && getProcessor(gongFengSubReport) != null) {
            getProcessor(gongFengSubReport) as SubReport
        } else {
            CommonSubReport
        }
    }

    fun createSubScanComposerByCov(toolName: String?): SubScanComposer {
        val coveritySubScanComposer = "com.tencent.devops.docker.CoveritySubScanComposer"
        return if (ToolConstants.COVERITY == toolName && getProcessor(coveritySubScanComposer) != null) {
            getProcessor(coveritySubScanComposer) as SubScanComposer
        } else {
            CommonSubScanComposer()
        }
    }

    fun createSubScanComposerByCovKloc(toolName: String): SubScanComposer {
        val coveritySubScanComposer = "com.tencent.devops.docker.CoveritySubScanComposer"
        return if ((ToolConstants.COVERITY == toolName || ToolConstants.KLOCWORK == toolName) && getProcessor(coveritySubScanComposer) != null) {
            getProcessor(coveritySubScanComposer) as SubScanComposer
        } else {
            CommonSubScanComposer()
        }
    }

    fun createSubScan(openScanPrj: Boolean?, toolName: String): SubScan {
        val coveritySubScan = "com.tencent.devops.docker.GongFengSubScan"
        return if (openScanPrj != null && openScanPrj == true && toolName == ToolConstants.COVERITY && getProcessor(coveritySubScan) != null) {
            getProcessor(coveritySubScan) as SubScan
        } else {
            CommonSubScan()
        }
    }

    fun createSubScanByToolName(toolName: String): SubScan {
        val coveritySubScan = "com.tencent.devops.docker.GongFengSubScan"
        return if ((toolName == ToolConstants.COVERITY || toolName == ToolConstants.KLOCWORK) && getProcessor(coveritySubScan) != null) {
            getProcessor(coveritySubScan) as SubScan
        } else {
            CommonSubScan()
        }
    }

    fun createSubSdkApi(openScanProj :Boolean): SubSdkApi {
        val GongFengSubSdkApi = "com.tencent.devops.utils.GongFengSubSdkApi"
        return if (openScanProj && getProcessor(GongFengSubSdkApi) != null) {
            getProcessor(GongFengSubSdkApi) as SubSdkApi
        } else {
            CommonSubSdkApi()
        }
    }

    private fun getProcessor(className: String): Any? {
        return try {
            val GongfengProcessor = Class.forName(className).newInstance()
            GongfengProcessor
        } catch (e: Exception) {
            LogUtils.printLog("find not the Class:${className}!")
            null
        }
    }
}