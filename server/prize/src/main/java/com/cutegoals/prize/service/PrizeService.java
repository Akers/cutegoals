package com.cutegoals.prize.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.prize.Prize;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.prize.mapper.PrizeMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service for prize CRUD, stock management, and soft delete (Phase 5, Tasks 5.1-5.2).
 */
@Service
@RequiredArgsConstructor
public class PrizeService {

    private static final Logger log = LoggerFactory.getLogger(PrizeService.class);

    private final PrizeMapper prizeMapper;
    private final AuditService auditService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // ========== Task 5.1: Prize CRUD ==========

    /**
     * Create a prize record.
     */
    @Transactional
    public Prize createPrize(Map<String, Object> request, Long familyId, Long accountId) {
        String name = extractString(request, "name");
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Prize name is required");
        }
        name = name.trim();

        Integer pointsCost = extractInteger(request, "pointsCost");
        if (pointsCost == null || pointsCost < 1) {
            throw new BusinessException(ErrorCode.PRIZE_INVALID_POINTS_COST,
                    "pointsCost must be a positive integer, got: " + pointsCost);
        }

        Integer stock = extractInteger(request, "stock");
        if (stock == null || stock < 0) {
            throw new BusinessException(ErrorCode.PRIZE_INVALID_STOCK,
                    "stock must be a non-negative integer, got: " + stock);
        }

        String description = extractString(request, "description");
        String image = extractString(request, "image");

        Boolean enabled = request.containsKey("enabled") ?
                Boolean.TRUE.equals(request.get("enabled")) : true;

        Prize prize = new Prize();
        prize.setFamilyId(familyId);
        prize.setName(name);
        prize.setDescription(description);
        prize.setImage(image);
        prize.setPointsCost(pointsCost);
        prize.setStock(stock);
        prize.setEnabled(enabled);
        prize.setDeleted(false);

        prizeMapper.insert(prize);

        auditService.record(AuditEvent.PRIZE_CREATED, accountId, "SUCCESS",
                "Prize created: id=" + prize.getId() + ", name=" + name
                        + ", cost=" + pointsCost + ", stock=" + stock);

        log.info("Prize created: id={}, name={}, cost={}, stock={}, familyId={}",
                prize.getId(), name, pointsCost, stock, familyId);

