package com.tencent.devops.pojo.exception

/**
 * 错误码定义
 * 102000 - 102999
 */
enum class ErrorCode(
    val errorCode: Int,
    val errorMsg: String
) {

    /**
     * 1020XX - 1021XX 用户配置类错误错误码定义
     */
    USER_SCAN_TOOL_EMPTY(102001, "scan tool is empty"),
    USER_PINPOINT_NO_SUPPORT(102002, "pinpoint no support macos and windows"),
    USER_ENV_MISSING(102003, "need install {0} to run multiple tool"),
    USER_ENV_CHECK_FAIL(102004, "check env fail."),
    USER_SET_ENV_FAIL(102005, "failed to set environment variable"),
    USER_PROJECT_STOP(102006, "this project already stopped, can't scan by CodeCC!"),
    USER_NO_P4_REPO_FOUNT(102007, "none perforce repo info found!"),
    USER_DIFF_MODE_NOT_SUPPORT(102008, "only git or github is support in diff mode"),
    USER_DIFF_BRANCH_REPO_LIMIT(102009, "only one repo is support in diff branch"),
    USER_MR_SOURCE_TARGET_NULL(102010, "source and target branch can not be null in MR mode"),
    USER_PROJECT_LANG_NOT_EXIST(102011, "lang is not exist"),
    USER_REPO_INFO_MISSING(102012, "repo info lack"),
    USER_REPO_TYPE_UNKNOWN(102013, "repo element unknown"),
    USER_GET_REPO_FAIL(102014, "fail to get the repo"),
    USER_INSUFFICIENT_MACHINE_PERMISSIONS(102015, "insufficient machine permissions"),
    USER_FILE_PATH_NOT_EXIST(102016, "file path no exist"),
    USER_CREDENTIAL_EMPTY(102017, "the credential Id is empty"),
    USER_EXEC_SCRIPT_FAIL(102018, "fail to execute script"),




    /**
     * 1022XX CodeCC后台返回200，插件错误（返回值缺失等）
     */
    CODECC_REQUIRED_FIELD_IS_EMPTY(102201, "required field is empty：{0}"),
    CODECC_RETURN_STATUS_CODE_ERROR(
        102202, "required status or code check fail, status: {0}," +
                " code: {1}, msg: {2}"
    ),
    CODECC_RETURN_PARAMS_CHECK_FAIL(102203, "return field check fail. {0}"),
    CODECC_REQUEST_FAIL(102204, "request to codecc fail."),
    CODECC_REQUEST_RETRY_LIMIT(102205, "request retry limit reached. limit : {0}"),
    CODECC_DOWNLOAD_RETRY_LIMIT(102206, "download retry limit reached. limit : {0}"),
    CODECC_DOWNLOAD_FAIL(102207, "download fail"),
    CODECC_IMAGE_NAME_INVALID(102208, "image name invalid"),


    /**
     * 1023XX - 1025XX CodeCC后台返回状态错误
     */


    /**
     * 1026XX 工具运行错误
     */
    TOOL_FINISH_STATUS_RETRY_LIMIT_REACHED(
        102601, "get tool finish status retry limit reached." +
                " limit : {0}"
    ),
    TOOL_STATUS_RETURN_EMPTY(102602, "get tool running step and status return empty."),
    TOOL_STATUS_CHECK_FAIL(102603, "tool run fail. step: {0}, flag: {1}"),
    TOOL_UPLOAD_FILE_FAIL(102604, "tool upload file fail. file: {0}"),
    TOOL_UPLOAD_RETRY_LIMIT(102605, "tool upload file retry limit reached. limit: {0}"),
    TOOL_DEFECT_SIZE_LIMIT(102606, "tool defect size limit reached: limit: {0}"),
    TOOL_DEFECT_DESERIALIZE_ERROR(102607, "defect file deserialize fail"),
    TOOL_RUN_FAIL(102608, "tool run fail"),

    /**
     * 1027XX SCM错误
     */
    SCM_INFO_NEW_FAIL(102701, "scm info new fail."),
    SCM_INCREMENT_FAIL(102702, "scm increment fail."),
    SCM_DIFF_FAIL(102703, "scm diff fail."),
    SCM_BLAME_FAIL(102704, "scm blame fail."),
    SCM_SVN_COMMAND_RUN_FAIL(102705, "svn command run fail. cmd: {0}"),
    SCM_GIT_COMMAND_RUN_FAIL(102706, "git command run fail. cmd: {0}"),
    SCM_COMMAND_RUN_FAIL(102707, "scm command run fail. cmd: {0}"),

    /**
     * 1028XX 第三方错误
     */
    THIRD_REQUEST_FAIL(102801, "request to third party fail"),

    /**
     * 1029XX 其他
     */
    UNKNOWN_PLUGIN_ERROR(102901, "unknown error"),
    UNSUPPORTED_HTTP_METHOD(102902, "unsupported Http method: {0}"),
    SCRIPT_COMMAND_EXECUTION_FAIL(102903, "script command execution failed."),
    PLUGIN_TIMEOUT(102904, "plugin timeout."),
    PLUGIN_DECRYPT_ERROR(102905, "aes decrypt fail."),
    PLUGIN_FILE_NOT_FOUNT(102906, "file not found."),
    PLUGIN_HASH_PARSE_ERROR(102907, "pp hash parse error."),
    PLUGIN_FILE_EXISTS(102908, "file exists.")


    ;

    companion object {
        fun valueOf(code: Int): ErrorCode? {
            for (value in values()) {
                if (code == value.errorCode) {
                    return value
                }
            }
            return null
        }
    }
}