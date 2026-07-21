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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

        // --- 新增字段解析 ---
        String prizeType = extractString(request, "prizeType");
        if (prizeType == null) prizeType = "PHYSICAL";
        if (!"VIRTUAL".equals(prizeType) && !"PHYSICAL".equals(prizeType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "prizeType must be VIRTUAL or PHYSICAL");
        }
        String prizeCategory = extractString(request, "prizeCategory");
        String titleImage = extractString(request, "titleImage");
        String detailImage = extractString(request, "detailImage");
        LocalDateTime validFrom = null;
        if (request.containsKey("validFrom")) {
            validFrom = LocalDateTime.parse(extractString(request, "validFrom"));
        }
        LocalDateTime validTo = null;
        if (request.containsKey("validTo")) {
            validTo = LocalDateTime.parse(extractString(request, "validTo"));
        }
        String typeConfig = extractString(request, "typeConfig");
        if (typeConfig != null && !typeConfig.isEmpty()) {
            validateTypeConfig(prizeType, prizeCategory, typeConfig);
        }
        prize.setPrizeType(prizeType);
        prize.setPrizeCategory(prizeCategory);
        prize.setTitleImage(titleImage);
        prize.setDetailImage(detailImage);
        prize.setValidFrom(validFrom);
        prize.setValidTo(validTo);
        prize.setTypeConfig(typeConfig);

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

        if (request.containsKey("prizeType")) {
            String pt = extractString(request, "prizeType");
            if (!"VIRTUAL".equals(pt) && !"PHYSICAL".equals(pt)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "prizeType must be VIRTUAL or PHYSICAL");
            }
            prize.setPrizeType(pt);
        }
        if (request.containsKey("prizeCategory")) {
            prize.setPrizeCategory(extractString(request, "prizeCategory"));
        }
        if (request.containsKey("titleImage")) {
            prize.setTitleImage(extractString(request, "titleImage"));
        }
        if (request.containsKey("detailImage")) {
            prize.setDetailImage(extractString(request, "detailImage"));
        }
        if (request.containsKey("validFrom")) {
            prize.setValidFrom(LocalDateTime.parse(extractString(request, "validFrom")));
        }
        if (request.containsKey("validTo")) {
            prize.setValidTo(LocalDateTime.parse(extractString(request, "validTo")));
        }
        if (request.containsKey("typeConfig")) {
            String tc = extractString(request, "typeConfig");
            if (tc != null && !tc.isEmpty()) {
                validateTypeConfig(
                        prize.getPrizeType() != null ? prize.getPrizeType() : extractString(request, "prizeType"),
                        prize.getPrizeCategory() != null ? prize.getPrizeCategory() : extractString(request, "prizeCategory"),
                        tc);
            }
            prize.setTypeConfig(tc);
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

    private void validateTypeConfig(String prizeType, String prizeCategory, String typeConfig) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode config = mapper.readTree(typeConfig);
            if ("PHYSICAL".equals(prizeType)) {
                if (!config.has("actualValue")) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "PHYSICAL prize requires actualValue in typeConfig");
                }
            } else if ("VIRTUAL".equals(prizeType)) {
                if (prizeCategory == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "VIRTUAL prize requires prizeCategory");
                }
                switch (prizeCategory) {
                    case "TV_TIME":
                    case "COMPUTER_TIME":
                        if (!config.has("durationType") || !config.has("duration")) {
                            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                                    prizeCategory + " requires durationType and duration");
                        }
                        break;
                    case "PARK_PLAY":
                    case "GENERAL":
                        if (!config.has("maxUses")) {
                            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                                    prizeCategory + " requires maxUses");
                        }
                        break;
                    case "TRAVEL":
                        if (!config.has("destination") || !config.has("travelDays")
                                || !config.has("travelNights") || !config.has("actualValue")) {
                            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                                    "TRAVEL requires destination, travelDays, travelNights, actualValue");
                        }
                        break;
                    default:
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                                "Unknown prizeCategory: " + prizeCategory);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Invalid typeConfig JSON: " + e.getMessage());
        }
    }
}
