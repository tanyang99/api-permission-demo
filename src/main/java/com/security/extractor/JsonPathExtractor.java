package com.security.extractor;

import com.jayway.jsonpath.JsonPath;
import com.security.enums.ExtractorType;
import com.security.enums.ParamSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class JsonPathExtractor implements ParameterExtractor {

    /**
     * 提取JSON请求体中的参数
     *
     * @param request          HTTP请求对象
     * @param paramName        参数名称（仅用于日志和提示用）
     * @param parseConfig      JSONPath表达式（如"$.user.id"）
     * @param source           参数来源（必须为BODY）
     * @param useCachedRequest 是否使用缓存的请求体
     * @return 提取的参数值列表，无结果时返回空列表
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> extract(HttpServletRequest request, String paramName, String parseConfig, ParamSource source, boolean useCachedRequest) {
        // 1. 基础校验：仅处理BODY来源的参数
        if (ParamSource.BODY != source) {
            log.warn("参数[{}]的来源不是BODY，JSONPath提取器不处理", paramName);
            return Collections.emptyList();
        }

        // 2. 检查是否需要使用缓存请求体
        if (!useCachedRequest) {
            log.warn("未启用缓存请求(useCachedRequest=false)，JSONPath提取器不处理");
            return Collections.emptyList();
        }

        // 3. 校验请求对象类型
        if (!(request instanceof ContentCachingRequestWrapper)) {
            log.warn("参数[{}]需要从缓存请求提取，但请求对象不是ContentCachingRequestWrapper", paramName);
            return Collections.emptyList();
        }
        ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;

        // 4. 校验JSONPath表达式
        if (!StringUtils.hasText(parseConfig)) {
            log.warn("参数[{}]的JSONPath表达式(parseConfig)为空，无法提取", paramName);
            return Collections.emptyList();
        }

        try {
            // 5. 读取缓存的请求体
            byte[] contentBytes = wrapper.getContentAsByteArray();
            if (contentBytes.length == 0) {
                log.trace("参数[{}]的缓存请求体为空，无提取结果", paramName);
                return Collections.emptyList();
            }
            String jsonBody = new String(contentBytes, StandardCharsets.UTF_8);

            // 6. 执行JSONPath提取
            Object result = JsonPath.read(jsonBody, parseConfig);

            // 7. 处理提取结果（单值转列表）
            if (result instanceof List) {
                List<String> valueList = (List<String>) result;
                log.info("参数[{}]通过JSONPath提取到{}个值，值为:{}", paramName, valueList.size(), valueList);
                return valueList;
            } else if (result != null) {
                List<String> singleValue = Collections.singletonList(result.toString());
                log.info("参数[{}]通过JSONPath提取到1个值: {}", paramName, singleValue.get(0));
                return singleValue;
            } else {
                log.info("参数[{}]通过JSONPath未提取到任何值", paramName);
                return Collections.emptyList();
            }

        } catch (Exception e) {
            log.error("参数[{}]通过JSONPath提取失败，表达式:{}", paramName, parseConfig, e);
            return Collections.emptyList();
        }
    }

    @Override
    public String supportParseMethod() {
        return ExtractorType.JSON_PATH.name();
    }

    @Override
    public List<ParamSource> supportSources() {
        return Collections.singletonList(ParamSource.BODY);
    }
}
