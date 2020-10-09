package com.tencent.devops.docker.pojo

data class ImageParam(
    var command: List<String>,
    val imageName: String,
    val registryUser: String = "",
    val registryPwd: String = "",
    val env: Map<String, String> = emptyMap()
) {
    override fun toString(): String {
        return "command: $command, imageName: $imageName, env: $env"
    }
}
