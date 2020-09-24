package com.tencent.devops.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.pojo.ArtifactData
import com.tencent.bk.devops.atom.pojo.DataField
import com.tencent.bk.devops.atom.pojo.ReportData
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.devops.api.CodeccReportApi
import com.tencent.devops.api.CodeccSdkApi
import com.tencent.devops.docker.pojo.ToolConstants
import com.tencent.devops.pojo.CodeccCheckAtomParamV3
import com.tencent.devops.pojo.report.CodeccCallback
import java.io.BufferedReader
import java.io.File
import java.nio.file.Files

@SuppressWarnings
object CodeccReportUtilsV2 {

    private val api = CodeccReportApi()

    private val tempDir = Files.createTempDirectory("codecc_").toFile()
    private val tempAsyncDir = Files.createTempDirectory("codecc_async_").toFile()

    fun report(atomContext: AtomContext<CodeccCheckAtomParamV3>) {
        try {
            val reportData = getReportData(atomContext.param.pipelineBuildId, atomContext.result.data) ?: return
            val indexHtml = doReport(atomContext.param, reportData)
            atomContext.result.data["codecc_report"] = ReportData.createLocalReport("CodeCC代码检查V3", tempDir.canonicalPath, indexHtml.name)
            // atomContext.result.data["codecc_resource"] = ArtifactData(setOf(chartOptionJs.canonicalPath, mainJs.canonicalPath, indexCss.canonicalPath))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun asyncReport(atomContext: AtomContext<CodeccCheckAtomParamV3>, detailLink: String) {
        try {
            val indexHtml = doAsyncReport(atomContext.param, detailLink)
            atomContext.result.data["codecc_report"] = ReportData.createLocalReport("CodeCC代码检查V3", tempAsyncDir.canonicalPath, indexHtml.name)
            // atomContext.result.data["codecc_resource"] = ArtifactData(setOf(chartOptionJs.canonicalPath, mainJs.canonicalPath, indexCss.canonicalPath))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun doAsyncReport(
        param: CodeccCheckAtomParamV3,
        detailLink: String
    ) : File {
        val indexHtmlBody = StringBuilder()
        val indexCss = File(tempAsyncDir, "index.css")
        indexCss.writeText(getIndexCss())

        // 头部
        indexHtmlBody.append("<!DOCTYPE html>\n" +
                "<html>\n" +
                "    <head> \n" +
                "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"> \n" +
                "        <meta http-equiv=\"X-UA-Compatible\" content=\"ie=edge\"> \n" +
                "        <meta charset=\"utf-8\"> \n" +
                "        <link rel=\"stylesheet\" href=\"https://magicbox.bk.tencent.com/static_api/v3/components_vue/2.0/bk-magic-vue.min.css\"> \n" +
                "        <link rel=\"stylesheet\" href=\"./${indexCss.name}\"> \n" +
                "        <title>CodeCC报告</title> \n" +
                "    </head> \n" +
                "    <body> \n" +
                "        <div class=\"pipeline-code-check\" style=\"position: relative;\">\n" +
                "            <section class=\"devops-empty-tips\">\n" +
                "                <h2 class=\"title\">前往CodeCC查看结果</h2>\n" +
                "                <p class=\"desc\">由于是异步执行，该任务检查结果需前往CodeCC服务查看</p>\n" +
                "                <p class=\"btns-row\">\n" +
                "                    <section>\n" +
                "                        <button title=\"\" class=\"bk-primary bk-button-normal bk-button\">\n" +
                "                            <a href=\"$detailLink\" target=\"_blank\">查看代码检查结果</a> \n" +
                "                        </button>\n" +
                "                    </section>\n" +
                "                </p>\n" +
                "            </section>\n" +
                "        </div>\n" +
                "    </body>\n" +
                "    <style>\n" +
                "        .pipeline-code-check {\n" +
                "            margin-top: 191px;\n" +
                "        }\n" +
                "        .devops-empty-tips {\n" +
                "            width: 913px;\n" +
                "            margin: 139px auto 0;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .devops-empty-tips .title {\n" +
                "            margin: 0 0 24px;\n" +
                "            color: #333;\n" +
                "            font-size: 18px;\n" +
                "            font-weight: normal;\n" +
                "        }\n" +
                "        .devops-empty-tips .desc {\n" +
                "            margin-bottom: 28px;\n" +
                "            color: #7b7d8a;\n" +
                "            font-size: 14px;\n" +
                "        }\n" +
                "        .bk-button-normal a {\n" +
                "            color: #fff;\n" +
                "            text-decoration: none;\n" +
                "        }\n" +
                "    </style>\n" +
                "</html>"
        )
        val indexHtml = File(tempAsyncDir, "index.html")
        indexHtml.writeText(indexHtmlBody.toString())
        return indexHtml
    }


    fun doReport(
        param: CodeccCheckAtomParamV3,
        reportData: CodeccCallback
    ) : File {
        reportData.toolSnapshotList = reportData.toolSnapshotList.filter { !it["tool_name_en"].toString().equals(ToolConstants.CLOC, true) }.toList()
        print("call back report list: ${reportData.toolSnapshotList}")
        val chartOptionJs = File(tempDir, "chart-option.js")
        val mainJs = File(tempDir, "main.js")
        val indexCss = File(tempDir, "index.css")

        chartOptionJs.writeText(getChartOptionJs())
        indexCss.writeText(getIndexCss())

        val indexHtmlBody = StringBuilder()
        // 头部
        indexHtmlBody.append("<!DOCTYPE html>\n" +
            "<html>\n" +
            "    <head>\n" +
            "        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "        <meta http-equiv=\"X-UA-Compatible\" content=\"ie=edge\">\n" +
            "        <meta charset=\"utf-8\">\n" +
            "        <link rel=\"stylesheet\" href=\"https://magicbox.bk.tencent.com/static_api/v3/components_vue/2.0/bk-magic-vue.min.css\">\n" +
            "        <link rel=\"stylesheet\" href=\"./${indexCss.name}\">\n" +
            "        <title>CodeCC报告</title>\n" +
            "        <script src=\"https://cdn.jsdelivr.net/npm/vue@2.6.10\"></script>\n" +
            "        <script src=\"http://magicbox.bk.tencent.com/static_api/v3/components_vue/2.0/bk-magic-vue.min.js\"></script>\n" +
            "        <script src=\"https://cdn.jsdelivr.net/npm/echarts@4.1.0/dist/echarts.js\"></script>\n" +
            "        <script src=\"https://cdn.jsdelivr.net/npm/vue-echarts@4.0.2\"></script>\n" +
            "    </head>\n" +
            "    <body>\n" +
            "        <div class=\"codecc-report-wrapper\" id=\"codeccReport\">\n" +
            "            <div class=\"pipeline-code-check\">\n")

        // section部分
        indexHtmlBody.append("<section class=\"code-check-header\">\n")
        indexHtmlBody.append("<p class=\"title-divider\"><span class=\"title-mark\"></span><span class=\"divider-text\">总览</span></p>\n")
        indexHtmlBody.append("<div class=\"code-check-header-wrapper\">\n")

        val json = BufferedReader(ClassLoader.getSystemClassLoader().getResourceAsStream("codecc-options.json").reader()).readText()
        val codeccOptionMap = JsonUtil.getObjectMapper().readValue<Map<String, Map<String, Any>>>(json)

        reportData.toolSnapshotList.forEach {
            val toolNameEn = it["tool_name_en"] as String
            val toolNameCn = it["tool_name_cn"] as String
            val codeccOptions = codeccOptionMap[toolNameEn] ?: getLintMap(toolNameCn)

            indexHtmlBody.append("<a class=\"code-check-head\" target='_blank' href='${it["defect_detail_url"]}'>\n")
            indexHtmlBody.append("<div class=\"code-check-title\">$toolNameCn</div>\n")
            indexHtmlBody.append("<div class=\"tool-display-circle\">\n" +
                "                                <div class=\"circle-content\"></div>\n" +
                "                            </div>\n")

            val centerContent = "<div class=\"center-content value-item\">\n" +
                "                                <div class=\"check-value\">${it[codeccOptions["mainVal"]]}</div>\n" +
                "                            </div>\n" +
                "                            <div class=\"center-content label-item\">\n" +
                "                                <div class=\"check-item\">${codeccOptions["mainKey"]}</div>\n" +
                "                            </div>\n"
            indexHtmlBody.append(centerContent)

            indexHtmlBody.append("<div class=\"charts-legend\">\n")
            val normalLegend = codeccOptions["normalLegend"] as List<Map<String, Any>>
            val firstLegend = normalLegend.first()
            val secondLegend = normalLegend[1]

            val activeChangeValue = it[codeccOptions["activeChange"]] as? Int
            val activeChange = activeChangeValue ?: 0
            val actionIcon = if (activeChange > 0) "icon-arrows-up" else if (activeChange < 0) "icon-arrows-down" else ""
            val itemStr1 = "<div class=\"legend-item\">\n" +
                "                                    <i class=\"first-index-icon\"></i>\n" +
                "                                    <label>${firstLegend["key"]}</label>\n" +
                "                                    <span class=\"legend-data\">${it[firstLegend["value"]]}</span>\n" +
                "                                    <!-- 上升、下降icon -->\n" +
                "                                    <i class=\"bk-icon $actionIcon f18\"></i>\n" +
                "                                </div>\n"
            indexHtmlBody.append(itemStr1)

            val normalChangeValue = it[codeccOptions["normalChange"]] as? Int
            val normalChange = normalChangeValue ?: 0
            val normalIcon = if (normalChange > 0) "icon-arrows-up" else if (normalChange < 0) "icon-arrows-down" else ""
            val itemStr2 = "<div class=\"legend-item\">\n" +
                "                                    <i class=\"second-index-icon\"></i>\n" +
                "                                    <label>${secondLegend["key"]}</label>\n" +
                "                                    <span class=\"legend-data\">${it[secondLegend["value"]]}</span>\n" +
                "                                    <!-- 上升、下降icon -->\n" +
                "                                    <i class=\"bk-icon $normalIcon f18\"></i>\n" +
                "                                </div>\n"
            indexHtmlBody.append(itemStr2)


            indexHtmlBody.append("</div>\n")
            indexHtmlBody.append("</a>\n")
        }

        indexHtmlBody.append("</div>\n")
        indexHtmlBody.append("</section>\n")


        // 工具分析图表
        val userId = param.pipelineStartUserName
        val toolTypeNameMap = CodeccSdkApi.getCodeccToolType(userId).map {
            it.name to it
        }.toMap()

        val metadataMap = CodeccSdkApi.getMetadataToolType(userId).map {
            it.key to it
        }.toMap()

        // 工具分类
        reportData.toolSnapshotList.groupBy {
            val toolNameEn = it["tool_name_en"] as String
            metadataMap[toolTypeNameMap[toolNameEn]?.type]?.name
        }.forEach {
            val toolType = it.key
            val toolList = it.value
            indexHtmlBody.append("<p class=\"title-divider\"><span class=\"title-mark\"></span><span class=\"divider-text\">$toolType</span></p>\n")
            toolList.forEach { tool ->
                val toolNameEn = tool["tool_name_en"] as String
                val toolNameCn = tool["tool_name_cn"] as String
                val codeccOptions = codeccOptionMap[toolNameEn] ?: getLintMap(toolNameCn)
                indexHtmlBody.append("<section class=\"code-check-row lint-check-row\">\n" +
                    "                    <div class=\"row-head\">\n" +
                    "                        <div class=\"row-head-text\">$toolNameCn</div>\n" +
                    "                        <div class=\"row-head-link\">\n" +
                    "                            <a class=\"text-link\" href='${tool["defect_report_url"]}' target='_blank'>查看详情</a>\n" +
                    "                        </div>\n" +
                    "                    </div>\n" +
                    "                    <div class=\"code-check-row-wrapper\">\n")

                val charts = codeccOptions["charts"] as List<Map<String, Any>>
                charts.forEachIndexed { index, chart ->
                    val chartType = chart["type"]
                    val chartName = "${toolNameEn}${chartType}Chart$index"
                    val chartHtml = "<div class=\"code-check-item\">\n" +
                        "                            <div class=\"chart-wrapper\" id=\"$chartName\"></div>\n" +
                        "                        </div>\n"
                    indexHtmlBody.append(chartHtml)
                }

                indexHtmlBody.append("</div>\n")
                if (toolTypeNameMap[toolNameEn]?.pattern == "LINT") indexHtmlBody.append("<p class=\"lint-tool-tips\">注：以上为起始时间后产生的新告警，鼓励该工具新告警清零。</p>\n")
                indexHtmlBody.append("</section>\n")
            }
        }

        mainJs.writeText(getMainJs(reportData, codeccOptionMap))

        // 尾部
        indexHtmlBody.append("</div>\n" +
            "        </div>\n" +
            "        <script src=\"./${chartOptionJs.name}\"></script>\n" +
            "        <script src=\"./${mainJs.name}\"></script>\n" +
            "    </body>\n" +
            "</html>\n")


        val indexHtml = File(tempDir, "index.html")
        indexHtml.writeText(indexHtmlBody.toString())
        return indexHtml
    }

    private fun getLintMap(toolName: String): Map<String, Any> {
        val lintJson = getLintJson()
        val map = JsonUtil.getObjectMapper().readValue<MutableMap<String, Any>>(lintJson)
        map["title"] = toolName
        return map
    }

    private fun getMainJs(
        reportData: CodeccCallback,
        codeccOptionMap: Map<String, Map<String, Any>>
    ): String {
        val mainJsBody = StringBuilder()
        val resizeJsBody = StringBuilder()
        mainJsBody.append("new Vue({\n" +
            "    el: '#codeccReport',\n" +
            "    mounted () {\n" +
            "        this.drawChart()\n" +
            "    },\n" +
            "    methods: {\n" +
            "        drawChart () {\n")
        reportData.toolSnapshotList.forEach {
            val toolNameEn = it["tool_name_en"] as String
            val toolNameCn = it["tool_name_cn"] as String

            val codeccOptions = codeccOptionMap[toolNameEn] ?: getLintMap(toolNameCn)
            val charts = codeccOptions["charts"] as List<Map<String, Any>>
            charts.forEachIndexed { index, chart ->
                val chartType = chart["type"]
                val chartName = "${toolNameEn}${chartType}Chart$index"

                val chartJs = when(chartType) {
                    "Bar" -> getBarJs(chart, chartName, toolNameEn, it)
                    "AuthorBar" -> getAuthorBarJs(chart, chartName, toolNameEn, it)
                    "Line" -> getLineJs(chart, chartName, toolNameEn, toolNameCn, it)
                    "Pie" -> getPipeJs(chart, chartName, it)
                    else -> ""
                }
                mainJsBody.append(chartJs).append("\n")
                resizeJsBody.append("$chartName.resize()\n")
            }
        }
        // 图表自适应
        mainJsBody.append("window.addEventListener('resize', () => {\n")
            .append(resizeJsBody)
            .append("\n})\n")
        mainJsBody.append("}\n" +
            "    }\n" +
            "})\n")
        return mainJsBody.toString()
    }

    // 柱状图
    private fun getBarJs(
        chart: Map<String, Any>,
        chartName: String,
        toolNameEn: String,
        reportData: Map<String, Any>
    ): String {
        val title = chart["title"]
        val opts = chart["opts"] as List<Map<String, String>>
        val color = opts.map { "'"+it["color"]+"'" }
        val data = opts.map { reportData[it["key"]] ?: "" }
        return " const $chartName = echarts.init(document.getElementById('$chartName'))\n" +
            "            const ${chartName}Option = JSON.parse(JSON.stringify(barOption))\n" +
            "            ${chartName}Option.title.text = '$title'\n" +
            "            ${chartName}Option.xAxis[0].data = ${opts.map { "\"${it["text"]}\"" }}\n" +
            "            ${chartName}Option.series[0].data = $data\n" +
            "            ${chartName}Option.series[0].name = '$toolNameEn'\n" +
            "            ${chartName}Option.color = $color\n" +
            "            ${chartName}Option.series[0].itemStyle.normal.color = (params) => {\n" +
            "                const colorList = $color\n" +
            "                return colorList[params.dataIndex]\n" +
            "            }\n" +
            "            $chartName.setOption(${chartName}Option)\n"
    }

