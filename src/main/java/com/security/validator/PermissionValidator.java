package com.security.validator;

import com.security.context.PermissionContext;

/**
 * 权限验证器接口：定义参数与主体的归属验证逻辑
 */
public interface PermissionValidator {
    /**
     * 验证目标参数是否归属当前主体
     * @param principal 主体数据
     * @param target 目标参数
     * @return 验证结果（true：通过；false：不通过）
     */
    boolean validate(PermissionContext.PrincipalData principal, PermissionContext.TargetParameter target);
    
    /**
     * 验证器唯一标识
     * @return 验证器ID（与配置中的validatorId对应）
     */
    String getValidatorId();
}
