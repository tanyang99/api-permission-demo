package com.security.enums;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum MultiParamMode {
    ALL_MATCH, ANY_MATCH;

    private static final Logger log = LoggerFactory.getLogger(MultiParamMode.class);

    // 配置解析容错：错误时返回默认值（ANY_MATCH）
    public static MultiParamMode fromString(String modeStr) {
        if (modeStr == null || modeStr.isEmpty()) {
            log.warn("多参数模式为空，使用默认值:ANY_MATCH");
            return ANY_MATCH;
        }
        try {
            return valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("多参数模式配置错误:{}，使用默认值:ANY_MATCH", modeStr);
            return ANY_MATCH;
        }
    }
}
