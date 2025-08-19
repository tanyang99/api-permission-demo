package com.security.extractor;

import com.security.enums.ParamSource;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 参数提取器接口：定义参数提取的标准行为
 */
public interface ParameterExtractor {
    /**
     * 提取参数值
     *
     * @param request     HTTP请求对象
     * @param paramName   参数名
     * @param parseConfig 解析配置（如JSONPath表达式）
     * @param source      参数来源
     * @param useCachedRequest 是否使用缓存的请求对象
     * @return 参数值列表（支持多值）
     */
    List<String> extract(HttpServletRequest request, String paramName, String parseConfig, ParamSource source, boolean useCachedRequest);

    /**
     * 支持的解析方式
     *
     * @return 解析方式名称（如"JSON_PATH"）
     */
    String supportParseMethod();

    /**
     * 支持的参数来源
     *
     * @return 参数来源枚举列表
     */
    List<ParamSource> supportSources();
}
