# API权限验证框架 - 详细使用指南

## 项目介绍

`api-permission-demo`是一个基于Spring Boot的轻量级API权限验证框架，专注于解决"参数归属型"
越权问题。通过拦截HTTP请求，提取主体参数（如当前登录用户ID）和目标参数（如操作的资源ID），并验证两者的归属关系，实现精细化权限控制。框架采用可扩展设计，支持自定义参数提取逻辑和权限验证规则，适用于各类需要严格权限管控的API服务。

## 核心功能

- **多源参数提取**：支持从URL路径（PATH）、请求体（BODY）、查询参数（QUERY）、请求头（HEADER）、Cookie中提取参数
- **灵活验证策略**：支持"全部匹配（ALL_MATCH）"和"任一匹配（ANY_MATCH）"两种多参数验证模式
- **可扩展架构**：通过接口实现自定义参数提取器和权限验证器，轻松适配业务需求
- **自动配置校验**：启动时自动验证配置合法性，存在错误时自动关闭全局开关并提示
- **线程安全设计**：使用ThreadLocal管理请求上下文，确保高并发场景下的数据隔离

## 核心组件解析

### 1. 参数提取器（ParameterExtractor）

**作用**：定义从HTTP请求中提取参数的标准逻辑，支持多种来源和解析方式。

- **核心接口**：`com.security.extractor.ParameterExtractor`
    - `extract(...)`：从请求中提取参数值列表（支持多值参数）
    - `supportParseMethod()`：返回支持的解析方式（对应`ExtractorType`枚举）
    - `supportSources()`：返回支持的参数来源（对应`ParamSource`枚举）

- **内置实现**：
    - `DefaultExtractor`：支持从QUERY/HEADER/COOKIE提取参数（解析方式：`DEFAULT`）
    - `JsonPathExtractor`：支持从JSON请求体提取参数（解析方式：`JSON_PATH`）
    - `PathMatchExtractor`：支持从URL路径变量提取参数（解析方式：`PATH_MATCH`）

### 2. 权限验证器（PermissionValidator）

**作用**：定义主体参数与目标参数的归属验证逻辑，判断当前主体是否有权限操作目标资源。

- **核心接口**：`com.security.validator.PermissionValidator`
    - `validate(principal, target)`：验证目标参数是否归属于当前主体
    - `getValidatorId()`：返回验证器唯一标识（需与配置中的`validatorId`对应）

- **验证器工厂（ValidatorFactory）**：自动扫描并注册所有`PermissionValidator`实现类，通过`validatorId`获取对应实例。

### 3. 配置与规则

- **配置类**：`ApiPermissionConfig`，通过`@ConfigurationProperties(prefix = "api.permission")`绑定yaml配置，包含全局开关和规则列表。
- **规则结构**：每个规则包含`uriPattern`（匹配的URI）、`principalParam`（主体参数配置）、`paramRules`（目标参数规则列表）、
  `multiParamMode`（多参数验证模式）。

### 4. 拦截与执行流程

1. **PermissionFilter**：预处理请求，对需要解析请求体的请求（如POST/PUT）进行缓存（`ContentCachingRequestWrapper`
   ），避免流只能读取一次的问题。
2. **PermissionInterceptor**：核心拦截逻辑，匹配URI对应的规则，调用提取器提取主体和目标参数，再通过验证器执行权限验证。
3. **上下文管理**：`PermissionContext`通过ThreadLocal存储请求过程中的参数和配置，确保线程安全。

## 快速开始

### 1. 环境依赖

- JDK 1.8+
- Spring Boot 2.7.x
- Maven 3.6+

### 2. 引入依赖

项目`pom.xml`已包含核心依赖，直接构建即可：

