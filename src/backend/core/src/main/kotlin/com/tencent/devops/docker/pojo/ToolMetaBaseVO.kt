package com.tencent.devops.docker.pojo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
open class ToolMetaBaseVO : CommonVO() {
    /**
     * 工具模型,LINT、COMPILE、TSCLUA、CCN、DUPC，决定了工具的接入、告警、报表的处理及展示类型
     */
    open var pattern: String? = null

    /**
     * 工具名称，也是唯一KEY
     */
    open var name: String? = null

    /**
     * 工具的展示名
     */
    open var displayName: String? = null

    /**
     * 工具类型，界面上展示工具归类：
     * 发现缺陷和安全漏洞、规范代码、复杂度、重复代码
     */
    open var type: String? = null

    /**
     * 支持语言，通过位运算的值表示
     */
    open var lang: Long = 0

    /**
     * 根据项目语言来判断是否推荐该款工具,true表示推荐，false表示不推荐
     */
    open var recommend = false

    /**
     * 状态：测试（T）、灰度（保留字段）、发布（P）、下架， 注：测试类工具只有管理员可以在页面上看到，只有管理员可以接入
     */
    open var status: String? = null

    /**
     * 工具的个性参数，如pylint的Python版本，这个参数用json保存。
     * 用户在界面上新增参数，填写参数名，参数变量， 类型（单选、复选、下拉框等），枚举值
     */
    open var params: String? = null
}
