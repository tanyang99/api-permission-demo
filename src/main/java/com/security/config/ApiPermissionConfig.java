package com.security.config;

import com.security.enums.ExtractorType;
import com.security.enums.MultiParamMode;
import com.security.enums.ParamSource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API权限配置类（完全手动实现验证逻辑，不依赖注解校验器）
 */
@Data
@Component
@ConfigurationProperties(prefix = "api.permission")
@Slf4j
public class ApiPermissionConfig implements InitializingBean {

    private boolean enabled = false; // 全局开关（默认关闭）
    private List<Rule> rules; // 规则列表（移除@Valid注解）

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("开始验证API权限配置...");

        // 1. 全局配置验证（收集所有错误）
        List<String> allErrors = new ArrayList<>(validate());

        // 2. 统一处理错误
        if (!allErrors.isEmpty()) {
            log.error("配置验证失败，共发现{}个错误：", allErrors.size());
            allErrors.forEach(error -> log.error("- {}", error));

            // 自动关闭全局开关
            if (enabled) {
                log.error("因配置错误，自动关闭全局开关");
                this.enabled = false;
            }
        } else {
            log.info("API权限配置验证通过");
        }
    }

    /**
     * 全局配置手动验证
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();

        // 验证规则列表非空（全局开关开启时）
        if (enabled && (rules == null || rules.isEmpty())) {
            errors.add("全局开关已开启，但未配置任何规则（rules不能为空）");
        }

        // 验证每个规则
        if (rules != null) {
            for (Rule rule : rules) {
                // 为规则错误添加标识
                List<String> ruleErrors = rule.validate().stream().map(error -> "规则[" + (rule.getUriPattern() != null ? rule.getUriPattern() : "未设置URI") + "]：" + error).collect(Collectors.toList());
                errors.addAll(ruleErrors);

                // 禁用有错误的规则
                if (!ruleErrors.isEmpty()) {
                    rule.setEnabled(false);
                }
            }
        }

        return errors;
    }

    /**
     * 接口级规则（手动验证所有参数）
     */
    @Data
    public static class Rule {
        private String uriPattern; // 移除@NotBlank注解
        private boolean enabled = false;

        private PrincipalParam principalParam; // 移除@NotNull和@Valid注解

        private List<ParamRule> paramRules; // 移除@NotEmpty和@Valid注解

        private MultiParamMode multiParamMode = MultiParamMode.ANY_MATCH; // 移除@NotNull注解

        /**
         * 规则参数手动验证
         */
        public List<String> validate() {
            List<String> errors = new ArrayList<>();

            // 未启用的规则也验证基础结构（避免配置完全无效）
            if (!enabled) {
                log.debug("规则[{}]未启用，仅验证基础配置", uriPattern);
            }

            // 1. 验证uriPattern非空及格式
            if (uriPattern == null || uriPattern.trim().isEmpty()) {
                errors.add("uriPattern不能为空（需指定Ant风格URI，如/api/users/**）");
            } else if (!uriPattern.startsWith("/")) {
                errors.add("uriPattern必须以/开头（Ant风格）");
            }

            // 2. 验证主体参数
            if (principalParam == null) {
                errors.add("principalParam不能为空（需配置主体参数）");
            } else {
                // 为主体参数错误添加标识
                List<String> principalErrors = principalParam.validate().stream().map(error -> "主体参数：" + error).collect(Collectors.toList());
                errors.addAll(principalErrors);
            }

            // 3. 验证目标参数列表
            if (paramRules == null || paramRules.isEmpty()) {
                errors.add("paramRules不能为空（需至少配置一个目标参数规则）");
            } else {
                // 验证每个目标参数
                for (ParamRule paramRule : paramRules) {
                    if (paramRule == null) {
                        errors.add("paramRules中存在空对象");
                        continue;
                    }
                    // 为目标参数错误添加标识
                    String paramName = paramRule.getParamName() != null ? paramRule.getParamName() : "未命名参数";
                    List<String> paramErrors = paramRule.validate().stream().map(error -> "参数[" + paramName + "]：" + error).collect(Collectors.toList());
                    errors.addAll(paramErrors);
                }
            }

            // 4. 验证多参数模式
            if (multiParamMode == null) {
                errors.add("multiParamMode不能为空（需指定ALL_MATCH/ANY_MATCH）");
            } else if (multiParamMode != MultiParamMode.ALL_MATCH && multiParamMode != MultiParamMode.ANY_MATCH) {
                errors.add("multiParamMode必须为ALL_MATCH或ANY_MATCH，当前值：" + multiParamMode);
            }

            // 5. 验证参数名重复（仅当参数列表有效时）
            if (paramRules != null && !paramRules.isEmpty() && errors.stream().noneMatch(e -> e.contains("paramRules不能为空"))) {
                List<String> duplicates = getDuplicateParamNames();
                if (!duplicates.isEmpty()) {
                    errors.add("存在重复参数名：" + duplicates);
                }
            }

            return errors;
        }

        // 获取重复的参数名
        private List<String> getDuplicateParamNames() {
            return paramRules.stream().filter(paramRule -> paramRule.getParamName() != null && !paramRule.getParamName().trim().isEmpty()).map(ParamRule::getParamName).collect(Collectors.groupingBy(name -> name, Collectors.counting())).entrySet().stream().filter(entry -> entry.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toList());
        }
    }

    /**
     * 主体参数配置（手动验证所有参数）
     */
    @Data
    public static class PrincipalParam {
        private String name; // 移除@NotBlank注解
        private ParamSource source; // 移除@NotNull注解
        private String parseMethod; // 移除@NotBlank注解
        private String parseConfig;

        /**
         * 主体参数手动验证
         */
        public List<String> validate() {
            List<String> errors = new ArrayList<>();

            // 1. 验证name非空
            if (name == null || name.trim().isEmpty()) {
                errors.add("name不能为空（需指定主体参数名，如staffId）");
            }

            // 2. 验证source非空及合法性
            if (source == null) {
                errors.add("source不能为空（需指定PATH/BODY/QUERY等）");
            } else {
                // 验证source是否为枚举中的有效值（防止反序列化异常）
                boolean isValidSource = false;
                for (ParamSource s : ParamSource.values()) {
                    if (s == source) {
                        isValidSource = true;
                        break;
                    }
                }
                if (!isValidSource) {
                    errors.add("source值无效：" + source);
                }
            }

            // 3. 验证parseMethod非空及合法性
            if (parseMethod == null || parseMethod.trim().isEmpty()) {
                errors.add("parseMethod不能为空（需指定解析方式）");
            } else {
                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
                if (ExtractorType.NONE.equals(extractorType)) {
                    errors.add("parseMethod值无效：" + parseMethod);
                }
            }

//            // 4. 验证PATH来源时的URI参数占位符（需结合父级规则的uriPattern，此处先做基础判断）
//            if (source == ParamSource.PATH && name != null && !name.trim().isEmpty()) {
//                // 注意：uriPattern在父级规则中，此处无法直接验证，需在Rule.validate()中补充
//                errors.add("来源为PATH时，uriPattern必须包含参数{" + name + "}");
//            }

            // 5. 验证解析方式与来源的匹配性
            if (source != null && parseMethod != null && !parseMethod.trim().isEmpty()) {
                if (!isValidCombination(source, parseMethod)) {
                    errors.add("来源[" + source + "]不支持解析方式[" + parseMethod + "]");
                }
            }

            // 6. 验证必要的parseConfig
            if (parseMethod != null && !parseMethod.trim().isEmpty()) {
                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
                if (ExtractorType.JSON_PATH.equals(extractorType) && (parseConfig == null || parseConfig.trim().isEmpty())) {
                    errors.add("解析方式[" + extractorType + "]必须配置parseConfig");
                }
            }

            return errors;
        }

        // 验证来源与解析方式的匹配性
        private boolean isValidCombination(ParamSource source, String parseMethod) {
            ExtractorType extractorType = ExtractorType.fromString(parseMethod);
            // 自定义类型默认支持所有来源（可根据实际需求修改）
            if (ExtractorType.CUSTOM.equals(extractorType)) {
                return true;
            }

            switch (source) {
                case PATH:
                    return ExtractorType.DEFAULT.equals(extractorType) || ExtractorType.PATH_MATCH.equals(extractorType);
                case BODY:
                    return ExtractorType.JSON_PATH.equals(extractorType);
                case QUERY:
                case HEADER:
                case COOKIE:
                    return ExtractorType.DEFAULT.equals(extractorType);
                default:
                    return false;
            }
        }
    }

    /**
     * 目标参数规则（手动验证所有参数）
     */
    @Data
    public static class ParamRule {
        private String paramName;
        private ParamSource source;
        private String parseMethod;
        private String parseConfig;
        private String validatorId;

        /**
         * 目标参数手动验证
         */
        public List<String> validate() {
            List<String> errors = new ArrayList<>();

            // 1. 验证paramName非空
            if (paramName == null || paramName.trim().isEmpty()) {
                errors.add("paramName不能为空（需指定参数名，如userId）");
            }

            // 2. 验证source非空及合法性
            if (source == null) {
                errors.add("source不能为空（需指定PATH/BODY/QUERY等）");
            } else {
                boolean isValidSource = false;
                for (ParamSource s : ParamSource.values()) {
                    if (s == source) {
                        isValidSource = true;
                        break;
                    }
                }
                if (!isValidSource) {
                    errors.add("source值无效：" + source);
                }
            }

            // 3. 验证parseMethod非空及合法性
            if (parseMethod == null || parseMethod.trim().isEmpty()) {
                errors.add("parseMethod不能为空");
            } else {
                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
                if (ExtractorType.NONE.equals(extractorType)) {
                    errors.add("parseMethod值无效：" + parseMethod);
                }
            }

            // 4. 验证validatorId非空及格式
            if (validatorId == null || validatorId.trim().isEmpty()) {
                errors.add("validatorId不能为空（需指定验证器ID）");
            }

            // 5. 验证解析方式与来源的匹配性
            if (source != null && parseMethod != null && !parseMethod.trim().isEmpty()) {
                if (!isValidCombination(source, parseMethod)) {
                    errors.add("来源[" + source + "]不支持解析方式[" + parseMethod + "]");
                }
            }

            // 6. 验证必要的parseConfig
            if (parseMethod != null && !parseMethod.trim().isEmpty()) {
                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
                if (ExtractorType.JSON_PATH.equals(extractorType) && (parseConfig == null || parseConfig.trim().isEmpty())) {
                    errors.add("解析方式[" + extractorType + "]必须配置parseConfig");
                }
            }

            return errors;
        }

        // 验证来源与解析方式的匹配性
        private boolean isValidCombination(ParamSource source, String parseMethod) {
            ExtractorType extractorType = ExtractorType.fromString(parseMethod);
            switch (source) {
                case PATH:
                    return ExtractorType.PATH_MATCH.equals(extractorType);
                case BODY:
                    return ExtractorType.JSON_PATH.equals(extractorType);
                case QUERY:
                case HEADER:
                case COOKIE:
                    return ExtractorType.DEFAULT.equals(extractorType);
                default:
                    return false;
            }
        }
    }
}