```xml

<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- JSON Path解析（用于从JSON请求体提取参数） -->
    <dependency>
        <groupId>com.jayway.jsonpath</groupId>
        <artifactId>json-path</artifactId>
        <version>2.7.0</version>
    </dependency>
    <!-- Lombok（简化代码） -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 3. 基础配置（application.yml）

```yaml
api:
  permission:
    enabled: true  # 全局开关：true启用验证，false关闭
    rules:
      - uri-pattern: "/staffs/{staffId}/logs/**"  # Ant风格URI匹配（支持*和**通配符）
        enabled: true  # 当前规则开关
        principal-param: # 主体参数（当前操作者标识，如员工ID）
          name: "staffId"  # 参数名
          source: "PATH"  # 参数来源：PATH/BODY/QUERY/HEADER/COOKIE
          parse-method: "PATH_MATCH"  # 解析方式：PATH_MATCH（路径变量提取）
        param-rules: # 目标参数规则列表（需验证的资源ID）
          - param-name: "userId"  # 目标参数名
            source: "BODY"  # 从请求体提取
            parse-method: "JSON_PATH"  # 使用JSONPath解析
            parse-config: "$.userId"  # JSONPath表达式（如请求体中{"userId":123}）
            validator-id: "staffId-userId"  # 关联的验证器ID
          - param-name: "classId"  # 另一个目标参数
            source: "QUERY"  # 从查询参数提取
            parse-method: "DEFAULT"  # 默认解析方式
            validator-id: "staffId-classId"  # 关联的验证器ID
        multi-param-mode: "ANY_MATCH"  # 多参数验证模式：ANY_MATCH（任一通过则放行）
```

### 4. 启动应用

```java

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

## 扩展指南

### 自定义PermissionValidator实现

#### 实现示例：验证员工与部门的归属关系

```java
package com.biz.demo.validator;

import com.security.context.PermissionContext;
import com.security.validator.PermissionValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 验证员工是否属于目标部门（员工ID -> 部门ID）
 */
@Component  // 必须标注@Component，确保被Spring扫描并注册到ValidatorFactory
public class StaffDeptValidator implements PermissionValidator {

    @Autowired
    private DeptService deptService;  // 业务服务：查询员工所属部门

    /**
     * 核心验证逻辑
     * @param principal 主体数据（当前员工信息）
     * @param target 目标参数（待验证的部门ID）
     * @return true：验证通过；false：验证失败
     */
    @Override
    public boolean validate(PermissionContext.PrincipalData principal,
                            PermissionContext.TargetParameter target) {
        // 1. 获取主体值（员工ID，此处假设为单值）
        String staffId = principal.getValues().getFirst();
        // 2. 获取目标参数值（部门ID列表，支持多值）
        List<String> deptIds = target.getValues();

        // 3. 业务验证：员工是否属于所有目标部门
        for (String deptId : deptIds) {
            boolean isAuthorized = deptService.checkStaffInDept(staffId, deptId);
            if (!isAuthorized) {
                return false;  // 任一部门不匹配则验证失败
            }
        }
        return true;  // 所有部门均匹配则验证通过
    }

    /**
     * 验证器唯一标识（需与配置中的validatorId一致）
     */
    @Override
    public String getValidatorId() {
        return "staff-dept-validator";  // 配置中需使用此ID关联
    }
}
```

#### 验证器实现原理

1. **注册机制**：所有标注`@Component`的`PermissionValidator`实现类会被`ValidatorFactory`自动扫描并注入，通过
   `getValidatorId()`作为key存储在Map中。
2. **调用时机**：拦截器提取完主体和目标参数后，根据配置的`validatorId`从`ValidatorFactory`获取对应的验证器，调用
   `validate()`方法。
3. **参数传递**：`principal`包含主体参数名和值列表（如当前员工ID），`target`包含目标参数名、值列表和验证器ID，验证器通过业务逻辑判断两者的归属关系。
4. **结果处理**：根据`multiParamMode`（ALL_MATCH/ANY_MATCH）聚合所有目标参数的验证结果，决定请求是否放行。

### 自定义ParameterExtractor实现

#### 实现示例：从XML请求体提取参数（解析方式：CUSTOM）