    private fun getAuthorBarJs(
        chart: Map<String, Any>,
        chartName: String,
        toolNameEn: String,
        reportData: Map<String, Any>
    ): String {
        val title = chart["title"]
//        val color = chart["color"] as List<String>
        val opts = chart["opts"] as List<Map<String, String>>
        val xkey = chart["xkey"] as? String
        val ykey = chart["ykey"] as? String
        val levelList = StringBuilder()
        levelList.append("[")
        opts.forEach {
            levelList.append("{ key: '${it["key"]}', text: '${it["text"]}' },\n")
        }
        levelList.removeSuffix(",\n")
        levelList.append("]")

        val authorCount = StringBuilder()
        authorCount.append("[")
        val authorList = reportData["author_list"] as? List<Map<String, Any>>
        authorList?.forEach {
            authorCount.append("{")
            it.forEach {
                authorCount.append("${it.key} : '${it.value}',")
            }
            authorCount.removeSuffix(",")
            authorCount.append("},\n")
        }

        authorCount.removeSuffix(",\n")
        authorCount.append("]")

        val xData = authorList?.map { "'${it["name"]}'" }

        return "const $chartName = echarts.init(document.getElementById('$chartName'))\n" +
            "            const ${chartName}Option = JSON.parse(JSON.stringify(barOption))\n" +
            "            ${chartName}Option.title.text = '$title'\n" +
            "            const ${chartName}levelList = $levelList\n" +
            "            const ${chartName}authorCount = $authorCount\n" +
            "            const ${chartName}newSeriesList = []\n" +
            "            ${chartName}levelList.map((item, index) => {\n" +
            "                const temp = {\n" +
            "                    name: item.text,\n" +
            "                    type: 'bar',\n" +
            "                    stack: 'author_list',\n" +
            "                    data: [],\n" +
            "                    itemStyle: {\n" +
            "                        normal: {\n" +
            "                            color: ${chartName}Option.color[index],\n" +
            "                            barBorderRadius: [0, 0, 0, 0]\n" +
            "                        }\n" +
            "                    },\n" +
            "                    barMaxWidth: 50\n" +
            "                }\n" +
            "                ${chartName}authorCount.map(opt => {\n" +
            "                    temp.data.push(opt[item.key])\n" +
            "                })\n" +
            "                ${chartName}newSeriesList.push(temp)\n" +
            "            })\n" +
            "            ${chartName}Option.xAxis[0].data = $xData\n" +
            "            ${chartName}Option.series = ${chartName}newSeriesList\n" +
            "            $chartName.setOption(${chartName}Option)"
    }

