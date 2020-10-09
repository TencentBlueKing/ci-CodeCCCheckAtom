package com.tencent.devops.pojo.sdk

/*
*
* {
    "taskId":166938,
    "pathType":"CUSTOM",
    "filterFile":[

    ],
    "filterDir":[

    ],
    "customPath":[
        "2222",
        "3333"
    ]
}
*
* */
data class FilterPathInput(
    val taskId: Long?,
    val pathType: String? = "",
    val customPath: List<String>?,
    val filterDir: List<String>?,
    val filterFile: List<String>? = listOf()
)