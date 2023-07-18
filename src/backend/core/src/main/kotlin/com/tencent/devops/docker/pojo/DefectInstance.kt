package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class DefectInstance(
    val traces: List<Trace>? = null
)


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Trace(
    val message: String,
    val fileMd5: String? = null,
    val filePath: String,
    val tag: String? = null,
    val traceNum: Int? = 0,
    val lineNum: Int = 0,
    val startColumn: Int? = 0,
    val endColumn: Int? = 0,
    val main: Boolean? = false,

    /**
     * 事件类型:
     * MODEL：       与函数调用对应。在 Coverity Connect 中，模型事件显示在“显示详情”(Show Details) 链接旁边。
     * -----------------------------------------------------------------------------------------------------
     * PATH：        标识软件问题发生所需的 conditional 分支和决定。
     * 示例：Condition !p, taking false branch
     * Related lines 107-108 of sample code: 107 if (!p) 108 return NO_MEM;
     * -----------------------------------------------------------------------------------------------------
     * MULTI：       提供支持检查器发现的软件问题的源代码中的证据。也称为证据事件。
     * -----------------------------------------------------------------------------------------------------
     * NORMAL：      引用被标识为检查器发现的软件问题的引起因素的代码行。
     * 示例：
     * 1. alloc_fn: Storage is returned from allocation function malloc.
     * 2. var_assign: Assigning: p = storage returned from malloc(12U)
     * Related line 5 of sample code: 5 char *p = malloc(12);
     * -----------------------------------------------------------------------------------------------------
     * REMEDIATION： 提供旨在帮助您修复报告的软件问题的补救建议，而不只是报告问题。用在安全缺陷中。
     */
    val kind: String? = null,

    /**
     * 关联告警跟踪信息
     */
    var linkTrace: List<Trace>? = null
)