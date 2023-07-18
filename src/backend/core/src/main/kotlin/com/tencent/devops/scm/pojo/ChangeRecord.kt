package com.tencent.devops.scm.pojo

import java.util.*
import kotlin.collections.ArrayList

data class ChangeRecord (
    var author: String? ="",
    var authorMail: String? = "",
    var lineRevisionId: String? = "",
    var lineShortRevisionId: String? = "",
    var lineUpdateTime: Long? = 0,
    var lines: ArrayList<Any>? = ArrayList<Any>()
)