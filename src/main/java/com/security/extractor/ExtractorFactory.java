package com.security.extractor;

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

        if(extractors == null || extractors.isEmpty()) {
            log.warn("未发现任何ParameterExtractor实现类");
            return;
        }
        // 遍历所有提取器，注册到map中
        for (ParameterExtractor extractor : extractors) {
            String parseMethod = extractor.supportParseMethod();
            // 检查是否有重复的解析方式
            if (extractorMap.containsKey(parseMethod)) {
                log.warn("发现重复的参数提取器解析方式: {}，将覆盖之前的实现", parseMethod);
            }
            extractorMap.put(parseMethod, extractor);
            log.info("注册参数提取器: 解析方式={}, 支持来源={}",
                    parseMethod,
                    extractor.supportSources().stream()
                            .map(ParamSource::name)
                            .collect(Collectors.joining(",")));
        }
        log.info("参数提取器初始化完成，共注册 {} 个实现", extractorMap.size());
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
