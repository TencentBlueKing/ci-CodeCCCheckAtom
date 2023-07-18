package com.tencent.devops.utils

import com.tencent.bk.devops.atom.pojo.AtomBaseParam
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.tools.LogUtils
import org.apache.commons.lang3.ObjectUtils
import org.slf4j.LoggerFactory
import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

object I18NUtils {
    @Volatile
    private var LANGUAGE_TAG: String = "en"

    @Volatile
    private var INITIALIZED = false
    private val logger = LoggerFactory.getLogger(I18NUtils::class.java)
    private const val DEFAULT_BASE_NAME = "i18n/message"

    @Synchronized
    fun init(atomBaseParam: AtomBaseParam) {
        if (INITIALIZED) {
            return
        }

        try {
            INITIALIZED = true
            LANGUAGE_TAG = CodeccSdkApi.getI18NLanguageTag(atomBaseParam.pipelineStartUserId)
        } catch (t: Throwable) {
            logger.error("get i18n language tag fail", t)
        }
        LogUtils.printLog("i18n language tag: $LANGUAGE_TAG")
    }

    fun getLanguageTag(): String {
        verify()
        return LANGUAGE_TAG
    }

    fun addAcceptLanguageHeader(headerMap: MutableMap<String, String>) {
        verify()
        headerMap["accept-language"] = LANGUAGE_TAG
    }

    private fun verify() {
        if (!INITIALIZED) {
            logger.warn("I18NUtils not initialize, default value: $LANGUAGE_TAG")
        }
    }

    /**
     * 获取国际化信息
     */
    fun getMessage(resourceCode: String): String {
        return try {
            val localeObj = Locale.forLanguageTag(getLanguageTag())
            val resourceBundle: ResourceBundle = ResourceBundle.getBundle("i18n/message", localeObj)
            resourceBundle.getString(resourceCode)
        } catch (e: Exception) {
            logger.error("i18n util get message error, code: {}", resourceCode, e)
            "I18N_ERR"
        }
    }

    /**
     * 获取国际化信息
     */
    fun getMessage(resourceCode: String, params: Array<String>?): String {
        val paramsObj = params ?: arrayOf<String>()

        val message = getMessage(resourceCode)
        if (ObjectUtils.isEmpty(message)) {
            return ""
        }

        return try {
            MessageFormat.format(message, params)
        } catch (e: IllegalArgumentException) {
            logger.error("i18n util get message error, code: {}, params: {}", resourceCode, params, e)
            "I18N_ERR"
        }
    }


    /**
     * 判断当前环境语言类型是否为中文
     */
    fun currentLanguageIsZhCN(): Boolean {
        return LANGUAGE_TAG == Locale.SIMPLIFIED_CHINESE.toLanguageTag()
    }
}