    // 饼状图
    private fun getPipeJs(
        chart: Map<String, Any>,
        chartName: String,
        reportData: Map<String, Any>
    ): String {
        val title = chart["title"]
        val opts = chart["opts"] as List<Map<String, String>>
        val legendData = StringBuilder()
        legendData.append("[")
        opts.forEach { legendData.append("{ name: '${it["text"]}', icon: 'circle' },\n") }
        legendData.removeSuffix(",\n")
        legendData.append("]")
        val dataStr = StringBuilder()
        dataStr.append("[")
        opts.forEach {
            dataStr.append("{ value: ${reportData[it["key"]]}, name: \"${it["text"]}\" },\n")
        }
        dataStr.removeSuffix(",\n")
        dataStr.append("]")
        return "const $chartName = echarts.init(document.getElementById('$chartName'))\n" +
            "            const ${chartName}Option = JSON.parse(JSON.stringify(pieOption))\n" +
            "            ${chartName}Option.title.text = '$title'\n" +
            "            ${chartName}Option.legend.data = $legendData\n" +
            "            ${chartName}Option.series[0].data =  $dataStr\n" +
            "           const ${chartName}SeriesList = $dataStr\n" +
            "           ${chartName}Option.legend.formatter = (name) => {\n" +
            "                let target\n" +
            "                for (let i = 0; i < ${chartName}SeriesList.length; i++) {\n" +
            "                    if (${chartName}SeriesList[i].name === name) {\n" +
            "                        target = ${chartName}SeriesList[i].value\n" +
            "                    }\n" +
            "                }\n" +
            "                const arr = [name + '  ' + target]\n" +
            "\n" +
            "                return arr.join('')\n" +
            "            }\n" +
            "            $chartName.setOption(${chartName}Option)\n"
    }

