package com.security.extractor;

import com.security.enums.ExtractorType;
import com.security.enums.ParamSource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 参数提取器工厂类：自动注册所有ParameterExtractor实现类，支持自定义扩展
 */
@Component
@Slf4j
public class ExtractorFactory implements InitializingBean {

    // 存储解析方式与提取器的映射关系
    private final Map<String, ParameterExtractor> extractorMap = new HashMap<>();

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
            String parseMethod = extractor.supportParseMethod();
            Class<?> extractorClass = extractor.getClass();

            // 1. 验证parseMethod非空
            if (parseMethod == null || parseMethod.trim().isEmpty()) {
                log.error("提取器[{}]的supportParseMethod返回空值，跳过注册", extractorClass.getName());
                continue;
            }
            parseMethod = parseMethod.trim(); // 去除首尾空格

            // 2. 验证解析方式与枚举的兼容性
            ExtractorType extractorType = ExtractorType.fromString(parseMethod);
            if (ExtractorType.NONE.equals(extractorType)) {
                log.error("提取器[{}]的parseMethod[{}]无法被识别（不支持的解析方式），跳过注册",
                        extractorClass.getName(), parseMethod);
                continue;
            }

            // 3. 自定义提取器特殊校验（必须符合CUSTOM#xxx格式）
            if (ExtractorType.CUSTOM.equals(extractorType)) {
                if (!parseMethod.startsWith(ExtractorType.CUSTOM.name() + "#")) {
                    log.error("自定义提取器[{}]的parseMethod[{}]格式错误，必须以'{}#'开头",
                            extractorClass.getName(), parseMethod, ExtractorType.CUSTOM.name());
                    continue;
                }
                // 提取自定义标识部分（CUSTOM#之后的内容）用于日志展示
                String customId = parseMethod.substring(ExtractorType.CUSTOM.name().length() + 1);
                log.debug("自定义提取器[{}]的标识为: {}", extractorClass.getName(), customId);
            } else {
                // 非自定义提取器：必须与枚举值严格匹配（忽略大小写）
                if (!parseMethod.equalsIgnoreCase(extractorType.name())) {
                    log.error("提取器[{}]的parseMethod[{}]与枚举值[{}]不匹配，跳过注册",
                            extractorClass.getName(), parseMethod, extractorType.name());
                    continue;
                }
            }

            // 4. 验证支持的参数来源非空且有效
            List<ParamSource> supportSources = extractor.supportSources();
            if (supportSources == null || supportSources.isEmpty()) {
                log.error("提取器[{}]未指定支持的参数来源（supportSources不能为空），跳过注册",
                        extractorClass.getName());
                continue;
            }
            // 检查来源是否为枚举有效值
            for (ParamSource source : supportSources) {
                if (source == null) {
                    log.error("提取器[{}]的supportSources包含空值，跳过注册", extractorClass.getName());
                    supportSources = null;
                    break;
                }
                // 验证来源是否在枚举范围内（防止反序列化异常）
                boolean isValid = false;
                for (ParamSource validSource : ParamSource.values()) {
                    if (validSource == source) {
                        isValid = true;
                        break;
                    }
                }
                if (!isValid) {
                    log.error("提取器[{}]的supportSources包含无效值[{}]，跳过注册",
                            extractorClass.getName(), source);
                    supportSources = null;
                    break;
                }
            }
            if (supportSources == null) {
                continue;
            }

            // 5. 处理重复的解析方式（覆盖时打印详细日志）
            if (extractorMap.containsKey(parseMethod)) {
                Class<?> existingClass = extractorMap.get(parseMethod).getClass();
                log.warn("解析方式[{}]存在重复实现：现有[{}]，新实现[{}]将覆盖旧实现",
                        parseMethod, existingClass.getName(), extractorClass.getName());
            }

            // 6. 注册提取器
            extractorMap.put(parseMethod, extractor);
            log.info("注册参数提取器: 解析方式={}, 支持来源={}, 实现类={}",
                    parseMethod,
                    supportSources.stream().map(ParamSource::name).collect(Collectors.joining(",")),
                    extractorClass.getSimpleName());
        }

        log.info("参数提取器初始化完成，共注册 {} 个有效实现，解析方式列表: {}",
                extractorMap.size(), extractorMap.keySet());
    }

    /**
     * 根据解析方式获取提取器
     * @param parseMethod 解析方式（如"JSON_PATH"、"HEADER"等）
     * @return 对应的ParameterExtractor
     * @throws IllegalArgumentException 当解析方式不被支持时
     */
    public ParameterExtractor getExtractor(String parseMethod) {
        ParameterExtractor extractor = extractorMap.get(parseMethod);
        if (extractor == null) {
            throw new IllegalArgumentException("不支持的参数解析方式: " + parseMethod +
                    "，已注册的方式: " + extractorMap.keySet());
        }
        return extractor;
    }

    /**
     * 获取所有支持的解析方式
     */
    public List<String> getSupportedParseMethods() {
        return new ArrayList<>(extractorMap.keySet());
    }
}
