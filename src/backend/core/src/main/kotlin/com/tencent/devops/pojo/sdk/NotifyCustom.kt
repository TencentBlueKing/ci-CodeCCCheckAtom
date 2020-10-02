package com.tencent.devops.pojo.sdk

/*
* {
    "rtxReceiverType":"0",
    "rtxReceiverList":[

    ],
    "botWebhookUrl":"webhookurl1",
    "botRemindRange":1,
    "botRemindSeverity":7,
    "botRemaindTools":[

    ],
    "emailReceiverType":"1",
    "emailReceiverList":[

    ],
    "emailCCReceiverList":[
        "ddlin"
    ],
    "reportStatus":1,
    "reportDate":[
        1,
        4,
        6
    ],
    "reportTime":3,
    "reportTools":[

    ],
    "instantReportStatus":1,
    "tosaReportDate":[

    ],
    "tosaReportTime":0
}
*
* */
data class NotifyCustom(

    val taskId: String?,
    /**
     * rtx接收人类型：0-所有项目成员；1-接口人；2-自定义
     */
    val rtxReceiverType: String? = null,

    /**
     * rtx接收人列表，rtxReceiverType=2时，自定义的接收人保存在该字段
     */

    val rtxReceiverList: Set<String>? = null,

    /**
     * 邮件收件人类型：0-所有项目成员；1-接口人；2-自定义
     */

    val emailReceiverType: String? = null,

    /**
     * 邮件收件人列表，当emailReceiverType=2时，自定义的收件人保存在该字段
     */

    val emailReceiverList: Set<String>? = null,

    /**
     * 邮件抄送人列表
     */

    val emailCCReceiverList: Set<String>? = null,

    /**
     * 定时报告任务的状态，有效：1，暂停：2
     */

    val reportStatus: Int? = null,

    /**
     * 定时报告的发送日期:
     */

    val reportDate: List<Int>? = null,

    /**
     * 定时报告的发送时间，小时位
     */

    val reportTime: Int? = null,

    /**
     * 定时报告分钟，分钟位
     */

    val reportMinute: Int? = null,

    /**
     * 即时报告状态，有效：1，暂停：2
     */

    val instantReportStatus: String? = null,

    /*---------群机器人通知start---------*/
    /**
     * 定时报告工具（即邮件需要发送哪些工具的数据报表）
     */

    val reportTools: Set<String>? = null,

    /**
     * 群机器人通知地址
     */

    val botWebhookUrl: String? = null,

    /**
     * 群机器人通知告警级别，多个级别直接相加
     */

    val botRemindSeverity: Int? = null,

    /**
     * 群机器人通知工具列表
     */

    val botRemaindTools: Set<String>? = null,

    /**
     * 群机器人通知范围
     */

    val botRemindRange: Int? = null
    /*---------群机器人通知end---------*/
)