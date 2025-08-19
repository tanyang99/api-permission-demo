package com.security.extractor;

import com.security.enums.ExtractorType;
import com.security.enums.ParamSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class DefaultExtractor implements ParameterExtractor {

    /**
     * 提取请求中的参数（支持QUERY/HEADER/COOKIE来源）
     *
     * @param request        HTTP请求对象
     * @param paramName      参数名称
     * @param parseConfig    预留配置（默认提取暂不使用）
     * @param source         参数来源（QUERY/HEADER/COOKIE）
     * @param useCachedRequest 是否使用缓存请求（默认提取无需缓存，此参数忽略）
     * @return 提取的参数值列表，无结果时返回空列表
     */
    @Override
    public List<String> extract(HttpServletRequest request, String paramName, String parseConfig,
                                ParamSource source, boolean useCachedRequest) {
        // 校验参数名称有效性
        if (!StringUtils.hasText(paramName)) {
            log.error("参数名称为空，无法提取");
            return Collections.emptyList();
        }

        // 根据参数来源提取值
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

    @Override
    public String supportParseMethod() {
        return ExtractorType.DEFAULT.name();
    }

    @Override
    public List<ParamSource> supportSources() {
        return Arrays.asList(ParamSource.QUERY, ParamSource.HEADER, ParamSource.COOKIE);
    }

    /**
     * 提取查询参数（URL中的?param=value部分）
     */
    private List<String> extractQueryParams(HttpServletRequest request, String paramName) {
        String[] paramValues = request.getParameterValues(paramName);
        if (paramValues == null || paramValues.length == 0) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(paramValues));
    }

    /**
     * 提取请求头参数
     */
    private List<String> extractHeaderParam(HttpServletRequest request, String paramName) {
        String headerValue = request.getHeader(paramName);
        if (headerValue == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(headerValue);
    }

    /**
     * 提取Cookie值
     */
    private List<String> extractCookieValue(HttpServletRequest request, String paramName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Collections.emptyList();
        }

        for (Cookie cookie : cookies) {
            if (paramName.equals(cookie.getName())) {
                return Collections.singletonList(cookie.getValue());
            }
        }
        return Collections.emptyList();
    }
}
