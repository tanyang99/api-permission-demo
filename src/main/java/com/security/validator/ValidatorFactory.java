package com.security.validator;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Order()
@Slf4j
@Component
public class ValidatorFactory implements InitializingBean {
    private final Map<String, PermissionValidator> validatorMap = new HashMap<>();

    // 注入所有PermissionValidator实现类（Spring自动扫描）
    @Setter
    @Autowired(required = false)
    private List<PermissionValidator> validators;

    /**
     * 根据validatorId获取验证器
     */
    public PermissionValidator getValidator(String validatorId) {
        PermissionValidator validator = validatorMap.get(validatorId);
        if (validator == null) {
            throw new IllegalArgumentException("不存在的验证器ID：" + validatorId);
        }
        return validator;
    }

    /**
     * 获取所有注册的验证器ID
     */
    public List<String> getAllValidatorIds() {
        return new ArrayList<>(validatorMap.keySet());
    }

    /**
     * InitializingBean回调方法：初始化时注册所有验证器
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 处理空列表情况（无任何验证器实现时）
        if (validators == null || validators.isEmpty()) {
            log.warn("未发现任何PermissionValidator实现类");
            return;
        }

        // 遍历验证器列表，注册到map中
        for (PermissionValidator validator : validators) {
            String validatorId = validator.getValidatorId();

            // 检查ID有效性
            if (validatorId == null || validatorId.trim().isEmpty()) {
                log.error("验证器[{}]的validatorId为空，已跳过注册", validator.getClass().getName());
                continue;
            }

            // 处理ID冲突
            if (validatorMap.containsKey(validatorId)) {
                log.warn("验证器ID[{}]冲突，已存在实现[{}]，新实现[{}]将覆盖旧实现", validatorId, validatorMap.get(validatorId).getClass().getName(), validator.getClass().getName());
            }

            // 注册验证器
            validatorMap.put(validatorId, validator);
            log.debug("验证器[{}]已注册，ID: {}", validator.getClass().getSimpleName(), validatorId);
        }

        log.info("验证器初始化完成，共注册{}个实现类，ID列表: {}", validatorMap.size(), validatorMap.keySet());
    }

}
