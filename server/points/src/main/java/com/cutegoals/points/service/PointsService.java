package com.cutegoals.points.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.points.PointsBalance;
import com.cutegoals.common.entity.points.PointsLedger;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.points.mapper.PointsBalanceMapper;
import com.cutegoals.points.mapper.PointsLedgerMapper;
import com.cutegoals.task.mapper.TaskAssignmentMapper;
import com.cutegoals.task.mapper.TaskChildMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for points ledger management, balance, adjustments, and queries (Phase 4, Tasks 4.8-4.10).
 */
@Service
@RequiredArgsConstructor
public class PointsService {

    private static final Logger log = LoggerFactory.getLogger(PointsService.class);

    private final PointsLedgerMapper pointsLedgerMapper;
    private final PointsBalanceMapper pointsBalanceMapper;
    private final TaskChildMapper taskChildMapper;
    private final FamilyMapper familyMapper;
    private final AuditService auditService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ADJUST_AMOUNT_ABS = 1_000_000_000;

    // ========== Task 4.8: Balance Query ==========

    /**
     * Get points balance for a child.
     */
    public PointsBalance getBalance(Long childId, Long familyId, Long viewerChildId) {
        // Verify access: viewer check first (fast path), then family scope
        if (viewerChildId != null && !viewerChildId.equals(childId)) {
            throw new BusinessException(ErrorCode.POINTS_FORBIDDEN,
                    "You can only view your own balance");
        }
        validateChildInFamily(childId, familyId);

        return pointsBalanceMapper.findByChildId(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                        "Points account not found for child: " + childId));
    }

    // ========== Task 4.9: Parent Manual Adjustment ==========

    /**
     * Parent manually adjusts a child's points (ADJUST type).
     * Amount can be positive or negative, abs <= 1,000,000,000.
     * Requires reason 1-500 chars and unique business reference.
     */
    @Transactional
    public PointsLedger adjustPoints(Map<String, Object> request, Long childId, Long familyId, Long accountId,
                                      List<String> roles) {
        // Only parents can adjust
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.POINTS_FORBIDDEN,
                    "Only parents can adjust points");
        }

        Integer amount = extractInteger(request, "amount");
        String reason = extractString(request, "reason");
        String businessRef = extractString(request, "businessRef");

        // Validate amount
        if (amount == null || amount == 0) {
            throw new BusinessException(ErrorCode.POINTS_INVALID_TRANSACTION,
                    "Amount must be a non-zero integer");
        }
        if (Math.abs(amount) > MAX_ADJUST_AMOUNT_ABS) {
            throw new BusinessException(ErrorCode.POINTS_INVALID_TRANSACTION,
                    "Amount must not exceed " + MAX_ADJUST_AMOUNT_ABS + " in absolute value");
        }

        // Validate reason (must be 1-500 chars)
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.POINTS_ADJUST_REASON_REQUIRED,
                    "Adjustment reason is required");
        }
        reason = reason.trim();
        if (reason.length() < 1 || reason.length() > 500) {
            throw new BusinessException(ErrorCode.POINTS_INVALID_TRANSACTION,
                    "Adjustment reason must be between 1 and 500 characters");
        }

        // Validate business ref
        if (businessRef == null || businessRef.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "businessRef is required for adjustment");
        }
        businessRef = businessRef.trim();
        if (businessRef.length() > 128) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "businessRef must not exceed 128 characters");
        }

        // Verify child belongs to family
        ChildProfile child = taskChildMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                        "Child not found: " + childId));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                    "Child does not belong to your family");
        }

        // Check business ref uniqueness
        Optional<PointsLedger> existing = pointsLedgerMapper.findByBusinessRef(childId, businessRef);
        if (existing.isPresent()) {
            PointsLedger er = existing.get();
            if (!"ADJUST".equals(er.getType()) || !amount.equals(er.getAmount())) {
                throw new BusinessException(ErrorCode.POINTS_REFERENCE_CONFLICT,
                        "Business reference already used with different parameters");
            }
            return er;
        }

        // Fetch balance with lock
        PointsBalance balance = pointsBalanceMapper.findByChildIdForUpdate(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                        "Points account not found for child: " + childId));

        // Calculate new balance
        int newBalance = balance.getBalance() + amount;
        if (newBalance < 0) {
            throw new BusinessException(ErrorCode.POINTS_INSUFFICIENT_BALANCE,
                    "Insufficient balance for negative adjustment. Current: " + balance.getBalance()
                            + ", requested: " + amount);
        }

        // Create ledger entry
        PointsLedger ledger = new PointsLedger();
        ledger.setChildId(childId);
        ledger.setType("ADJUST");
        ledger.setAmount(amount);
        ledger.setBalanceAfter(newBalance);
        ledger.setBusinessRef(businessRef);
        ledger.setSourceSnapshot(String.format("{\"reason\":\"%s\"}", escapeJson(reason)));
        ledger.setOperatorId(accountId);
        ledger.setReason(reason);
        try {
            pointsLedgerMapper.insert(ledger);
        } catch (org.apache.ibatis.exceptions.PersistenceException ex) {
            // Defensive: handle duplicate businessRef for concurrent ADJUST requests
            if (ex.getCause() instanceof java.sql.SQLException sqlEx && sqlEx.getMessage() != null
                    && sqlEx.getMessage().toLowerCase().contains("business_ref")) {
                throw new BusinessException(ErrorCode.POINTS_REFERENCE_CONFLICT,
                        "Duplicate business reference for adjustment: " + businessRef);
            }
            throw ex;
        }

        // Update balance with optimistic lock (totalEarned stays unchanged for ADJUST)
        int updated;
        if (amount > 0) {
            // Positive adjust: only balance changes, totalEarned unchanged
            updated = pointsBalanceMapper.updateBalanceOnlyWithVersion(
                    childId, newBalance, balance.getVersion());
        } else {
            // Negative adjust: only balance changes
            updated = pointsBalanceMapper.updateBalanceOnlyWithVersion(
                    childId, newBalance, balance.getVersion());
        }
        if (updated == 0) {
            throw new BusinessException(ErrorCode.POINTS_ACCOUNT_CONFLICT,
                    "Points balance was modified concurrently");
        }

        auditService.record(AuditEvent.POINTS_ADJUST, accountId, "SUCCESS",
                "Points adjusted: childId=" + childId + ", amount=" + amount
                        + ", reason=" + reason + ", ref=" + businessRef);

        log.info("Points adjusted: childId={}, amount={}, ref={}, newBalance={}",
                childId, amount, businessRef, newBalance);

        return ledger;
    }

    // ========== Phase 5: Spend & Refund for Exchange ==========

    /**
     * Spend points for an exchange. Creates SPEND ledger entry and updates balance.
     * Must be called within an existing transaction.
     */
    @Transactional
    public PointsLedger spendPoints(Long childId, Long familyId, int amount,
                                     String businessRef, String reason) {
        if (amount < 1) {
            throw new BusinessException(ErrorCode.POINTS_INVALID_TRANSACTION,
                    "Spend amount must be positive, got: " + amount);
        }

        // Validate child belongs to family
        ChildProfile child = taskChildMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                        "Child not found: " + childId));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.POINTS_FORBIDDEN,
                    "Child does not belong to your family");
        }

        // Check business ref uniqueness
        Optional<PointsLedger> existing = pointsLedgerMapper.findByBusinessRef(childId, businessRef);
        if (existing.isPresent()) {
            PointsLedger er = existing.get();
            if (!"SPEND".equals(er.getType()) || er.getAmount() != amount) {
                throw new BusinessException(ErrorCode.POINTS_REFERENCE_CONFLICT,
                        "Business reference already used with different parameters");
            }
            return er;
        }

        // Fetch balance with lock
        PointsBalance balance = pointsBalanceMapper.findByChildIdForUpdate(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                        "Points account not found for child: " + childId));

        int newBalance = balance.getBalance() - amount;
        if (newBalance < 0) {
            throw new BusinessException(ErrorCode.POINTS_INSUFFICIENT_BALANCE,
                    "Insufficient balance. Current: " + balance.getBalance() + ", required: " + amount);
        }

        // Create SPEND ledger entry
        PointsLedger ledger = new PointsLedger();
        ledger.setChildId(childId);
        ledger.setType("SPEND");
        ledger.setAmount(amount);
        ledger.setBalanceAfter(newBalance);
        ledger.setBusinessRef(businessRef);
        ledger.setSourceSnapshot(String.format("{\"reason\":\"%s\"}", escapeJson(reason != null ? reason : "")));
        try {
            pointsLedgerMapper.insert(ledger);
        } catch (org.apache.ibatis.exceptions.PersistenceException ex) {
            if (ex.getCause() instanceof java.sql.SQLException sqlEx && sqlEx.getMessage() != null
                    && sqlEx.getMessage().toLowerCase().contains("business_ref")) {
                throw new BusinessException(ErrorCode.POINTS_REFERENCE_CONFLICT,
                        "Duplicate business reference: " + businessRef);
            }
            throw ex;
        }

        // Update balance (totalEarned stays unchanged for SPEND)
        int updated = pointsBalanceMapper.updateBalanceWithVersion(
                childId, newBalance, balance.getTotalEarned(), balance.getVersion());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.POINTS_ACCOUNT_CONFLICT,
                    "Points balance was modified concurrently");
        }

        auditService.record(AuditEvent.POINTS_SPEND, null, "SUCCESS",
                "Points spent: childId=" + childId + ", amount=" + amount + ", ref=" + businessRef);

        log.info("Points spent: childId={}, amount={}, ref={}, newBalance={}",
                childId, amount, businessRef, newBalance);

        return ledger;
    }

    /**
     * Refund points for a cancelled exchange. Creates REFUND ledger entry and updates balance.
     * Must be called within an existing transaction.
     */
    @Transactional
    public PointsLedger refundPoints(Long childId, Long familyId, int amount,
                                      String businessRef, String reason) {
        if (amount < 1) {
            throw new BusinessException(ErrorCode.POINTS_INVALID_TRANSACTION,
                    "Refund amount must be positive, got: " + amount);
        }

        // Validate child belongs to family
        ChildProfile child = taskChildMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                        "Child not found: " + childId));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.POINTS_FORBIDDEN,
                    "Child does not belong to your family");
        }

        // Check business ref uniqueness
        Optional<PointsLedger> existing = pointsLedgerMapper.findByBusinessRef(childId, businessRef);
        if (existing.isPresent()) {
            PointsLedger er = existing.get();
            if (!"REFUND".equals(er.getType()) || er.getAmount() != amount) {
                throw new BusinessException(ErrorCode.POINTS_REFERENCE_CONFLICT,
                        "Business reference already used with different parameters");
            }
            return er;
        }

        // Fetch balance with lock
        PointsBalance balance = pointsBalanceMapper.findByChildIdForUpdate(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                        "Points account not found for child: " + childId));

        int newBalance = balance.getBalance() + amount;

        // Create REFUND ledger entry
        PointsLedger ledger = new PointsLedger();
        ledger.setChildId(childId);
        ledger.setType("REFUND");
        ledger.setAmount(amount);
        ledger.setBalanceAfter(newBalance);
        ledger.setBusinessRef(businessRef);
        ledger.setSourceSnapshot(String.format("{\"reason\":\"%s\"}", escapeJson(reason != null ? reason : "")));
        try {
            pointsLedgerMapper.insert(ledger);
        } catch (org.apache.ibatis.exceptions.PersistenceException ex) {
            if (ex.getCause() instanceof java.sql.SQLException sqlEx && sqlEx.getMessage() != null
                    && sqlEx.getMessage().toLowerCase().contains("business_ref")) {
                throw new BusinessException(ErrorCode.POINTS_REFERENCE_CONFLICT,
                        "Duplicate business reference: " + businessRef);
            }
            throw ex;
        }

        // Update balance (totalEarned stays unchanged for REFUND)
        int updated = pointsBalanceMapper.updateBalanceWithVersion(
                childId, newBalance, balance.getTotalEarned(), balance.getVersion());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.POINTS_ACCOUNT_CONFLICT,
                    "Points balance was modified concurrently");
        }

        auditService.record(AuditEvent.POINTS_REFUND, null, "SUCCESS",
                "Points refunded: childId=" + childId + ", amount=" + amount + ", ref=" + businessRef);

        log.info("Points refunded: childId={}, amount={}, ref={}, newBalance={}",
                childId, amount, businessRef, newBalance);

        return ledger;
    }

    // ========== Task 4.10: Ledger Query ==========

    /**
     * Query points ledger for a specific child with filters.
     */
    public Map<String, Object> queryLedger(Long childId, Map<String, Object> params,
                                            Long familyId, Long viewerChildId) {
        // Verify access: viewer check first (fast path), then family scope
        if (viewerChildId != null && !viewerChildId.equals(childId)) {
            throw new BusinessException(ErrorCode.POINTS_FORBIDDEN,
                    "You can only view your own ledger");
        }
        validateChildInFamily(childId, familyId);

        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.POINTS_INVALID_QUERY,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        LambdaQueryWrapper<PointsLedger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsLedger::getChildId, childId);

        // Filter by type
        if (params.containsKey("type")) {
            String type = (String) params.get("type");
            if (!Set.of("EARN", "SPEND", "REFUND", "ADJUST").contains(type)) {
                throw new BusinessException(ErrorCode.POINTS_INVALID_QUERY,
                        "Invalid ledger type: " + type);
            }
            wrapper.eq(PointsLedger::getType, type);
        }

        // Filter by date range (Asia/Shanghai)
        if (params.containsKey("startDate") && params.containsKey("endDate")) {
            String startStr = (String) params.get("startDate");
            String endStr = (String) params.get("endDate");
            LocalDate startDate = LocalDate.parse(startStr);
            LocalDate endDate = LocalDate.parse(endStr);

            if (startDate.isAfter(endDate)) {
                throw new BusinessException(ErrorCode.POINTS_INVALID_QUERY,
                        "Start date must not be after end date");
            }

            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();
            wrapper.ge(PointsLedger::getCreatedAt, startDateTime);
            wrapper.lt(PointsLedger::getCreatedAt, endDateTime);
        }

        // Order by created_at DESC, id DESC
        wrapper.orderByDesc(PointsLedger::getCreatedAt);
        wrapper.orderByDesc(PointsLedger::getId);

        Page<PointsLedger> page = pointsLedgerMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        // Get current balance
        PointsBalance balance = pointsBalanceMapper.findByChildId(childId).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", page.getRecords());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());
        if (balance != null) {
            result.put("currentBalance", balance.getBalance());
            result.put("totalEarned", balance.getTotalEarned());
        }
        return result;
    }

    /**
     * Get family summary for points: per-child balance and period summary.
     */
    public Map<String, Object> getFamilySummary(Map<String, Object> params, Long familyId) {
        List<ChildProfile> children = taskChildMapper.findActiveByFamilyId(familyId);

        String startDateStr = (String) params.get("startDate");
        String endDateStr = (String) params.get("endDate");

        LocalDate startDate = startDateStr != null ? LocalDate.parse(startDateStr) : LocalDate.now().withDayOfMonth(1);
        LocalDate endDate = endDateStr != null ? LocalDate.parse(endDateStr) : LocalDate.now();

        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.POINTS_INVALID_QUERY,
                    "Start date must not be after end date");
        }

        ZoneId shanghaiZone = ZoneId.of("Asia/Shanghai");
        LocalDateTime periodStart = startDate.atStartOfDay();
        LocalDateTime periodEnd = endDate.plusDays(1).atStartOfDay();

        List<Map<String, Object>> childSummaries = new ArrayList<>();
        for (ChildProfile child : children) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("childId", child.getId());
            summary.put("nickname", child.getNickname());

            PointsBalance balance = pointsBalanceMapper.findByChildId(child.getId()).orElse(null);
            if (balance != null) {
                summary.put("currentBalance", balance.getBalance());
                summary.put("totalEarned", balance.getTotalEarned());
            } else {
                summary.put("currentBalance", 0);
                summary.put("totalEarned", 0);
            }

            // Calculate period opening balance (balance before period start)
            int openingBalance = pointsLedgerMapper.sumBalanceBefore(child.getId(), periodStart);
            summary.put("openingBalance", openingBalance);

            // Get period transactions summary
            List<PointsLedger> periodLedgers = pointsLedgerMapper.findByChildIdAndDateRange(
                    child.getId(), periodStart, periodEnd);

            int earnTotal = 0, spendTotal = 0, refundTotal = 0, posAdjustTotal = 0, negAdjustTotal = 0;
            for (PointsLedger pl : periodLedgers) {
                switch (pl.getType()) {
                    case "EARN" -> earnTotal += pl.getAmount();
                    case "SPEND" -> spendTotal += pl.getAmount();
                    case "REFUND" -> refundTotal += pl.getAmount();
                    case "ADJUST" -> {
                        if (pl.getAmount() > 0) posAdjustTotal += pl.getAmount();
                        else negAdjustTotal += Math.abs(pl.getAmount());
                    }
                }
            }
            summary.put("earnTotal", earnTotal);
            summary.put("spendTotal", spendTotal);
            summary.put("refundTotal", refundTotal);
            summary.put("positiveAdjustTotal", posAdjustTotal);
            summary.put("negativeAdjustTotal", negAdjustTotal);

            // Calculate period closing balance
            int closingBalance = openingBalance + earnTotal + refundTotal + posAdjustTotal - spendTotal - negAdjustTotal;
            summary.put("closingBalance", Math.max(closingBalance, 0));

            childSummaries.add(summary);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());
        result.put("children", childSummaries);
        return result;
    }

    // ========== Helpers ==========

    private void validateChildInFamily(Long childId, Long familyId) {
        ChildProfile child = taskChildMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POINTS_ACCOUNT_NOT_FOUND,
                        "Child not found: " + childId));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.POINTS_FORBIDDEN,
                    "Child does not belong to your family");
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return val.toString();
    }

    private Integer extractInteger(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return ((Number) val).intValue();
    }

    public Long getSingleFamilyId() {
        var families = familyMapper.selectList(null);
        if (families.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "No family found");
        }
        return families.get(0).getId();
    }

    public void verifyParentRole(List<String> roles) {
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.POINTS_FORBIDDEN);
        }
    }
}
