package com.security.config;//package com.security.config;
//
//import com.ctrip.framework.apollo.model.ConfigChangeEvent;
//import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
//import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
//import com.security.enums.ExtractorType;
//import com.security.enums.MultiParamMode;
//import com.security.enums.ParamSource;
//import lombok.Data;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.InitializingBean;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
///**
// * API权限配置类（读取Apollo JSON数组配置）
// */
//@Data
//@Component
//@Slf4j
//public class ApiPermissionApolloConfig implements InitializingBean {
//
//    // 全局开关（从Apollo读取，默认关闭）
//    @Value("${api.permission.enabled:false}")
//    private boolean enabled;
//
//    // 规则列表（从Apollo JSON数组解析，默认空列表）
//    // Apollo配置键：api.permission.rules，值为JSON数组
//    @ApolloJsonValue("${api.permission.rules:[]}")
//    private List<Rule> rules;
//
//    @Override
//    public void afterPropertiesSet() throws Exception {
//        log.info("开始验证API权限配置（Apollo来源）...");
//        validateAndLog();
//    }
//
//    /**
//     * 配置变更监听（Apollo配置更新时自动触发）
//     */
//    @ApolloConfigChangeListener(value = "API_PERMISSION_NAMESPACE") // 替换为实际的Apollo命名空间
//    public void onConfigChange(ConfigChangeEvent event) {
//        log.info("API权限配置发生变更，重新验证...");
//        // 重新验证所有配置
//        validateAndLog();
//    }
//
//    /**
//     * 验证配置并处理结果
//     */
//    private void validateAndLog() {
//        List<String> allErrors = new ArrayList<>(validate());
//
//        if (!allErrors.isEmpty()) {
//            log.error("Apollo配置验证失败，共发现{}个错误：", allErrors.size());
//            allErrors.forEach(error -> log.error("- {}", error));
//
//            // 配置错误时自动关闭全局开关
//            if (enabled) {
//                log.error("因配置错误，自动关闭全局开关");
//                this.enabled = false;
//            }
//        } else {
//            log.info("API权限配置（Apollo）验证通过");
//        }
//    }
//
//    /**
//     * 全局配置手动验证
//     */
//    public List<String> validate() {
//        List<String> errors = new ArrayList<>();
//
//        // 全局开关开启时，规则列表不能空
//        if (enabled && (rules == null || rules.isEmpty())) {
//            errors.add("全局开关已开启，但Apollo未配置任何规则（rules不能为空）");
//        }
//
//        // 验证每个规则
//        if (rules != null) {
//            for (Rule rule : rules) {
//                List<String> ruleErrors = rule.validate().stream()
//                        .map(error -> "规则[" + (rule.getUriPattern() != null ? rule.getUriPattern() : "未设置URI") + "]：" + error)
//                        .collect(Collectors.toList());
//                errors.addAll(ruleErrors);
//
//                // 禁用有错误的规则
//                if (!ruleErrors.isEmpty()) {
//                    rule.setEnabled(false);
//                }
//            }
//        }
//
//        return errors;
//    }
//
//    /**
//     * 接口级规则（对应Apollo JSON数组中的对象）
//     */
//    @Data
//    public static class Rule {
//        private String uriPattern; // URI匹配模式（Ant风格）
//        private boolean enabled = false; // 规则开关
//
//        private PrincipalParam principalParam; // 主体参数配置
//
//        private List<ParamRule> paramRules; // 目标参数规则列表
//
//        private MultiParamMode multiParamMode = MultiParamMode.ANY_MATCH; // 多参数验证模式
//
//        /**
//         * 规则参数验证
//         */
//        public List<String> validate() {
//            List<String> errors = new ArrayList<>();
//
//            // 1. 验证uriPattern
//            if (uriPattern == null || uriPattern.trim().isEmpty()) {
//                errors.add("uriPattern不能为空（需指定Ant风格URI，如/api/users/**）");
//            } else if (!uriPattern.startsWith("/")) {
//                errors.add("uriPattern必须以/开头（Ant风格）");
//            }
//
//            // 2. 验证主体参数
//            if (principalParam == null) {
//                errors.add("principalParam不能为空（需配置主体参数）");
//            } else {
//                List<String> principalErrors = principalParam.validate().stream()
//                        .map(error -> "主体参数：" + error)
//                        .collect(Collectors.toList());
//                errors.addAll(principalErrors);
//            }
//
//            // 3. 验证目标参数列表
//            if (paramRules == null || paramRules.isEmpty()) {
//                errors.add("paramRules不能为空（需至少配置一个目标参数规则）");
//            } else {
//                for (ParamRule paramRule : paramRules) {
//                    if (paramRule == null) {
//                        errors.add("paramRules中存在空对象");
//                        continue;
//                    }
//                    String paramName = paramRule.getParamName() != null ? paramRule.getParamName() : "未命名参数";
//                    List<String> paramErrors = paramRule.validate().stream()
//                            .map(error -> "参数[" + paramName + "]：" + error)
//                            .collect(Collectors.toList());
//                    errors.addAll(paramErrors);
//                }
//            }
//
//            // 4. 验证多参数模式
//            if (multiParamMode == null) {
//                errors.add("multiParamMode不能为空（需指定ALL_MATCH/ANY_MATCH）");
//            } else if (multiParamMode != MultiParamMode.ALL_MATCH && multiParamMode != MultiParamMode.ANY_MATCH) {
//                errors.add("multiParamMode必须为ALL_MATCH或ANY_MATCH，当前值：" + multiParamMode);
//            }
//
//            // 5. 验证参数名重复
//            if (paramRules != null && !paramRules.isEmpty() && errors.stream().noneMatch(e -> e.contains("paramRules不能为空"))) {
//                List<String> duplicates = getDuplicateParamNames();
//                if (!duplicates.isEmpty()) {
//                    errors.add("存在重复参数名：" + duplicates);
//                }
//            }
//
//            return errors;
//        }
//
//        // 获取重复的参数名
//        private List<String> getDuplicateParamNames() {
//            return paramRules.stream()
//                    .filter(paramRule -> paramRule.getParamName() != null && !paramRule.getParamName().trim().isEmpty())
//                    .map(ParamRule::getParamName)
//                    .collect(Collectors.groupingBy(name -> name, Collectors.counting()))
//                    .entrySet().stream()
//                    .filter(entry -> entry.getValue() > 1)
//                    .map(Map.Entry::getKey)
//                    .collect(Collectors.toList());
//        }
//    }
//
//    /**
//     * 主体参数配置
//     */
//    @Data
//    public static class PrincipalParam {
//        private String name; // 参数名（如staffId）
//        private ParamSource source; // 参数来源（PATH/BODY/QUERY等）
//        private String parseMethod; // 解析方式（对应ExtractorType）
//        private String parseConfig; // 解析配置（如JSON路径）
//
//        /**
//         * 主体参数验证
//         */
//        public List<String> validate() {
//            List<String> errors = new ArrayList<>();
//
//            // 1. 验证name非空
//            if (name == null || name.trim().isEmpty()) {
//                errors.add("name不能为空（需指定主体参数名，如staffId）");
//            }
//
//            // 2. 验证source合法性
//            if (source == null) {
//                errors.add("source不能为空（需指定PATH/BODY/QUERY等）");
//            } else {
//                boolean isValidSource = false;
//                for (ParamSource s : ParamSource.values()) {
//                    if (s == source) {
//                        isValidSource = true;
//                        break;
//                    }
//                }
//                if (!isValidSource) {
//                    errors.add("source值无效：" + source);
//                }
//            }
//
//            // 3. 验证parseMethod合法性
//            if (parseMethod == null || parseMethod.trim().isEmpty()) {
//                errors.add("parseMethod不能为空（需指定解析方式）");
//            } else {
//                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
//                if (ExtractorType.NONE.equals(extractorType)) {
//                    errors.add("parseMethod值无效：" + parseMethod);
//                }
//            }
//
//            // 4. 验证来源与解析方式匹配性
//            if (source != null && parseMethod != null && !parseMethod.trim().isEmpty()) {
//                if (!isValidCombination(source, parseMethod)) {
//                    errors.add("来源[" + source + "]不支持解析方式[" + parseMethod + "]");
//                }
//            }
//
//            // 5. 验证必要的parseConfig
//            if (parseMethod != null && !parseMethod.trim().isEmpty()) {
//                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
//                if (ExtractorType.JSON_PATH.equals(extractorType) && (parseConfig == null || parseConfig.trim().isEmpty())) {
//                    errors.add("解析方式[" + extractorType + "]必须配置parseConfig");
//                }
//            }
//
//            return errors;
//        }
//
//        // 验证来源与解析方式的匹配性
//        private boolean isValidCombination(ParamSource source, String parseMethod) {
//            ExtractorType extractorType = ExtractorType.fromString(parseMethod);
//            switch (source) {
//                case PATH:
//                    return ExtractorType.DEFAULT.equals(extractorType) || ExtractorType.PATH_MATCH.equals(extractorType);
//                case BODY:
//                    return ExtractorType.JSON_PATH.equals(extractorType);
//                case QUERY:
//                case HEADER:
//                case COOKIE:
//                    return ExtractorType.DEFAULT.equals(extractorType);
//                default:
//                    return false;
//            }
//        }
//    }
//
//    /**
//     * 目标参数规则
//     */
//    @Data
//    public static class ParamRule {
//        private String paramName; // 参数名
//        private ParamSource source; // 参数来源
//        private String parseMethod; // 解析方式
//        private String parseConfig; // 解析配置
//        private String validatorId; // 验证器ID
//
//        /**
//         * 目标参数验证
//         */
//        public List<String> validate() {
//            List<String> errors = new ArrayList<>();
//
//            // 1. 验证paramName非空
//            if (paramName == null || paramName.trim().isEmpty()) {
//                errors.add("paramName不能为空（需指定参数名，如userId）");
//            }
//
//            // 2. 验证source合法性
//            if (source == null) {
//                errors.add("source不能为空（需指定PATH/BODY/QUERY等）");
//            } else {
//                boolean isValidSource = false;
//                for (ParamSource s : ParamSource.values()) {
//                    if (s == source) {
//                        isValidSource = true;
//                        break;
//                    }
//                }
//                if (!isValidSource) {
//                    errors.add("source值无效：" + source);
//                }
//            }
//
//            // 3. 验证parseMethod合法性
//            if (parseMethod == null || parseMethod.trim().isEmpty()) {
//                errors.add("parseMethod不能为空");
//            } else {
//                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
//                if (ExtractorType.NONE.equals(extractorType)) {
//                    errors.add("parseMethod值无效：" + parseMethod);
//                }
//            }
//
//            // 4. 验证validatorId非空
//            if (validatorId == null || validatorId.trim().isEmpty()) {
//                errors.add("validatorId不能为空（需指定验证器ID）");
//            }
//
//            // 5. 验证来源与解析方式匹配性
//            if (source != null && parseMethod != null && !parseMethod.trim().isEmpty()) {
//                if (!isValidCombination(source, parseMethod)) {
//                    errors.add("来源[" + source + "]不支持解析方式[" + parseMethod + "]");
//                }
//            }
//
//            // 6. 验证必要的parseConfig
//            if (parseMethod != null && !parseMethod.trim().isEmpty()) {
//                ExtractorType extractorType = ExtractorType.fromString(parseMethod);
//                if (ExtractorType.JSON_PATH.equals(extractorType) && (parseConfig == null || parseConfig.trim().isEmpty())) {
//                    errors.add("解析方式[" + extractorType + "]必须配置parseConfig");
//                }
//            }
//
//            return errors;
//        }
//
//        // 验证来源与解析方式的匹配性
//        private boolean isValidCombination(ParamSource source, String parseMethod) {
//            ExtractorType extractorType = ExtractorType.fromString(parseMethod);
//            switch (source) {
//                case PATH:
//                    return ExtractorType.PATH_MATCH.equals(extractorType);
//                case BODY:
//                    return ExtractorType.JSON_PATH.equals(extractorType);
//                case QUERY:
//                case HEADER:
//                case COOKIE:
//                    return ExtractorType.DEFAULT.equals(extractorType);
//                default:
//                    return false;
//            }
//        }
//    }
//}