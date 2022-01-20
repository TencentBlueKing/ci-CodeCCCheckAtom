# 腾讯代码分析

# 打包

插件需要打包成zip包形式，分为前端、后端的打包。

[前端]
1. cd bk-frontend
2. npm install
3. npm run public

[后端]
1. cd src/backend/core
2. gradle clean buildZip
3. 用src\backend\core\build\distributions\CodeCCCheckAtom.zip上传

# 配置

1) 新建插件
内容如图填写
插件调试项目按用户实际定义，一般是常用的调试项目


![image](https://user-images.githubusercontent.com/7228354/127156468-a8b85bd2-5ae3-421d-ae3b-44594b1fe03f.png)

2）需要配置四个配置项：
BK_CI_PUBLIC_URL
BK_CODECC_ENCRYPTOR_KEY（默认: abcde , 具体和codecc.properties的BK_CODECC_ENCRYPTOR_KEY对齐)
BK_CODECC_PRIVATE_URL
BK_CODECC_PUBLIC_URL
BK_CI_PUBLIC_URL
![image](https://user-images.githubusercontent.com/7228354/127156554-772f2c15-ffa8-4a9c-ab72-2fd5c1c0e93a.png)

3）上架插件
![image](https://user-images.githubusercontent.com/7228354/127156673-92a10d5a-4031-406e-b833-99d9d250e4e9.png)


需要填写的内容：
插件名称：腾讯代码分析
简介：CodeCC提供专业的代码检查解决方案，检查缺陷、漏洞、规范等多种维度代码问题，为产品质量保驾护航。
详细描述：CodeCC提供专业的代码检查解决方案，检查缺陷、漏洞、规范等多种维度代码问题，为产品质量保驾护航。
发布者：CodeCC
要选中自定义前端：
![image](https://user-images.githubusercontent.com/7228354/127156736-9a7b18e7-d6fc-4f6c-b7b6-29857caecbb9.png)

其他信息：
![image](https://user-images.githubusercontent.com/7228354/127156774-bb96fcbc-ce0e-47ad-bd5d-a14db51f979f.png)

logo：
![random_15555659555223262843762655521958](https://user-images.githubusercontent.com/7228354/127156842-306ce3bb-3609-4c23-8f9e-a8571e120400.png)


4）打包ok就点继续

![image](https://user-images.githubusercontent.com/7228354/127156912-69884151-8a08-4b38-b24d-3e69e2d84030.png)

注意：
后续升级插件，尽量不要用不兼容性升级，要选截图这个
![image](https://user-images.githubusercontent.com/7228354/127156956-ff0dd721-19b9-49c7-9ce1-3eb86c98f3f2.png)



