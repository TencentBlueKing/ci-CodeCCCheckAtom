package com.tencent.devops.docker.pojo

enum class ScanType(code: Int) {
    FULL(0),
    INCREMENT(1),
    DIFF(2),
    FAST_INCREMENTAL(3),
    PARTIAL_INCREMENTAL(4)
}
