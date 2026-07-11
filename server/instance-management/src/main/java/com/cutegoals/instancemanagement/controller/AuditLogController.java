package com.cutegoals.instancemanagement.controller;

import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.instancemanagement.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log query and export (Task 6.4).
 * GET /api/admin/audit-logs
 * GET /api/admin/audit-logs/export
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> queryAuditLogs(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }
        Map<String, Object> data = auditLogService.queryAuditLogs(
                actorId, eventType, result, startDate, endDate, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }

    @GetMapping("/export")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> exportAuditLogs(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String requestId = MDC.get("requestId");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("requestId", requestId);
        }
        List<Map<String, Object>> data = auditLogService.exportAuditLogs(
                actorId, eventType, result, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(data, requestId));
    }
}
