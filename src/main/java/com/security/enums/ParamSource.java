package com.security.enums;

import lombok.extern.slf4j.Slf4j;

/**
 * 参数来源枚举类，定义系统可提取参数的位置（包括请求中携带的参数和服务器端会话数据），
 * 用于权限校验时指定参数的获取来源。提供配置解析的容错机制，确保非法输入时系统稳定运行。
 */
@Slf4j
public enum ParamSource {

    /**
     * 路径参数：URL路径中的占位符参数，例如<code>/users/{userId}</code>中的<code>userId</code>
     * 通常用于RESTful API中标识资源的唯一标识，需配合路径匹配解析方式（如PATH_MATCH）提取
     */
    PATH,

    /**
     * 请求体参数：HTTP请求消息体中的参数，适用于POST、PUT、PATCH等请求方法
     * 支持JSON、Form表单（x-www-form-urlencoded）、multipart/form-data等格式
     * 需配合对应的解析方式（如JSON_PATH）提取
     */
    BODY,

    /**
     * 查询参数：URL中<code>?</code>后面的键值对参数，例如<code>/search?keyword=java&page=1</code>
     * 常用于GET请求传递过滤条件、分页信息等非敏感数据，支持多个同名参数
     */
    QUERY,

    /**
     * 请求头参数：HTTP请求头部的键值对信息，例如<code>Authorization: Bearer token</code>
     * 常用于传递认证信息、内容类型、客户端标识等元数据
     */
    HEADER,

    /**
     * Cookie参数：请求中携带的Cookie键值对，由浏览器自动随请求发送
     * 常用于存储会话ID（如JSESSIONID）、用户偏好等小型数据，需通过Cookie名称提取对应值
     */
    COOKIE,

    /**
     * 会话参数：存储在服务器端的会话（Session）数据，需通过请求关联的会话ID（通常来自Cookie）获取
     * 例如登录用户信息、临时会话状态等，数据存储在服务器（内存、Redis等），不直接通过请求传递
     * 提取时需依赖当前请求的会话上下文（如<code>request.getSession().getAttribute(key)</code>）
     */
    SESSION;

    /**
     * 将字符串解析为ParamSource枚举值，提供容错机制
     * 用于处理配置文件中可能的非法输入（如拼写错误、空值），避免因配置错误导致系统异常
     *
     * @param sourceStr 待解析的字符串（如配置文件中的参数来源值）
     * @return 对应的ParamSource枚举值；若解析失败则返回默认值QUERY
     */
    public static ParamSource fromString(String sourceStr) {
        // 处理空输入：日志警告并返回默认值
        if (sourceStr == null || sourceStr.isEmpty()) {
            log.warn("参数来源配置为空，自动使用默认值：{}", QUERY);
            return QUERY;
        }

        try {
            // 忽略大小写解析（如"session"、"Session"均解析为SESSION）
            return valueOf(sourceStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 处理非法值：日志报错并返回默认值
            log.error("参数来源配置错误，非法值：{}，自动使用默认值：{}", sourceStr, QUERY);
            return QUERY;
        }
    }
}