```java
package com.biz.demo.extractor;

import com.security.enums.ExtractorType;
import com.security.enums.ParamSource;
import com.security.extractor.ParameterExtractor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.servlet.http.HttpServletRequest;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * 从XML请求体提取参数的自定义提取器（解析方式：CUSTOM）
 */
@Component  // 必须标注@Component，确保被ExtractorFactory扫描并注册
public class XmlParamExtractor implements ParameterExtractor {

    public static final String PARSE_METHOD = "XML_PATH";

    /**
     * 核心提取逻辑
     *
     * @param request          请求对象
     * @param paramName        参数名（仅用于日志）
     * @param parseConfig      解析配置（XML XPath表达式）
     * @param source           参数来源
     * @param useCachedRequest 是否使用缓存的请求体
     * @return 提取的参数值列表
     */
    @Override
    public List<String> extract(HttpServletRequest request, String paramName, String parseConfig, ParamSource source, boolean useCachedRequest) {
        // 1. 仅处理BODY来源的参数
        if (ParamSource.BODY != source) {
            return Collections.emptyList();
        }

        // 2. 确保使用缓存的请求体（XML在请求体中，需重复读取）
        if (!useCachedRequest || !(request instanceof ContentCachingRequestWrapper)) {
            return Collections.emptyList();
        }

        // 3. 验证XPath表达式配置
        if (!StringUtils.hasText(parseConfig)) {
            return Collections.emptyList();
        }

        try {
            // 4. 从缓存请求中读取XML内容
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            byte[] contentBytes = wrapper.getContentAsByteArray();
            if (contentBytes.length == 0) {
                return Collections.emptyList();
            }
            String xmlBody = new String(contentBytes, StandardCharsets.UTF_8);

            // 5. 使用XPath解析XML
            Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xmlBody)));
            XPath xpath = XPathFactory.newInstance().newXPath();
            String result = xpath.evaluate(parseConfig, doc);  // 执行XPath表达式

            // 6. 处理结果（单值转列表）
            return result != null ? Collections.singletonList(result) : Collections.emptyList();
        } catch (Exception e) {
            // 提取失败返回空列表（可根据业务需求抛出异常）
            return Collections.emptyList();
        }
    }

    /**
     * 自定义名称解析方式。
     */
    @Override
    public String supportParseMethod() {
        return PARSE_METHOD;
    }

    /**
     * 支持的参数来源（此提取器仅支持BODY）
     */
    @Override
    public List<ParamSource> supportSources() {
        return Collections.singletonList(ParamSource.BODY);
    }
}
```

#### 提取器实现原理

1. **ExtractorType与注册机制**：
    - `ExtractorType`枚举中的`CUSTOM`类型作为所有自定义提取器的统一标识。自定义提取器（如`XmlParamExtractor`）通过
      `supportParseMethod()`方法返回自身定义的唯一名称（如"XML_PATH"），`ExtractorFactory`会以该名称为键，将提取器实例注册到映射表中进行管理。
    - 当配置文件中`parse-method`指定为自定义名称（如"XML_PATH"）时，`ExtractorType.fromString()`方法会因该名称无法匹配任何内置枚举值，自动将其识别为
      `CUSTOM`类型。同时，系统会通过`getExtractorType()`方法生成带前缀的标识（如"CUSTOM#XML_PATH"）。

2. **提取流程**：
    - 在配置文件中直接指定自定义解析方式的名称（如`parse-method: "XML_PATH"`）即可，无需显式声明为`CUSTOM`类型。
    - 框架通过`ExtractorType.fromString("XML_PATH")`解析得到`CUSTOM`枚举值后，会基于原始配置的"XML_PATH"名称，从
      `ExtractorFactory`的映射表中精准匹配到对应的`XmlParamExtractor`实例。
    - 最终调用`XmlParamExtractor`的`extract()`方法完成参数提取，整个过程通过原始自定义名称实现配置与提取器的精准绑定。

3. **来源与解析适配**：
    - 自定义提取器通过`supportSources()`方法声明自身支持的参数来源（例如`XmlParamExtractor`可声明仅支持`BODY`来源）。
    - 框架在初始化阶段会自动校验：配置中指定的参数来源（如`source: "BODY"`）是否在提取器声明的支持列表中。若不匹配，将触发配置错误提示，确保提取逻辑与参数来源的兼容性。

4. **缓存请求体**：
    - 对于`BODY`来源的参数（如XML格式请求体），`PermissionFilter`会自动将请求对象包装为`ContentCachingRequestWrapper`
      ，实现请求体的缓存与重复读取（解决HTTP请求流只能读取一次的问题）。
    - 自定义提取器（如`XmlParamExtractor`）通过`useCachedRequest`参数判断是否使用缓存的请求体。当`useCachedRequest=true`
      时，提取器会从缓存中读取XML内容并执行解析，避免因流已关闭导致的读取失败。

### 自定义组件的配置使用

在yaml中配置使用自定义提取器和验证器：

