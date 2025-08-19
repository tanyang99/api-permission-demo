package com.security.enums;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum ParamSource {
    PATH, BODY, QUERY, HEADER, COOKIE;

    // 配置解析容错：错误时返回默认值（QUERY）
    public static ParamSource fromString(String sourceStr) {
        if (sourceStr == null || sourceStr.isEmpty()) {
            log.warn("参数来源为空，使用默认值:QUERY");
            return QUERY;
        }
        try {
            return valueOf(sourceStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("参数来源配置错误:{}，使用默认值:QUERY", sourceStr);
            return QUERY;
        }
    }
}
