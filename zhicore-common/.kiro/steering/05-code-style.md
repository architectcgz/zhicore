---
inclusion: manual
---

# 代码风格自动化

[返回索引](./README-zh.md)

---

## 核心原则

**代码格式必须通过自动化检查，禁止人工争论格式问题。**

---

## Java 项目配置

### 推荐工具

| 工具 | 用途 | 配置文件 |
|------|------|---------|
| **Spotless** | 自动格式化（推荐） | `pom.xml` 或 `build.gradle` |
| **Checkstyle** | 代码风格检查 | `checkstyle.xml` |
| **Google Java Format** | Google 风格格式化 | IDE 插件 |

### Maven 配置示例

```xml
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <version>2.40.0</version>
    <configuration>
        <java>
            <googleJavaFormat>
                <version>1.17.0</version>
                <style>GOOGLE</style>
            </googleJavaFormat>
            <removeUnusedImports/>
            <trimTrailingWhitespace/>
            <endWithNewline/>
        </java>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
            <phase>compile</phase>
        </execution>
    </executions>
</plugin>
```

---

## 提交前检查

```bash
# 格式化代码
mvn spotless:apply

# 检查格式（CI 环境）
mvn spotless:check

# 运行测试和格式检查
mvn clean test spotless:check
```

---

## IDE 统一配置

### IntelliJ IDEA

1. 安装 Google Java Format 插件
2. 导出代码风格配置：`File > Export Settings`
3. 团队共享配置文件：`.idea/codeStyles/`

### 配置文件位置

```
project-root/
├── .editorconfig          # 跨 IDE 配置
├── .idea/
│   └── codeStyles/
│       └── Project.xml    # IDEA 代码风格
└── checkstyle.xml         # Checkstyle 规则
```

---

## 强制规则

- [ ] 提交前必须运行 `mvn spotless:apply`
- [ ] CI 流水线必须包含 `mvn spotless:check`
- [ ] 格式检查失败时禁止合并 PR
- [ ] 团队使用统一的 IDE 配置文件

---

**最后更新**：2026-02-01