```yaml
api:
  permission:
    rules:
      - uri-pattern: "/depts/**"
        enabled: true
        principal-param:
          name: "staffId"
          source: "HEADER"
          parse-method: "DEFAULT"  # 使用内置DefaultExtractor
        param-rules:
          - param-name: "deptId"
            source: "BODY"  # 从XML请求体提取
            parse-method: "XML_PATH"  # 使用自定义XmlParamExtractor
            parse-config: "/root/dept/id"  # XML XPath表达式
            validator-id: "staff-dept-validator"  # 使用自定义验证器
        multi-param-mode: "ALL_MATCH"
```

## 配置详解（yaml格式）

### 核心配置结构

```yaml
api:
  permission:
    enabled: true  # 全局开关（true/false）
    rules: # 验证规则列表
      - uri-pattern: "/api/**"  # Ant风格URI模式（必须以/开头）
        enabled: true  # 规则开关
        principal-param: # 主体参数配置（当前操作者）
          name: "staffId"  # 参数名（不能为空）
          source: "PATH"  # 参数来源（PATH/BODY/QUERY/HEADER/COOKIE）
          parse-method: "PATH_MATCH"  # 解析方式（需与source匹配）
          parse-config: ""  # 解析配置（如JSONPath/XPath表达式，非必须）
        param-rules: # 目标参数规则列表（至少1个）
          - param-name: "resourceId"  # 目标参数名
            source: "BODY"  # 参数来源
            parse-method: "JSON_PATH"  # 解析方式
            parse-config: "$.resourceId"  # JSONPath表达式（JSON_PATH必填）
            validator-id: "staff-resource-validator"  # 验证器ID（不能为空）
        multi-param-mode: "ALL_MATCH"  # 多参数模式（ALL_MATCH/ANY_MATCH）
```

### 关键配置说明

1. **uri-pattern**：Ant风格的URI匹配模式，如`/users/{id}/**`（`*`匹配一级路径，`**`匹配多级路径），必须以`/`开头。

2. **principal-param.source与parse-method匹配关系**：
    - `PATH`：仅支持`PATH_MATCH`或`DEFAULT`
    - `BODY`：支持`JSON_PATH`（内置）或`CUSTOM`（自定义）
    - `QUERY/HEADER/COOKIE`：仅支持`DEFAULT`

3. **parse-config**：
    - `JSON_PATH`：必填，如`$.user.id`（JSONPath表达式）
    - `CUSTOM`：根据自定义提取器需求填写（如XML的XPath表达式）
    - 其他方式：可选（一般留空）

4. **multi-param-mode**：
    - `ALL_MATCH`：所有目标参数验证通过才放行
    - `ANY_MATCH`：任一目标参数验证通过即放行

## 注意事项

1. **请求体缓存限制**：文件上传请求（`Content-Type`以`multipart/`开头）不会缓存请求体，避免内存溢出，此类请求的BODY参数提取会失败。
2. **配置校验**：启动时框架会自动验证配置合法性（如`source`与`parse-method`是否匹配、必填项是否缺失），错误会记录日志并关闭全局开关。
3. **参数多值处理**：提取器支持返回多值参数（如QUERY参数`?ids=1&ids=2`），验证器需处理`List<String>`类型的参数值。
4. **自定义组件扫描**：自定义提取器和验证器必须放在Spring扫描路径下（标注`@Component`），否则无法被工厂类注册。
5. **性能考虑**：请求体解析（如JSON/XML）会产生额外开销，建议仅对敏感接口启用验证。
6. **[ParamSource.java](src/main/java/com/security/enums/ParamSource.java)**
    - 定义参数来源的枚举类型，包括`PATH`、`BODY`、`QUERY`、`HEADER`、`COOKIE`、`SESSION`。
    - 包含`supportSources()`方法，用于判断当前枚举值是否支持特定的参数来源，不支持其他类型的参数来源，如果自定义服务会启动失败。
7. **[ExtractorType.java](src/main/java/com/security/enums/ExtractorType.java)**
    - 定义参数提取器的枚举类型，包括`DEFAULT`、`JSON_PATH`、`PATH_MATCH`、`CUSTOM`。如果对相同的ParamSource提供不同解析器（CUSTOM除外），会自动覆盖默认的，
    - 如果需要支持多个解析器，需要自己实现扩展能力

