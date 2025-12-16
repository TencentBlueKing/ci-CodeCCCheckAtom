plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.allopen") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.6"
    application
}

// 定义版本变量
val javaAtomSdkVersion = "2.0.0"
val cryptSdkVersion = "1.1.3"
val coroutinesVersion = "1.10.1"
val jacksonVersion = "2.15.0"  // 与java-atom-sdk保持一致，支持Java 17
val compressVersion = "1.27.1"
val reflectionsVersion = "0.10.2"
val jsonVersion = "20250107"
val p4javaVersion = "2021.2.2240592"
val fluentVersion = "4.5.14"
val injectVersion = "7.0.0"
val fastjsonVersion = "1.2.83"
val lz4Version = "1.8.0"
val xzVersion = "1.10"  // 升级: 1.2 -> 1.10
val zip4jVersion = "2.11.4"
val hashidsVersion = "1.0.3"

group = "com.tencent.bk.devops"

fun getValue(key: String, defaultValue: Any): String {
    return System.getProperty(key)
        ?: System.getenv(key)
        ?: defaultValue.toString()
}

val mavenCredUserName = getValue("mavenCredUserName", property("MAVEN_CRED_USERNAME") ?: "")
val mavenCredPassword = getValue("mavenCredPassword", property("MAVEN_CRED_PASSWORD") ?: "")

val ktlint by configurations.creating

tasks.register<JavaExec>("ktlint") {
    group = "verification"
    description = "Check Kotlin code style."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("src/**/*.kt")
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Fix Kotlin code style deviations."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args("-F", "src/**/*.kt")
}

repositories {
    mavenLocal()
//    maven {
//        url = uri("https://mirrors.tencent.com/repository/maven/tencent_public")
//    }
    maven {
        url = uri("https://repo.maven.apache.org/maven2")
    }
    maven {
        url = uri("https://plugins.gradle.org/m2")
    }
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.tencent.devops.ci-plugins:java-plugin-sdk:${javaAtomSdkVersion}")
    implementation("com.tencent.bk.sdk:crypto-java-sdk:$cryptSdkVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    // jacksonVersion需要与java-atom-sdk中使用的版本保持一致
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("org.apache.commons:commons-compress:$compressVersion")
    implementation("org.reflections:reflections:$reflectionsVersion")
    implementation("org.json:json:$jsonVersion")
    implementation("com.perforce:p4java:$p4javaVersion")
    implementation("org.apache.httpcomponents:fluent-hc:$fluentVersion")
    implementation("com.google.inject:guice:$injectVersion")
    implementation("com.alibaba:fastjson:$fastjsonVersion")
    implementation("org.lz4:lz4-java:$lz4Version")
    // commons-compress包解压.xz文件有依赖
    implementation("org.tukaani:xz:$xzVersion")
    implementation("net.lingala.zip4j:zip4j:$zip4jVersion")
    implementation("org.hashids:hashids:$hashidsVersion")
    ktlint("com.pinterest:ktlint:0.50.0")
}
kotlin {
    jvmToolchain(17)  // 升级: Java 8 -> Java 17
}

application {
    // 固定入口类 不要改
    mainClass.set("com.tencent.bk.devops.atom.AtomRunner")
}

tasks.shadowJar {
    // baseName为插件默认打包名+".jar"，bkdevops-plugin.jar
    // 如果修改，则要一同修改插件task.json中的target启动命令
    // 为了省事，建议不用修改
    archiveBaseName = "bkdevops-plugin"
    archiveClassifier = ""
    archiveVersion = ""
    isZip64 = true
}
// 复制前端文件任务
tasks.register<Copy>("copyFrontend") {
    from("../../frontend/dist")
    into("../../frontend/target/frontend")
}

// 清理目标目录任务
tasks.register<Delete>("cleanTarget") {
    delete("../../../target")
}

// 复制文档任务
tasks.register<Copy>("copyDocs") {
    from("../../../docs")
    into("../../../target/file/docs")
}

// 复制图片任务
tasks.register<Copy>("copyImages") {
    from("../../../images")
    into("../../../target/file/images")
}

// 构建中文版ZIP包
tasks.register<Zip>("buildZipCn") {
    dependsOn("shadowJar", "copyFrontend", "cleanTarget", "copyDocs", "copyImages")
    from("build/libs", "task.json", "quality.json", "../../frontend/target", "../../../target/file")
    into("CodeCCCheckAtom")
    archiveFileName.set("CodeCCCheckAtom.zip")
}

// 重命名英文task.json任务
tasks.register<Copy>("renameEnTaskJson") {
    dependsOn("cleanTarget")
    from("task_en.json")
    into("../../../target/en")
    rename { "task.json" }
}

// 构建英文版ZIP包
tasks.register<Zip>("buildZipWithEn") {
    dependsOn("buildZipCn", "renameEnTaskJson")
    from("build/libs", "quality.json", "../../frontend/target", "../../../target/file", "../../../target/en")
    into("CodeCCCheckAtom")
    archiveFileName.set("CodeCCCheckAtom_en.zip")
}
