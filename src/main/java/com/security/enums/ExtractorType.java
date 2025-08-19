package com.security.enums;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum ExtractorType {
    DEFAULT,       // 默认
    JSON_PATH,     // JSON路径（如从请求体JSON中提取）
    PATH_MATCH,    // 路径匹配（如从URI正则提取）
    DECRYPTION,    // 解密（如encryptedStaffId解密为S1001）
    CUSTOM,       // 自定义解析
    NONE; // 无解析

    // 配置解析容错：错误时返回默认值
    public static ExtractorType fromString(String str) {
        if (str == null || str.isEmpty()) {
            log.warn("解析类型配置为空，使用默认值:CUSTOM");
            return NONE;
        }
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("解析类型配置为空:{}，使用默认值:CUSTOM", str);
            return NONE;
        }
    }
}
