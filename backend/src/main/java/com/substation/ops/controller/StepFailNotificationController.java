package com.substation.ops.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.substation.ops.common.ApiResponse;
import com.substation.ops.dto.NotificationReadRequest;
import com.substation.ops.service.StepFailNotificationService;

import jakarta.validation.Valid;

/**
 * 倒闸操作失败站内通知：
 * 失败落库即向本票调度员、监护人各发一条；双方各自确认已读，互不覆盖；
 * 原步重试成功后转为「已解除」并永久留痕，不提供删除入口。
 */
@RestController
@RequestMapping("/api/notifications")
public class StepFailNotificationController {

    private final StepFailNotificationService service;

    public StepFailNotificationController(StepFailNotificationService service) {
        this.service = service;
    }

    /** 当前值班员的失败通知（未解除在前）；未选择身份时返回空列表 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String person) {
        return ApiResponse.success(service.listFor(person));
    }

    /** 顶部铃铛角标：未解除且尚未确认数 */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(@RequestParam(required = false) String person) {
        long count = service.unreadCount(person);
        return ApiResponse.success(Map.of("count", count));
    }

    /**
     * 确认已读：仅通知接收本人可操作，只更新自己那一行；
     * 已解除的通知也允许补确认，已读时间照留。
     */
    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id,
                                      @Valid @RequestBody NotificationReadRequest request) {
        service.markRead(id, request.getPerson());
        return ApiResponse.success("已确认", null);
    }
}
