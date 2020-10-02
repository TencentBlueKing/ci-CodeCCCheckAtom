package com.tencent.devops.pojo

data class OpenScanConfigParam(
        var openScanFilterBgId : String? = null,
        var coverityFilterBg : String? = null,
        var coverityScanPeriod : Int? = null
)