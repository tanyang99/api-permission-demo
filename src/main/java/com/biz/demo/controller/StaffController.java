package com.biz.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/")
public class StaffController {

    /**
     * 获取员工的日程安排
     * 演示水平越权防护的接口
     */
    @PostMapping("api/staffs/{staffId}/schedules")
    public ResponseEntity<Map<String, Object>> getStaffSchedules(
            @PathVariable String staffId,
            @RequestParam(required = false) String classId,
            @RequestBody Map<String, Object> requestBody) {

        Map<String, Object> response = new HashMap<String, Object>();
        response.put("status", "success");
        response.put("message", "成功获取日程安排");
        response.put("staffId", staffId);
        response.put("classId", classId);
        response.put("userId", requestBody.get("userId"));
        response.put("data", "日程数据...");

        return ResponseEntity.ok(response);
    }


    @GetMapping("/staffs/{staffId}/logs/{id}")
    public ResponseEntity<Map<String, Object>> logs(
            @PathVariable String staffId,
            @PathVariable Long id,
            @RequestParam(required = false) String classId) {

        Map<String, Object> response = new HashMap<String, Object>();
        response.put("status", "success");
        response.put("message", "成功获取日程安排");
        response.put("classId", classId);
        response.put("staffId", staffId);
        response.put("id", id);

        return ResponseEntity.ok(response);
    }

}


