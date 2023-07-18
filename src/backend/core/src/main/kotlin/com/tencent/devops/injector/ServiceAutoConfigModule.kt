package com.tencent.devops.injector

import com.google.inject.AbstractModule
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.injector.service.InjectorService
import com.tencent.devops.injector.service.TaskIssueService
import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.util.ConfigurationBuilder

class ServiceAutoConfigModule : AbstractModule() {

    companion object {
        private const val corePackage = "com.tencent.devops.injector"
        private const val extPackage = "com.tencent.devops.ext.injector"
    }

    override fun configure() {
        val reflections = Reflections(ConfigurationBuilder().forPackages(corePackage).addScanners(SubTypesScanner()))

        val subClassList = reflections.getSubTypesOf(InjectorService::class.java)
        if (subClassList.isEmpty()) {
            return
        }
        for (clazz in subClassList) {
            if (clazz.isInterface) {
                // 绑定接口实现
                LogUtils.printLog("do bind $clazz")
                doBind(reflections, clazz)
            }
        }
    }

    private fun <T> doBind(reflections: Reflections, clazz: Class<T>) {
        val subClassList = reflections.getSubTypesOf(clazz)
        if (subClassList.isEmpty()) {
            LogUtils.printLog("bind $clazz fail. subclass empty")
            return
        }
        val subClassSelect = if (subClassList.size > 1) {
            var subClass = subClassList.first()
            for (subClassItem in subClassList) {
                if (subClassItem.`package`.name.startsWith(extPackage)) {
                    subClass = subClassItem
                    break
                }
            }
            subClass
        } else {
            subClassList.first()
        }
        LogUtils.printLog("bind $clazz to $subClassSelect")
        bind(clazz).to(subClassSelect)
    }
}