    // 折线图
    private fun getLineJs(
        chart: Map<String, Any>,
        chartName: String,
        toolNameEn: String,
        toolNameCn: String,
        reportData: Map<String, Any>
    ): String {
        val title = chart["title"]
        val opts = chart["opts"] as String
        val xKey = chart["xkey"] as String
        val yKey = chart["ykey"] as String
        val dataList = reportData[opts] as List<Map<String, Any>>?
        val dataKey = dataList?.map { "'${it[xKey]}'" }
        val dataValue = dataList?.map { it[yKey] }
        return "const $chartName = echarts.init(document.getElementById('$chartName'))\n" +
            "            const ${chartName}Option = JSON.parse(JSON.stringify(lineOption))\n" +
            "            ${chartName}Option.title.text = '$title'\n" +
            "            ${chartName}Option.xAxis.data = $dataKey\n" +
            "            ${chartName}Option.series[0].data = $dataValue\n" +
            "            ${chartName}Option.series[0].name = '$toolNameCn'\n" +
            "            ${chartName}Option.series[0].itemStyle.normal.label = {\n" +
            "                show: false,\n" +
            "                positiong: 'top',\n" +
            "                formatter: '{c}%'\n" +
            "            }\n" +
            "            ${chartName}Option.yAxis.axisLabel.formatter = '{value} %'\n" +
            "            ${chartName}Option.tooltip.formatter = (params) => {\n" +
            "                let res = params.seriesName + '<br/>'\n" +
            "                res += '<span style=\"display:inline-block;margin-right:5px;border-radius:10px;width:9px;height:9px;background-color:'\n" +
            "                    + ${chartName}Option.color + '\"></span>' + params.name + '：' + params.data + '%'\n" +
            "                return res\n" +
            "            }\n" +
            "            $chartName.setOption(${chartName}Option)\n"
    }

