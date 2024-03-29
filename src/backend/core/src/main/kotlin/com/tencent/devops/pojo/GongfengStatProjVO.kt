package com.tencent.devops.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class GongfengStatProjVO(
    val id: Int,
    @JsonProperty("bg_id")
    var bgId: Int?,
    @JsonProperty("org_paths")
    var orgPaths: String?,
    val path: String?,
    val description: String?,
    val visibility: String?,
    @JsonProperty("visibility_level")
    val visibilityLevel: Int?,
    val belong: String?,
    val owners: String?,
    @JsonProperty("created_at")
    val createdAt: String?,
    val creator: String?,
    val url: String?,
    val archived: Boolean?,
    @JsonProperty("is_sensitive")
    val isSensitive: Boolean?,
    @JsonProperty("sensitive_reason")
    val sensitiveReason: String?,
    @JsonProperty("public_visibility")
    val publicVisibility: Int?
)