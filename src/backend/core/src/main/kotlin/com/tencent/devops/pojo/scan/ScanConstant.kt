package com.tencent.devops.pojo.scan

const val LARGE_REPORT_HEAD_HTML = "<!DOCTYPE html>\n" +
    "<html>\n" +
    "\n" +
    "<head>\n" +
    "    <meta charset=\"UTF-8\">\n" +
    "    <title>large.report.head.html.title</title>\n" +
    "\t<style>\n" +
    "        body {\n" +
    "            font-family: Arial, \"Helvetica Neue\", Helvetica, sans-serif;\n" +
    "            font-size: 16px;\n" +
    "            font-weight: normal;\n" +
    "            margin: 0;\n" +
    "            padding: 0;\n" +
    "            color: #333\n" +
    "        }\n" +
    "\n" +
    "        #overview {\n" +
    "            padding: 20px 30px\n" +
    "        }\n" +
    "\n" +
    "        td,\n" +
    "        th {\n" +
    "            padding: 5px 10px\n" +
    "        }\n" +
    "\n" +
    "        h1 {\n" +
    "            margin: 0\n" +
    "        }\n" +
    "\n" +
    "        table {\n" +
    "            margin: 30px;\n" +
    "            width: calc(100% - 60px);\n" +
    "            max-width: 1000px;\n" +
    "            border-radius: 5px;\n" +
    "            border: 1px solid #ddd;\n" +
    "            border-spacing: 0px;\n" +
    "        }\n" +
    "\n" +
    "        th {\n" +
    "            font-weight: 400;\n" +
    "            font-size: medium;\n" +
    "            text-align: left;\n" +
    "            cursor: pointer\n" +
    "        }\n" +
    "\n" +
    "        td.clr-1,\n" +
    "        td.clr-2,\n" +
    "        th span {\n" +
    "            font-weight: 700\n" +
    "        }\n" +
    "\n" +
    "        th span {\n" +
    "            float: right;\n" +
    "            margin-left: 20px\n" +
    "        }\n" +
    "\n" +
    "        th span:after {\n" +
    "            content: \"\";\n" +
    "            clear: both;\n" +
    "            display: block\n" +
    "        }\n" +
    "\n" +
    "        tr:last-child td {\n" +
    "            border-bottom: none\n" +
    "        }\n" +
    "\n" +
    "\n" +
    "\n" +
    "        #overview.bg-0,\n" +
    "        tr.bg-0 th {\n" +
    "            color: #468847;\n" +
    "            background: #dff0d8;\n" +
    "            border-bottom: 1px solid #d6e9c6\n" +
    "        }\n" +
    "\n" +
    "        #overview.bg-1,\n" +
    "        tr.bg-1 th {\n" +
    "            color: #f0ad4e;\n" +
    "            background: #fcf8e3;\n" +
    "            border-bottom: 1px solid #fbeed5\n" +
    "        }\n" +
    "\n" +
    "        #overview.bg-2,\n" +
    "        tr.bg-2 th {\n" +
    "            color: #b94a48;\n" +
    "            background: #f2dede;\n" +
    "            border-bottom: 1px solid #eed3d7\n" +
    "        }\n" +
    "\n" +
    "        td {\n" +
    "            border-bottom: 1px solid #ddd\n" +
    "        }\n" +
    "\n" +
    "        td.clr-1 {\n" +
    "            color: #f0ad4e\n" +
    "        }\n" +
    "\n" +
    "        td.clr-2 {\n" +
    "            color: #b94a48\n" +
    "        }\n" +
    "\n" +
    "        td a {\n" +
    "            color: #3a33d1;\n" +
    "            text-decoration: none\n" +
    "        }\n" +
    "\n" +
    "        td a:hover {\n" +
    "            color: #272296;\n" +
    "            text-decoration: underline\n" +
    "        }\n" +
    "    </style>\n" +
    "</head>\n" +
    "\n" +
    "<body>\n" +
    "    <div id=\"overview\">\n" +
    "        <h1>large.report.head.html.h1</h1>\n" +
    "\t\t<br/>\n" +
    "        <div>\n"

const val LARGE_REPORT_TAIL_HTML = "\n" +
    "        </tbody>\n" +
    "    </table>\n" +
    "    <script type=\"text/javascript\">\n" +
    "        var groups = document.querySelectorAll(\"tr[data-group]\");\n" +
    "        for (i = 0; i < groups.length; i++) {\n" +
    "            groups[i].addEventListener(\"click\", function () {\n" +
    "                var inGroup = document.getElementsByClassName(this.getAttribute(\"data-group\"));\n" +
    "                this.innerHTML = (this.innerHTML.indexOf(\"+\") > -1) ? this.innerHTML.replace(\"+\", \"-\") : this.innerHTML.replace(\"-\", \"+\");\n" +
    "                for (var j = 0; j < inGroup.length; j++) {\n" +
    "                    inGroup[j].style.display = (inGroup[j].style.display !== \"none\") ? \"none\" : \"table-row\";\n" +
    "                }\n" +
    "            });\n" +
    "        }\n" +
    "    </script>\n" +
    "</body>\n" +
    "</html>"