    private fun getLintJson(): String {
        return BufferedReader(ClassLoader.getSystemClassLoader().getResourceAsStream("lint-codecc-options.json").reader()).readText()
    }

    private fun getChartOptionJs(): String {
        return BufferedReader(ClassLoader.getSystemClassLoader().getResourceAsStream("chart-option.js").reader()).readText()
    }

    private fun getIndexCss(): String {
        return BufferedReader(ClassLoader.getSystemClassLoader().getResourceAsStream("index.css").reader()).readText()
    }

    private fun getReportData(buildId: String, outputData: MutableMap<String, DataField>): CodeccCallback? {
        for (i in (1..30)) {
            val callback = api.getCodeccReport(buildId).data
            if (callback != null && callback.toolSnapshotList.isNotEmpty()) {
                println("get codecc report data success")
                val callbackFile = File.createTempFile("codecc_callback_", ".json")
                callbackFile.writeText(JsonUtil.toJson(callback))
                outputData["reportCallBack"] = ArtifactData(setOf(callbackFile.canonicalPath))
                return callback
            }
            Thread.sleep(5000)
        }
        System.err.println("获取报告数据失败")
        return null
    }

    private fun getReportDataMock(buildId: String): CodeccCallback? {
        val reportJson = BufferedReader(ClassLoader.getSystemClassLoader().getResourceAsStream("test-report.json").reader()).readText()
        return JsonUtil.getObjectMapper().readValue(reportJson)
    }

}