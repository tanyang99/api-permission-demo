package com.security.extractor;

import com.security.enums.ExtractorType;
import com.security.enums.ParamSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class DefaultExtractor implements ParameterExtractor {

    // 缓存请求对应的Cookie Map（key: request.hashCode()，避免内存泄漏使用WeakHashMap）
    private final Map<Integer, Map<String, String>> cookieCache = new WeakHashMap<>();

    @Override
    public List<String> extract(HttpServletRequest request, String paramName, String parseConfig,
                                ParamSource source, boolean useCachedRequest) {
        if (!StringUtils.hasText(paramName)) {
            log.error("参数名称为空，无法提取");
            return Collections.emptyList();
        }

        List<String> values;
        switch (source) {
            case QUERY:
                values = extractQueryParams(request, paramName);
                break;
            case HEADER:
                values = extractHeaderParam(request, paramName);
                break;
            case COOKIE:
                values = extractCookieValue(request, paramName);
                break;
            default:
                log.warn("不支持的参数来源[{}]，参数名[{}]", source, paramName);
                return Collections.emptyList();
        }

        log.info("参数[{}]从[{}]提取到{}个值，分别为: {}", paramName, source, values.size(), values);
        return values;
    }

    /**
     * 提取Cookie值（优化：缓存为Map减少重复遍历）
     */
    private List<String> extractCookieValue(HttpServletRequest request, String paramName) {
        // 1. 从缓存获取当前请求的Cookie Map，不存在则构建
        Map<String, String> cookieMap = cookieCache.computeIfAbsent(
                request.hashCode(),  // 用request的哈希值作为缓存key
                k -> buildCookieMap(request.getCookies())  // 构建Cookie键值对Map
        );

        // 2. 直接通过参数名获取Cookie值
        String value = cookieMap.get(paramName);
        return value != null ? Collections.singletonList(value) : Collections.emptyList();
    }

    /**
     * 将Cookie数组转换为Map<String, String>
     */
    private Map<String, String> buildCookieMap(Cookie[] cookies) {
        if (cookies == null || cookies.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, String> cookieMap = new HashMap<>(cookies.length);
        for (Cookie cookie : cookies) {
            cookieMap.put(cookie.getName(), cookie.getValue());
        }
        return cookieMap;
    }

    // 其他方法保持不变...
    @Override
    public String supportParseMethod() {
        return ExtractorType.DEFAULT.name();
    }

    @Override
    public List<ParamSource> supportSources() {
        return Arrays.asList(ParamSource.QUERY, ParamSource.HEADER, ParamSource.COOKIE);
    }

    private List<String> extractQueryParams(HttpServletRequest request, String paramName) {
        String[] paramValues = request.getParameterValues(paramName);
        if (paramValues == null || paramValues.length == 0) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(paramValues));
    }

    private List<String> extractHeaderParam(HttpServletRequest request, String paramName) {
        String headerValue = request.getHeader(paramName);
        if (headerValue == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(headerValue);
    }
}