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
import java.util.Arrays;
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
    private List<Rule> rules;

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

    // ApiPermissionConfig类中
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        // 仅负责全局配置验证
        validateGlobalConfig(errors);
        // 规则验证委托给规则自身
        validateRules(errors);
        return errors;
    }

    // 全局配置验证（独立方法）
    private void validateGlobalConfig(List<String> errors) {
        if (enabled && (rules == null || rules.isEmpty())) {
            errors.add("全局开关已开启，但未配置任何规则（rules不能为空）");
        }
    }

    // 规则列表验证（独立方法）
    private void validateRules(List<String> errors) {
        if (rules != null) {
            for (Rule rule : rules) {
                List<String> ruleErrors = rule.validate().stream()
                        .map(error -> "规则[" + (rule.getUriPattern() != null ? rule.getUriPattern() : "未设置URI") + "]：" + error)
                        .collect(Collectors.toList());
                errors.addAll(ruleErrors);
                if (!ruleErrors.isEmpty()) {
                    rule.setEnabled(false);
                }
            }
        }
    }

    /**
     * 接口级规则（手动验证所有参数）
     */
    @Data
    public static class Rule {
        private String uriPattern;
        private boolean enabled = false;

        private PrincipalParam principalParam;

        private List<ParamRule> paramRules;

        private MultiParamMode multiParamMode = MultiParamMode.ANY_MATCH;

        /**
         * 规则参数手动验证
         */
        // Rule类中
        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            // 1. 自身属性验证（uriPattern、multiParamMode）
            validateBasicProperties(errors);
            // 2. 主体参数验证（委托给PrincipalParam）
            validatePrincipalParam(errors);
            // 3. 目标参数验证（委托给ParamRule）
            validateParamRules(errors);
            // 4. 跨参数验证（如重复参数名）
            validateCrossParamConstraints(errors);
            return errors;
        }

        // 1. 基础属性验证（uriPattern格式、multiParamMode）
        private void validateBasicProperties(List<String> errors) {
            if (uriPattern == null || uriPattern.trim().isEmpty()) {
                errors.add("uriPattern不能为空（需指定Ant风格URI，如/api/users/**）");
            } else if (!uriPattern.startsWith("/")) {
                errors.add("uriPattern必须以/开头（Ant风格）");
            }
            if (multiParamMode == null) {
                errors.add("multiParamMode不能为空（需指定ALL_MATCH/ANY_MATCH）");
            } else if (multiParamMode != MultiParamMode.ALL_MATCH && multiParamMode != MultiParamMode.ANY_MATCH) {
                errors.add("multiParamMode必须为ALL_MATCH或ANY_MATCH，当前值：" + multiParamMode);
            }
        }

        // 2. 主体参数验证
        private void validatePrincipalParam(List<String> errors) {
            if (principalParam == null) {
                errors.add("principalParam不能为空（需配置主体参数）");
            } else {
                List<String> principalErrors = principalParam.validate().stream()
                        .map(error -> "主体参数：" + error)
                        .collect(Collectors.toList());
                errors.addAll(principalErrors);
            }
        }

        // 3. 目标参数验证
        private void validateParamRules(List<String> errors) {
            if (paramRules == null || paramRules.isEmpty()) {
                errors.add("paramRules不能为空（需至少配置一个目标参数规则）");
                return;
            }
            for (ParamRule paramRule : paramRules) {
                if (paramRule == null) {
                    errors.add("paramRules中存在空对象");
                    continue;
                }
                String paramName = paramRule.getParamName() != null ? paramRule.getParamName() : "未命名参数";
                List<String> paramErrors = paramRule.validate().stream()
                        .map(error -> "参数[" + paramName + "]：" + error)
                        .collect(Collectors.toList());
                errors.addAll(paramErrors);
            }
        }

        // 4. 跨参数约束验证（如重复参数名）
        private void validateCrossParamConstraints(List<String> errors) {
            if (paramRules == null || paramRules.isEmpty()) return;
            // 检查是否已有paramRules为空的错误，有则跳过
            if (errors.stream().anyMatch(e -> e.contains("paramRules不能为空"))) return;

            List<String> duplicates = getDuplicateParamNames();
            if (!duplicates.isEmpty()) {
                errors.add("存在重复参数名：" + duplicates);
            }
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
        private String name;
        private ParamSource source;
        private String parseMethod;
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
            boolean isSourceValid = false;
            if (source == null) {
                errors.add("source不能为空（需指定PATH/BODY/QUERY等）");
            } else {
                isSourceValid = ParamSource.contains(source);
                if (!isSourceValid) {
                    errors.add("source值无效：" + source);
                } else {
                    // 当source有效且parseMethod为空时，自动赋值
                    if (parseMethod == null || parseMethod.trim().isEmpty()) {
                        if (ParamSource.PATH.equals(source)) {
                            parseMethod = ExtractorType.PATH_MATCH.name();
                        } else if (Arrays.asList(ParamSource.QUERY, ParamSource.HEADER, ParamSource.COOKIE).contains(source)) {
                            parseMethod = ExtractorType.DEFAULT.name();
                        }
                        // 其他source（如BODY）不自动赋值，保留后续验证
                    }
                }
            }

            // 3. 验证parseMethod非空及合法性（此时可能已被自动赋值）
            if (parseMethod == null || parseMethod.trim().isEmpty()) {
                errors.add("parseMethod不能为空（需指定解析方式）");
            } else {
                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
                if (ExtractorType.NONE.equals(extractorType)) {
                    errors.add("parseMethod值无效：" + parseMethod);
                }
            }

            // 4. 验证解析方式与来源的匹配性
            if (isSourceValid && parseMethod != null && !parseMethod.trim().isEmpty()) {
                if (!isValidCombination(source, parseMethod)) {
                    errors.add("来源[" + source + "]不支持解析方式[" + parseMethod + "]");
                }
            }

            // 5. 验证必要的parseConfig
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
            boolean isSourceValid = false;
            if (source == null) {
                errors.add("source不能为空（需指定PATH/BODY/QUERY等）");
            } else {
                isSourceValid = ParamSource.contains(source);
                if (!isSourceValid) {
                    errors.add("source值无效：" + source);
                } else {
                    // 当source有效且parseMethod为空时，自动赋值
                    if (parseMethod == null || parseMethod.trim().isEmpty()) {
                        if (ParamSource.PATH.equals(source)) {
                            parseMethod = ExtractorType.PATH_MATCH.name();
                        } else if (Arrays.asList(ParamSource.QUERY, ParamSource.HEADER, ParamSource.COOKIE).contains(source)) {
                            parseMethod = ExtractorType.DEFAULT.name();
                        }
                        // 其他source（如BODY）不自动赋值，保留后续验证
                    }
                }
            }

            // 3. 验证parseMethod非空及合法性（此时可能已被自动赋值）
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
            if (isSourceValid && parseMethod != null && !parseMethod.trim().isEmpty()) {
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
