package com.security.extractor;

import com.security.enums.ExtractorType;
import com.security.enums.ParamSource;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 参数提取器工厂类：自动注册所有ParameterExtractor实现类，支持自定义扩展
 */
@Component
@Slf4j
public class ExtractorFactory implements InitializingBean {

    // 常量定义
    private static final String CUSTOM_PREFIX = ExtractorType.CUSTOM.name() + "#";

    // 存储解析方式与提取器的映射关系
    private final Map<String, ParameterExtractor> extractorMap = new HashMap<>();

    // 预加载有效的参数来源集合，提高校验效率
    private final Set<ParamSource> validParamSources = EnumSet.allOf(ParamSource.class);

    // 自动注入所有ParameterExtractor实现类（包括自定义的）
    @Setter
    @Autowired(required = false)
    private List<ParameterExtractor> extractors;


    /**
     * 初始化时注册所有提取器
     */
    @Override
    public void afterPropertiesSet() {
        if (extractors == null || extractors.isEmpty()) {
            log.warn("未发现任何ParameterExtractor实现类");
            return;
        }

        for (ParameterExtractor extractor : extractors) {
            Class<?> extractorClass = extractor.getClass();
            String extractorClassName = extractorClass.getName();

            // 1. 验证解析方式并获取标准化后的方法名
            ParseMethodInfo methodInfo = validateAndGetParsingMethod(extractor, extractorClassName);
            if (methodInfo == null) {
                continue;
            }

            // 2. 验证支持的参数来源
            List<ParamSource> supportSources = validateSupportSources(extractor, extractorClassName);
            if (supportSources == null) {
                continue;
            }

            // 3. 处理重复注册并完成注册
            registerExtractor(extractor, methodInfo.getParseMethod(), supportSources, extractorClass);
        }

        log.info("参数提取器初始化完成，共注册 {} 个有效实现，解析方式列表: {}", extractorMap.size(), extractorMap.keySet());
    }

    /**
     * 验证解析方式有效性并返回标准化信息
     */
    private ParseMethodInfo validateAndGetParsingMethod(ParameterExtractor extractor, String extractorClassName) {
        String originParseMethod = extractor.supportParseMethod();

        // 验证parseMethod非空
        if (originParseMethod == null || originParseMethod.trim().isEmpty()) {
            log.error("提取器[{}]的supportParseMethod返回空值，跳过注册", extractorClassName);
            return null;
        }

        ExtractorType extractorType = ExtractorType.fromString(originParseMethod.trim());
        String targetParseMethod = ExtractorType.getExtractorType(originParseMethod.trim());


        // 如果是标准提取器，直接返回
        if (ExtractorType.DEFAULT.equals(extractorType)
                || ExtractorType.JSON_PATH.equals(extractorType)
                || ExtractorType.PATH_MATCH.equals(extractorType)
                || (ExtractorType.CUSTOM.equals(extractorType) && targetParseMethod.startsWith(CUSTOM_PREFIX))) {

            ParseMethodInfo parseMethodInfo = new ParseMethodInfo(targetParseMethod, extractorType);
            log.info("提取器[{}]的解析方式转换为: {}", extractorClassName, parseMethodInfo);
            return parseMethodInfo;
        }

        log.warn("提取器[{}]的parseMethod[{}]无法被识别（不支持的解析方式），跳过注册", extractorClassName, targetParseMethod);
        return null;
    }


    /**
     * 验证支持的参数来源有效性
     */
    private List<ParamSource> validateSupportSources(ParameterExtractor extractor, String extractorClassName) {
        List<ParamSource> supportSources = extractor.supportSources();

        // 验证非空
        if (supportSources == null || supportSources.isEmpty()) {
            log.warn("提取器[{}]未指定支持的参数来源（supportSources不能为空），跳过注册", extractorClassName);
            return null;
        }

        // 验证每个来源的有效性
        for (ParamSource source : supportSources) {
            if (source == null) {
                log.warn("提取器[{}]的supportSources包含空值，跳过注册", extractorClassName);
                return null;
            }
            if (!validParamSources.contains(source)) {
                log.warn("提取器[{}]的supportSources包含无效值[{}]，跳过注册", extractorClassName, source);
                return null;
            }
        }

        return supportSources;
    }

    /**
     * 处理重复注册并完成提取器注册
     */
    private void registerExtractor(ParameterExtractor extractor, String parseMethod, List<ParamSource> supportSources, Class<?> extractorClass) {
        // 处理重复实现
        if (extractorMap.containsKey(parseMethod)) {
            Class<?> existingClass = extractorMap.get(parseMethod).getClass();
            log.warn("解析方式[{}]存在重复实现：现有[{}]，新实现[{}]将覆盖旧实现", parseMethod, existingClass.getName(), extractorClass.getName());
        }

        // 完成注册
        extractorMap.put(parseMethod, extractor);
        log.info("注册参数提取器: 解析方式={}, 支持来源={}, 实现类={}", parseMethod, supportSources.stream().map(ParamSource::name).collect(Collectors.joining(",")), extractorClass.getSimpleName());
    }

    /**
     * 根据解析方式获取提取器
     *
     * @param parseMethod 解析方式（如"JSON_PATH"、"HEADER"等）
     * @return 对应的ParameterExtractor
     * @throws IllegalArgumentException 当解析方式不被支持时
     */
    public ParameterExtractor getExtractor(String parseMethod) {
        if (parseMethod == null || parseMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("解析方式不能为空");
        }

        String extractorType = ExtractorType.getExtractorType(parseMethod.trim());
        ParameterExtractor extractor = extractorMap.get(extractorType);

        if (extractor == null) {
            throw new IllegalArgumentException("不支持的参数解析方式: " + parseMethod + "，已注册的方式: " + extractorMap.keySet());
        }

        return extractor;
    }

    /**
     * 获取所有支持的解析方式（返回不可修改的列表，防止外部篡改）
     */
    public List<String> getSupportedParseMethods() {
        return Collections.unmodifiableList(new ArrayList<>(extractorMap.keySet()));
    }

    /**
     * 内部辅助类：封装解析方式相关信息
     */
    @Data
    private static class ParseMethodInfo {
        private final String parseMethod;
        private final ExtractorType extractorType;

        public ParseMethodInfo(String parseMethod, ExtractorType extractorType) {
            this.parseMethod = parseMethod;
            this.extractorType = extractorType;
        }

    }
}
