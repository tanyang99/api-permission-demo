package com.biz.demo.validator;

import com.biz.demo.service.UserRelationService;
import com.security.context.PermissionContext;
import com.security.validator.PermissionValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StaffUserIdValidator implements PermissionValidator {
    @Autowired
    private UserRelationService userRelationService;

    @Override
    public boolean validate(PermissionContext.PrincipalData principal, PermissionContext.TargetParameter target) {
        // 主体值（员工ID）
        String staffId = principal.getValues().get(0);
        // 目标参数值（用户ID列表）
        List<String> userIds = target.getValues();
        
        // 验证所有userId是否属于当前staffId
        for (String userId : userIds) {
            if (!userRelationService.isStaffOwnerOfUser(staffId, userId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getValidatorId() {
        return "staffId-userId"; // 与配置中validatorId对应
    }
}
