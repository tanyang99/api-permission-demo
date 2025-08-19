package com.security.context;

import com.security.enums.MultiParamMode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限上下文，存储验证过程中的相关数据
 */
public class PermissionContext {
    // 线程隔离的上下文容器
    private static final ThreadLocal<ContextData> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 初始化上下文
     */
    public static void init() {
        THREAD_LOCAL.set(new ContextData());
    }

    /**
     * 清理上下文（必须在请求结束时调用）
     */
    public static void clear() {
        THREAD_LOCAL.remove();
    }

    /**
     * 获取上下文数据
     */
    public static ContextData getContextData() {
        return THREAD_LOCAL.get();
    }

    /**
     * 设置上下文数据
     */
    public static void setContextData(ContextData data) {
        THREAD_LOCAL.set(data);
    }

    /**
     * 上下文核心数据
     */
    @Data
    public static class ContextData {
        private String uri; // 请求URI
        private boolean enabled; // 是否启用验证
        private PrincipalData principalData; // 主体数据
        private List<TargetParameter> targetParameters = new ArrayList<>(); // 目标参数列表
        private MultiParamMode multiParamMode; // 多参数验证模式
        // 精简参数名：是否使用缓存的request获取参数
        private boolean useCachedRequest;
    }

    /**
     * 主体数据（当前操作主体）
     */
    @Data
    public static class PrincipalData {
        private String name; // 主体名称（如"staffId"）
        private List<String> values; // 主体值（支持多值）
    }

    /**
     * 目标参数（需验证的参数）
     */
    @Data
    public static class TargetParameter {
        private String name; // 参数名
        private List<String> values; // 参数值（支持多值）
        private String validatorId; // 验证器ID
    }

}
