package com.security.interceptor;

import com.security.config.ApiPermissionConfig;
import com.security.context.PermissionContext;
import com.security.enums.MultiParamMode;
import com.security.exception.CustomAccessDeniedException;
import com.security.extractor.ExtractorFactory;
import com.security.extractor.ParameterExtractor;
import com.security.validator.PermissionValidator;
import com.security.validator.ValidatorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

@Component
public class PermissionInterceptor implements HandlerInterceptor {
    @Autowired
    private ApiPermissionConfig globalConfig;

    @Autowired
    private ExtractorFactory extractorFactory;

    @Autowired
    private ValidatorFactory validatorFactory;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 全局开关关闭，直接放行
        if (!globalConfig.isEnabled()) {
            return true;
        }

        // 2. 匹配当前URI对应的规则
        String requestUri = request.getRequestURI();
        ApiPermissionConfig.Rule matchedRule = matchRule(requestUri);
        if (matchedRule == null || !matchedRule.isEnabled()) {
            return true; // 无匹配规则或规则关闭
        }

        // 3. 提取主体参数（PrincipalData）
        PermissionContext.PrincipalData principalData = extractPrincipal(request, matchedRule.getPrincipalParam(), PermissionContext.getContextData().isUseCachedRequest());
        PermissionContext.getContextData().setPrincipalData(principalData);

        // 4. 提取目标参数（TargetParameters）
        List<PermissionContext.TargetParameter> targetParameters = extractTargetParams(request, matchedRule.getParamRules(), PermissionContext.getContextData().isUseCachedRequest());
        PermissionContext.getContextData().setTargetParameters(targetParameters);

        // 5. 设置多参数验证模式
        PermissionContext.getContextData().setMultiParamMode(matchedRule.getMultiParamMode());

        // 6. 执行验证
        boolean validationPassed = executeValidation(principalData, targetParameters, matchedRule.getMultiParamMode());

        if (!validationPassed) {
            throw new CustomAccessDeniedException("越权访问：参数不归属当前主体");
        }

        return true;
    }

    /**
     * 匹配URI对应的规则
     */
    private ApiPermissionConfig.Rule matchRule(String requestUri) {
        if (globalConfig.getRules() == null) {
            return null;
        }
        for (ApiPermissionConfig.Rule rule : globalConfig.getRules()) {
            if (pathMatcher.match(rule.getUriPattern(), requestUri)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 提取主体参数
     */
    private PermissionContext.PrincipalData extractPrincipal(HttpServletRequest request, ApiPermissionConfig.PrincipalParam config, boolean useCachedRequest) {
        // 获取提取器
        ParameterExtractor extractor = extractorFactory.getExtractor(config.getParseMethod());

        // 校验提取器是否支持该来源
        if (!extractor.supportSources().contains(config.getSource())) {
            throw new IllegalArgumentException(config.getParseMethod() + "提取器不支持" + config.getSource() + "来源（主体参数：" + config.getName() + "）");
        }

        // 提取参数值
        List<String> values = extractor.extract(request, config.getName(), config.getParseConfig(), config.getSource(), useCachedRequest);

        if (values.isEmpty()) {
            throw new CustomAccessDeniedException("主体参数不存在：" + config.getName());
        }

        // 构建主体数据
        PermissionContext.PrincipalData principal = new PermissionContext.PrincipalData();
        principal.setName(config.getName());
        principal.setValues(values);
        return principal;
    }

    /**
     * 提取目标参数列表
     */
    private List<PermissionContext.TargetParameter> extractTargetParams(HttpServletRequest request, List<ApiPermissionConfig.ParamRule> paramRules, boolean useCachedRequest) {
        if (paramRules == null || paramRules.isEmpty()) {
            throw new IllegalArgumentException("目标参数规则不能为空");
        }

        List<PermissionContext.TargetParameter> targetParameters = new ArrayList<>();
        for (ApiPermissionConfig.ParamRule rule : paramRules) {
            // 获取提取器
            ParameterExtractor extractor = extractorFactory.getExtractor(rule.getParseMethod());

            // 校验提取器是否支持该来源
            if (!extractor.supportSources().contains(rule.getSource())) {
                throw new IllegalArgumentException(rule.getParseMethod() + "提取器不支持" + rule.getSource() + "来源（参数：" + rule.getParamName() + "）");
            }

            // 提取参数值
            List<String> values = extractor.extract(request, rule.getParamName(), rule.getParseConfig(), rule.getSource(),useCachedRequest);

            // 构建目标参数
            PermissionContext.TargetParameter target = new PermissionContext.TargetParameter();
            target.setName(rule.getParamName());
            target.setValues(values);
            target.setValidatorId(rule.getValidatorId());
            targetParameters.add(target);
        }

        return targetParameters;
    }

    /**
     * 执行验证逻辑
     */
    private boolean executeValidation(PermissionContext.PrincipalData principal, List<PermissionContext.TargetParameter> targets, MultiParamMode mode) {
        if (targets.isEmpty()) {
            return false; // 无目标参数，验证不通过
        }

        // 根据多参数模式执行验证
        if (mode == MultiParamMode.ALL_MATCH) {
            // 所有参数必须通过验证
            for (PermissionContext.TargetParameter target : targets) {
                PermissionValidator validator = validatorFactory.getValidator(target.getValidatorId());
                if (!validator.validate(principal, target)) {
                    return false;
                }
            }
            return true;
        } else if (mode == MultiParamMode.ANY_MATCH) {
            // 任一参数通过验证
            for (PermissionContext.TargetParameter target : targets) {
                PermissionValidator validator = validatorFactory.getValidator(target.getValidatorId());
                if (validator.validate(principal, target)) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }
}
