# 腾讯代码分析

# 配置

插件需要打包成zip包形式，分为前端、后端的打包。

[前端]
1. 替换掉src/frontend/src/index.html的__CODECC_GATEWAY_HOST__ 、 \_\_DEVOPS_GATEWAY_HOST__ 、 \_\_CODECC_GATEWAY_PORT__ 、 __DEVOPS_GATEWAY_PORT__占位符
2. cd src/frontend
3. npm install
4. npm run public

[后端]
1. cd src/backend/core
2. gradle clean buildZip
3. 用src\backend\core\build\distributions\core.zip上传
