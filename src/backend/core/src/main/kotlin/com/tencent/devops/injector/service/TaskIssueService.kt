package com.tencent.devops.injector.service

import com.tencent.devops.pojo.CodeccCheckAtomParamV3

interface TaskIssueService : InjectorService {
    fun updateTaskIssueInfo(param: CodeccCheckAtomParamV3)
}