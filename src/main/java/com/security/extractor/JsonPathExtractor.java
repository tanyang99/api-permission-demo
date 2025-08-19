package com.security.extractor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import com.security.enums.ExtractorType;
import com.security.enums.ParamSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Order(1)
@Component
@Slf4j
public class JsonPathExtractor implements ParameterExtractor {

    // 缓存预编译的JsonPath表达式，提升重复使用效率
    private static final Map<String, JsonPath> JSON_PATH_CACHE = new ConcurrentHashMap<>(64);
    // 流式处理配置，全局共享
    private static final Configuration STREAMING_CONFIG = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS)    // 抑制非关键异常
            .addOptions(Option.ALWAYS_RETURN_LIST) ;    // 确保返回结果始终为列表
    // 用于复杂对象序列化的Jackson映射器
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public List<String> extract(HttpServletRequest request, String paramName, String parseConfig,
                                ParamSource source, boolean useCachedRequest) {
        // 基础校验链：快速失败原则
        if (ParamSource.BODY != source) {
            log.trace("参数[{}]来源非BODY，JSONPath提取器不处理", paramName);
            return Collections.emptyList();
        }
        if (!useCachedRequest) {
            log.trace("未启用缓存请求，JSONPath提取器不处理参数[{}]", paramName);
            return Collections.emptyList();
        }
        if (!(request instanceof ContentCachingRequestWrapper)) {
            log.warn("参数[{}]提取失败：请求不是ContentCachingRequestWrapper类型", paramName);
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(parseConfig)) {
            log.warn("参数[{}]提取失败：JSONPath表达式为空", paramName);
            return Collections.emptyList();
        }

        ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
        byte[] contentBytes = wrapper.getContentAsByteArray();

        if (contentBytes.length == 0) {
            log.trace("参数[{}]提取结果为空：请求体长度为0", paramName);
            return Collections.emptyList();
        }

        try {
            return extractWithStreaming(contentBytes, parseConfig, paramName);
        } catch (JsonPathException e) {
            log.error("参数[{}]JSONPath解析异常，表达式:{}", paramName, parseConfig, e);
            return Collections.emptyList();
        }
    }

    /**
     * 流式处理大JSON，减少内存占用
     */
    private List<String> extractWithStreaming(byte[] contentBytes, String jsonPathExpr, String paramName) {
        // 从缓存获取或编译JsonPath表达式
        JsonPath jsonPath = JSON_PATH_CACHE.computeIfAbsent(jsonPathExpr, expr -> {
            try {
                return JsonPath.compile(expr);
            } catch (JsonPathException e) {
                log.error("参数[{}]JSONPath表达式[{}]编译失败", paramName, expr, e);
                throw e;
            }
        });

        // 使用缓冲流提升大文件读取效率
        try (InputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(contentBytes))) {
            // 流式解析并提取数据
            List<Object> result = JsonPath.using(STREAMING_CONFIG)
                    .parse(inputStream)
                    .read(jsonPath);

            return processExtractedValues(result, paramName);
        } catch (Exception e) {
            log.error("参数[{}]流式解析失败，表达式:{}", paramName, jsonPathExpr, e);
            throw new JsonPathException("流式解析JSON失败", e);
        }
    }

    /**
     * 处理提取结果，转换为字符串列表
     */
    private List<String> processExtractedValues(List<Object> rawValues, String paramName) {
        if (rawValues == null || rawValues.isEmpty()) {
            log.trace("参数[{}]未提取到任何值", paramName);
            return Collections.emptyList();
        }

        // 过滤null并序列化值为字符串
        List<String> valueList = rawValues.stream()
                .filter(Objects::nonNull)
                .map(value -> serializeValue(value, paramName))
                .collect(Collectors.toList());

        log.debug("参数[{}]提取到{}个有效值", paramName, valueList.size());
        return Collections.unmodifiableList(new ArrayList<>(valueList));
    }

    /**
     * 序列化值为字符串，支持复杂对象
     */
    private String serializeValue(Object value, String paramName) {
        try {
            // 基本类型直接转换，复杂对象JSON序列化
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                return value.toString();
            }
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("参数[{}]值[{}]序列化失败，使用默认toString()", paramName, value.getClass().getSimpleName(), e);
            return value.toString();
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