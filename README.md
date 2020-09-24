# 使用java开发插件示例

可以按照如下步骤一一开发插件，修改配置后体验插件开发流程
示例插件代码库地址：

### Step 1. 登录蓝盾插件市场-插件工作台初始化插件

- [点此登录插件工作台]()
- 新增插件时，系统将自动创建代码库并将样例工程添加至你的代码库中，可在插件概览页面复制代码库链接



### Step 2. 引入项目所需的jar包

- 将maven的setting.xml的配置改为蓝鲸提供的，配置文件内容如下：

```
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">
    <mirrors>
    </mirrors>
    <profiles>
        <profile>
            <id>bk_mirror</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <repositories>
                <repository>
                    <id>bk_mirror</id>
                    <url></url>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>bk_mirror</id>
                    <url></url>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>bk_mirror</activeProfile>
    </activeProfiles>
</settings>
```



- java-atom-sdk为官方提供的sdk包，初始化版本为1.0.0（详见：[java版插件开发sdk]()）
- sdk-dependencies为官方提供的sdk依赖的包，初始化版本为1.0.0



```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.tencent.bk.devops.atom</groupId>
        <artifactId>sdk-dependencies</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>atomDemoJava</artifactId>
    <version>1.0.0</version>

    <properties>
        <!-- 引用的sdk版本，当sdk版本有升级时再修改 -->
        <sdk.version>1.0.0</sdk.version>
        <java.version>1.8</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <!--项目版本 -->
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    </properties>

    <dependencies>
        <dependency>
            <groupId>com.tencent.bk.devops.atom</groupId>
            <artifactId>java-atom-sdk</artifactId>
            <version>${sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.12</version>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.name}</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```



### Step 3. 修改代码库根目录下的pom.xml文件

- 将artifactId节点的ATOM_CODE替换为你的插件标识名称（初始化插件时填写的英文标识）
- 将java.version节点的内容替换为你插件所需要的sdk版本（详见：[java版插件开发sdk]()）

```
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.tencent.bk.devops.atom</groupId>
        <artifactId>sdk-dependencies</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>ATOM_CODE</artifactId>
    <version>1.0.0</version>

    <properties>
        <!-- 引用的sdk版本，当sdk版本有升级时再修改 -->
        <sdk.version>1.0.0</sdk.version>
        <java.version>1.8</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <!--项目版本 -->
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    </properties>

    <dependencies>
        <dependency>
            <groupId>com.tencent.bk.devops.atom</groupId>
            <artifactId>java-atom-sdk</artifactId>
            <version>${sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.12</version>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.name}</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```







### Step 4. 代码库根目录下添加蓝盾插件配置文件：task.json

task.jaon配置规则详见：[插件开发规范]()

task.json简单示例如下：

- 必填项：

1.  修改atomCode为你的插件标识名称（初始化插件时填写的英文标识）
2.  修改执行配置execution.target为你的插件启动命令
3.  修改输入输出字段定义

- 非必填项：

1.  若调起执行前需安装依赖则无需填写执行配置execution.demands

```
{
  "atomCode": "ATOM_CODE,
  "execution": {
    "language": "java",
    "minimumVersion": "1.8",
    "demands": [],
    "target": "java -jar ATOM_CODE-jar-with-dependencies.jar"
  },
  "input": {
    "desc": {
      "label": "描述",
      "default": "",
      "placeholder": "请输入描述信息",
      "type": "vuex-input",
      "desc": "描述",
      "required": true,
      "disabled": false,
      "hidden": false,
      "isSensitive": false
    }
  },
  "output": {
    "testResult": {
      "description": "升级是否成功",
      "type": "string",
      "isSensitive": false
    }
  }
}
```



### Step 5. 定义继承sdk包中的AtomBaseParam插件基本参数类的插件参数类

- 参数类需统一加上lombok框架的@Data注解(IDE开发工具需安装lombok插件)，参数类定义的参数类型统一为String格式

```
@Data
@EqualsAndHashCode(callSuper = true)
public class AtomParam extends AtomBaseParam {
    /**
     * 以下请求参数只是示例，具体可以删除修改成你要的参数
     */
    private String desc; //描述信息
}
```



### Step 6. 定义实现sdk包中的TaskAtom接口的插件任务类

- 插件任务类必须实现sdk包中的TaskAtom接口
- 插件任务类必须加上“@AtomService(paramClass = AtomParam.class)”注解才能被sdk识别和执行（paramClass对应的值为定义的参数类文件名）

```
@AtomService(paramClass = AtomParam.class)
public class DemoAtom implements TaskAtom<AtomParam> {

    private final static Logger logger = LoggerFactory.getLogger(DemoAtom.class);

    /**
     * 执行主入口
     * @param atomContext 插件上下文
     */
    @Override
    public void execute(AtomContext<AtomParam> atomContext) {
        // 1.1 拿到请求参数
        AtomParam param = atomContext.getParam();
        logger.info("the param is :{}", JsonUtil.toJson(param));
        // 1.2 拿到初始化好的返回结果对象
        AtomResult result = atomContext.getResult();
        // 2. 校验参数失败直接返回
        checkParam(param, result);
        if (result.getStatus() != Status.success) {
            return;
        }
        // 3. 模拟处理插件业务逻辑
        logger.info("the desc is :{}", param.getDesc()); //打印描述信息
        // 4. 输出参数，如果有的话
        // 输出参数是一个Map,Key是参数名， value是值对象
        Map<String, DataField> data = result.getData();
        // 假设这个是输出参数的内容
        StringData testResult = new StringData("hello");
        // 设置一个名称为testResult的出参
        data.put("testResult", testResult);
        logger.info("the testResult is :{}", JsonUtil.toJson(testResult));
        // 结束。
    }

    /**
     * 检查参数
     * @param param  请求参数
     * @param result 结果
     */
    private void checkParam(AtomParam param, AtomResult result) {
        // 参数检查
        if (StringUtils.isBlank(param.getDesc())) {
            result.setStatus(Status.failure);// 状态设置为失败
            result.setMessage("描述不能为空!"); // 失败信息回传给插件执行框架会打印出结果
        }

        /*
         其他比如判空等要自己业务检测处理，否则后面执行可能会抛出异常，状态将会是 Status.error
         这种属于插件处理不到位，算是bug行为，需要插件的开发去定位
          */
    }

}
```



### Step 7. 配置TaskAtom接口的spi实现类

1. 在 src/main/resources/ 下建立 /META-INF/services 目录， 新增一个以接口命名的文件 com.tencent.bk.devops.atom.spi.TaskAtom
2. 文件里面的内容是定义的实现spi接口的插件任务类，如：com.tencent.bk.devops.atom.task.DemoAtom

