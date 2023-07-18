package com.tencent.devops.injector

import com.google.inject.Guice
import com.google.inject.Inject
import com.google.inject.Injector
import com.tencent.devops.docker.tools.LogUtils
import com.tencent.devops.injector.service.InjectorService
import org.reflections.Reflections
import org.reflections.scanners.FieldAnnotationsScanner
import org.reflections.util.ConfigurationBuilder
import java.lang.reflect.Modifier

object ServiceInjector  {

    private const val basePackage = "com.tencent.devops"

    private val injector: Injector = Guice.createInjector(ServiceAutoConfigModule())

    // 注入入口
    fun injectService(){
        // 静态自动注入
        injectStaticService()
    }

    fun <T : InjectorService> inject(clazz: Class<T>): T {
        return injector.getInstance(clazz)
    }

    private fun injectStaticService(){
        val reflections = Reflections(ConfigurationBuilder().forPackages(basePackage)
                .addScanners(FieldAnnotationsScanner()))
        val fields = reflections.getFieldsAnnotatedWith(Inject::class.java)
        if (fields.isNotEmpty()) {
            for (field in fields) {
                if(Modifier.isStatic(field.modifiers)){
                    field.isAccessible = true
                    LogUtils.printLog(field.type)
                    field.set(null, injector.getInstance(field.type))
                }
            }
        }
    }
}