        return prize;
    }

    /**
     * Update a prize record.
     */
    @Transactional
    public Prize updatePrize(Long prizeId, Map<String, Object> request, Long familyId, Long accountId) {
        Prize prize = prizeMapper.findByIdAndFamily(prizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize not found: " + prizeId));

        if (Boolean.TRUE.equals(prize.getDeleted())) {
            throw new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                    "Prize has been deleted: " + prizeId);
        }

        if (request.containsKey("name")) {
            String name = extractString(request, "name");
            if (name == null || name.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Prize name is required");
            }
            prize.setName(name.trim());
        }

        if (request.containsKey("description")) {
            prize.setDescription(extractString(request, "description"));
        }

        if (request.containsKey("image")) {
            prize.setImage(extractString(request, "image"));
        }

        if (request.containsKey("pointsCost")) {
            Integer pointsCost = extractInteger(request, "pointsCost");
            if (pointsCost == null || pointsCost < 1) {
                throw new BusinessException(ErrorCode.PRIZE_INVALID_POINTS_COST,
                        "pointsCost must be a positive integer, got: " + pointsCost);
            }
            prize.setPointsCost(pointsCost);
        }

        if (request.containsKey("stock")) {
            Integer stock = extractInteger(request, "stock");
            if (stock == null || stock < 0) {
                throw new BusinessException(ErrorCode.PRIZE_INVALID_STOCK,
                        "stock must be a non-negative integer, got: " + stock);
            }
            prize.setStock(stock);
        }

        if (request.containsKey("enabled")) {
            prize.setEnabled(Boolean.TRUE.equals(request.get("enabled")));
        }

        prizeMapper.updateById(prize);

        auditService.record(AuditEvent.PRIZE_UPDATED, accountId, "SUCCESS",
                "Prize updated: id=" + prizeId + ", name=" + prize.getName());

        log.info("Prize updated: id={}, familyId={}", prizeId, familyId);
        return prize;
    }

    /**
     * Query prizes with pagination and filters. Parent endpoint - shows all statuses.
     */
    public Map<String, Object> queryPrizes(Map<String, Object> params, Long familyId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        LambdaQueryWrapper<Prize> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Prize::getFamilyId, familyId);

        if (params.containsKey("enabled")) {
            wrapper.eq(Prize::getEnabled, Boolean.TRUE.equals(params.get("enabled")));
        }
        if (params.containsKey("deleted")) {
            wrapper.eq(Prize::getDeleted, Boolean.TRUE.equals(params.get("deleted")));
        } else {
            wrapper.eq(Prize::getDeleted, false);
        }

        wrapper.orderByDesc(Prize::getCreatedAt);
        wrapper.orderByDesc(Prize::getId);

        Page<Prize> page = prizeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", page.getRecords());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());
        return result;
    }

    /**
     * Query available prizes for children - only enabled, not deleted, stock > 0.
     */
    public Map<String, Object> queryAvailablePrizes(Map<String, Object> params, Long familyId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        LambdaQueryWrapper<Prize> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Prize::getFamilyId, familyId);
        wrapper.eq(Prize::getEnabled, true);
        wrapper.eq(Prize::getDeleted, false);
        wrapper.gt(Prize::getStock, 0);
        wrapper.orderByDesc(Prize::getCreatedAt);
        wrapper.orderByDesc(Prize::getId);

        Page<Prize> page = prizeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", page.getRecords());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());
        return result;
    }

    /**
     * Get a single prize by id (parent view).
     */
    public Prize getPrizeById(Long prizeId, Long familyId) {
        return prizeMapper.findByIdAndFamily(prizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize not found: " + prizeId));
    }

    /**
     * Get a single available prize by id (child view).
     */
    public Prize getAvailablePrizeById(Long prizeId, Long familyId) {
        return prizeMapper.findAvailableByIdAndFamily(prizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize not available: " + prizeId));
    }

    // ========== Task 5.2: Stock Adjustment & Soft Delete ==========

    /**
     * Soft delete a prize. If it has exchange history, keep record; otherwise physically delete.
     */
    @Transactional
    public void deletePrize(Long prizeId, Long familyId, Long accountId) {
        Prize prize = prizeMapper.findByIdAndFamily(prizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize not found: " + prizeId));

        if (Boolean.TRUE.equals(prize.getDeleted())) {
            throw new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                    "Prize already deleted: " + prizeId);
        }

        // Check if any exchange references this prize
        int exchangeCount = prizeMapper.countExchangesByPrizeId(prizeId);

        prize.setEnabled(false);
        prize.setDeleted(true);
        prize.setDeletedAt(LocalDateTime.now());
        prizeMapper.updateById(prize);

        auditService.record(AuditEvent.PRIZE_DELETED, accountId, "SUCCESS",
                "Prize deleted: id=" + prizeId + ", name=" + prize.getName()
                        + ", hadExchanges=" + (exchangeCount > 0));

        log.info("Prize soft-deleted: id={}, name={}, exchangeCount={}", prizeId, prize.getName(), exchangeCount);
    }

    /**
     * Adjust stock for a prize (parent operation).
     */
    @Transactional
    public Prize adjustStock(Long prizeId, Integer newStock, Long familyId, Long accountId) {
        if (newStock == null || newStock < 0) {
            throw new BusinessException(ErrorCode.PRIZE_INVALID_STOCK,
                    "stock must be a non-negative integer, got: " + newStock);
        }

        Prize prize = prizeMapper.findByIdAndFamily(prizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize not found: " + prizeId));

        if (Boolean.TRUE.equals(prize.getDeleted())) {
            throw new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                    "Prize has been deleted: " + prizeId);
        }

        prize.setStock(newStock);
        prizeMapper.updateById(prize);

        auditService.record(AuditEvent.PRIZE_STOCK_ADJUSTED, accountId, "SUCCESS",
                "Prize stock adjusted: id=" + prizeId + ", newStock=" + newStock);

        log.info("Prize stock adjusted: id={}, newStock={}", prizeId, newStock);
        return prize;
    }

    // ========== Internal helpers used by exchange module ==========

    /**
     * Decrement stock atomically with FOR UPDATE check. Returns true if successful.
     */
    @Transactional
    public boolean decrementStockAtomic(Long prizeId, Long familyId) {
        // Lock the prize row
        Prize prize = prizeMapper.findByIdAndFamilyForUpdate(prizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize not found: " + prizeId));

        if (Boolean.TRUE.equals(prize.getDeleted()) || Boolean.FALSE.equals(prize.getEnabled())) {
            throw new BusinessException(ErrorCode.PRIZE_OUT_OF_STOCK,
                    "Prize is not available for exchange");
        }

        if (prize.getStock() <= 0) {
            throw new BusinessException(ErrorCode.PRIZE_OUT_OF_STOCK,
                    "Prize out of stock: " + prizeId);
        }

        int updated = prizeMapper.decrementStock(prizeId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.PRIZE_OUT_OF_STOCK,
                    "Prize out of stock (concurrent): " + prizeId);
        }
        return true;
    }

    /**
     * Increment stock atomically (for cancellation refund).
     */
    @Transactional
    public void incrementStockAtomic(Long prizeId) {
        prizeMapper.incrementStock(prizeId);
    }

    // ========== Helpers ==========

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
}
