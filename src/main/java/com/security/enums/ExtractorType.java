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
        // 如果传入的字符串是 NONE，直接返回 NONE
        if (str.equalsIgnoreCase("NONE")) {
            return NONE;
        }
        try {
            // 优先匹配已有枚举（DEFAULT/JSON_PATH等）
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 自定义的解析方式，返回CUSTOM
            log.info("未知的解析类型: {}, 自动识别为自定义解析方式", str);
            return CUSTOM;
        }
    }

    /**
     * 获取解析器类型
     */
    public static String getExtractorType(String str) {
        ExtractorType extractorType = ExtractorType.fromString(str);
        if (extractorType.equals(ExtractorType.CUSTOM)) {
            return extractorType.name() + "#" + str;
        }
        return extractorType.name();
    }
}