package com.biz.demo.validator;

import com.biz.demo.service.UserRelationService;
import com.security.context.PermissionContext;
import com.security.validator.PermissionValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StaffClassIdValidator implements PermissionValidator {
    @Autowired
    private UserRelationService userRelationService;

    @Override
    public boolean validate(PermissionContext.PrincipalData principal, PermissionContext.TargetParameter target) {
        // 主体值（员工ID）
        String staffId = principal.getValues().get(0);
        // 目标参数值（班级ID列表）
        List<String> classIds = target.getValues();
        
        // 验证所有classId是否属于当前staffId
        for (String classId : classIds) {
            if (!userRelationService.isStaffOwnerOfClass(staffId, classId)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getValidatorId() {
        return "staffId-classId"; // 与配置中validatorId对应
    }
}
