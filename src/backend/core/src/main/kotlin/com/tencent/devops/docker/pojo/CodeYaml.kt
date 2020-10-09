package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CodeYaml(
    var source: Source?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Source(
    val test_source: FilepathRegex?,
    val auto_generate_source: FilepathRegex?,
    val third_party_source: FilepathRegex?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FilepathRegex(
    val filepath_regex: List<String>?
)