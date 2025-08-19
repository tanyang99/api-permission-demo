package com.security.enums;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum ExtractorType {
    DEFAULT,       // 默认
    JSON_PATH,     // JSON路径
    PATH_MATCH,    // 路径匹配
    CUSTOM,        // 自定义解析（统一标识）
    NONE;          // 无解析

    // 配置解析容错：自定义字符串解析为CUSTOM，其他错误返回NONE
    public static ExtractorType fromString(String str) {
        if (str == null || str.isEmpty()) {
            log.warn("解析类型配置为空，使用默认值:NONE");
            return NONE;
        }
        try {
            // 优先匹配已有枚举（DEFAULT/JSON_PATH等）
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 自动为非枚举值拼接custom_前缀，视为自定义类型
            String customMethod = CUSTOM.name() + "#" + str.trim();
            log.info("自动识别为自定义解析方式: {}", customMethod);
            return CUSTOM;
        }
    }
}