package com.tencent.devops.pojo.sdk


/*
*
* {
    "taskId":"166938",
    "scanType":1,
    "timeAnalysisConfig":{
        "executeDate":[

        ],
        "executeTime":""
    },
    "transferAuthorList":[
        {
            "sourceAuthor":"ddddddd",
            "targetAuthor":"ddlin"
        }
    ],
    "newDefectJudge":{
        "judgeBy":1,
        "fromDate":"2019-12-16"
    }
}
*
* */
data class ScanConfiguration(
    val taskId: String?,
    val scanType: Int?,
    val timeAnalysisConfig: TimeAnalysisConfig?,
    val transferAuthorList: List<TransferAuthorPair>?,
    val newDefectJudge: NewDefectJudge,
    val mrCommentEnable: Boolean?
) {
    data class NewDefectJudge(
        val fromDate: String?,
        val judgeBy: Int?
    )

    data class TimeAnalysisConfig(
        val executeDate: List<String>,
        val executeTime: String
    )

    data class TransferAuthorPair(
        val sourceAuthor: String,
        val targetAuthor: String
    )
}