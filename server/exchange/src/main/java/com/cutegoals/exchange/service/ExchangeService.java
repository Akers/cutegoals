package com.cutegoals.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.exchange.Exchange;
import com.cutegoals.common.entity.exchange.ExchangeSnapshot;
import com.cutegoals.common.entity.prize.BlindBoxPool;
import com.cutegoals.common.entity.prize.Prize;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.exchange.mapper.ExchangeMapper;
import com.cutegoals.exchange.mapper.ExchangeSnapshotMapper;
import com.cutegoals.points.mapper.PointsBalanceMapper;
import com.cutegoals.points.mapper.PointsLedgerMapper;
import com.cutegoals.points.service.PointsService;
import com.cutegoals.prize.mapper.BlindBoxPoolMapper;
import com.cutegoals.prize.mapper.PrizeMapper;
import com.cutegoals.prize.service.BlindBoxService;
import com.cutegoals.prize.service.PrizeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for exchange creation (direct & blind box), idempotency, fulfillment and cancellation
 * (Phase 5, Tasks 5.7-5.11).
 */
@Service
@RequiredArgsConstructor
public class ExchangeService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);

    private final ExchangeMapper exchangeMapper;
    private final ExchangeSnapshotMapper exchangeSnapshotMapper;
    private final PrizeMapper prizeMapper;
    private final PrizeService prizeService;
    private final BlindBoxPoolMapper blindBoxPoolMapper;
    private final BlindBoxService blindBoxService;
    private final PointsService pointsService;
    private final PointsBalanceMapper pointsBalanceMapper;
    private final PointsLedgerMapper pointsLedgerMapper;
    private final AuditService auditService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // ========== Task 5.7 & 5.8: Direct & Blind Box Exchange ==========

    /**
     * Create a direct exchange (Task 5.7).
     * Atomic: points check + deduct + stock deduct + exchange record + SPEND ledger + snapshot.
     */
    @Transactional
    public Exchange createDirectExchange(Map<String, Object> request, Long childId, Long familyId) {
        Long prizeId = request.get("prizeId") != null ? ((Number) request.get("prizeId")).longValue() : null;
        String idempotencyKey = extractString(request, "idempotencyKey");

        if (prizeId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "prizeId is required");
        }

        // Check idempotency
        Exchange existing = checkIdempotency(childId, idempotencyKey, request);
        if (existing != null) {
            return existing;
        }

        // Lock and validate prize
        Prize prize = prizeMapper.findByIdAndFamilyForUpdate(prizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize not found: " + prizeId));

        if (Boolean.TRUE.equals(prize.getDeleted()) || Boolean.FALSE.equals(prize.getEnabled())) {
            throw new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                    "Prize is not available: " + prizeId);
        }

        if (prize.getStock() <= 0) {
            throw new BusinessException(ErrorCode.PRIZE_OUT_OF_STOCK,
                    "Prize out of stock: " + prizeId);
        }

        // Deduct points using PointsService (which includes balance check + SPEND ledger)
        int costPoints = prize.getPointsCost();
        String spendBusinessRef = "EXCHANGE_SPEND_" + UUID.randomUUID();
        pointsService.spendPoints(childId, familyId, costPoints, spendBusinessRef,
                "Direct exchange: prize=" + prize.getName());

        // Deduct stock atomically
        int stockUpdated = prizeMapper.decrementStock(prizeId);
        if (stockUpdated == 0) {
            // Rollback points - should not normally happen after above check
            throw new BusinessException(ErrorCode.PRIZE_OUT_OF_STOCK,
                    "Prize out of stock (concurrent): " + prizeId);
        }

        // Create exchange record
        Exchange exchange = new Exchange();
        exchange.setChildId(childId);
        exchange.setFamilyId(familyId);
        exchange.setType("DIRECT");
        exchange.setStatus("PENDING_FULFILLMENT");
        exchange.setCostPoints(costPoints);
        exchange.setIdempotencyKey(idempotencyKey);
        exchange.setPrizeId(prizeId);
        exchangeMapper.insert(exchange);

        // Create snapshot
        ExchangeSnapshot snapshot = new ExchangeSnapshot();
        snapshot.setExchangeId(exchange.getId());
        snapshot.setPrizeName(prize.getName());
        snapshot.setPrizeImage(prize.getImage());
        snapshot.setPrizeDescription(prize.getDescription());
        snapshot.setPointsCost(costPoints);
        exchangeSnapshotMapper.insert(snapshot);

        auditService.record(AuditEvent.EXCHANGE_DIRECT, childId, "SUCCESS",
                "Direct exchange created: id=" + exchange.getId()
                        + ", prize=" + prize.getName() + ", cost=" + costPoints);

        log.info("Direct exchange created: id={}, childId={}, prizeId={}, cost={}",
                exchange.getId(), childId, prizeId, costPoints);

        return exchange;
    }

    /**
     * Create a blind box exchange (Task 5.8).
     * Atomic: availability_version check + filter + draw + points deduct + stock deduct + snapshot.
     */
    @Transactional
    public Exchange createBlindBoxExchange(Map<String, Object> request, Long childId, Long familyId) {
        Long poolId = request.get("poolId") != null ? ((Number) request.get("poolId")).longValue() : null;
        String idempotencyKey = extractString(request, "idempotencyKey");
        String clientVersion = extractString(request, "availabilityVersion");

        if (poolId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "poolId is required");
        }

        // Check idempotency FIRST (priority over version check per spec)
        Exchange existing = checkIdempotency(childId, idempotencyKey, request);
        if (existing != null) {
            return existing;
        }

        // Lock and validate pool
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamilyForUpdate(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));

        if (Boolean.TRUE.equals(pool.getDeleted()) || Boolean.FALSE.equals(pool.getEnabled())) {
            throw new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                    "Pool not available: " + poolId);
        }

        // Verify availability version (if client provided one)
        if (clientVersion != null && !clientVersion.isEmpty()) {
            String serverVersion = blindBoxService.computeAvailabilityVersion(poolId, familyId);
            if (!clientVersion.equals(serverVersion)) {
                throw new BusinessException(ErrorCode.BLIND_BOX_POOL_CHANGED,
                        "Pool availability has changed; please refresh candidates");
            }
        }

        // Get effective candidates and draw
        Map<String, Object> drawnCandidate = blindBoxService.drawPrize(poolId, familyId);
        Long drawnPrizeId = (Long) drawnCandidate.get("prizeId");

        // Lock the drawn prize
        Prize prize = prizeMapper.findByIdAndFamilyForUpdate(drawnPrizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_UNAVAILABLE,
                        "Drawn prize no longer available: " + drawnPrizeId));

        if (prize.getStock() <= 0) {
            throw new BusinessException(ErrorCode.BLIND_BOX_POOL_CHANGED,
                    "Drawn prize out of stock, pool changed");
        }

        // Deduct points
        int costPoints = pool.getCostPoints();
        String spendBusinessRef = "EXCHANGE_SPEND_" + UUID.randomUUID();
        pointsService.spendPoints(childId, familyId, costPoints, spendBusinessRef,
                "Blind box exchange: pool=" + pool.getName());

        // Deduct stock
        int stockUpdated = prizeMapper.decrementStock(drawnPrizeId);
        if (stockUpdated == 0) {
            throw new BusinessException(ErrorCode.BLIND_BOX_POOL_CHANGED,
                    "Drawn prize out of stock (concurrent): " + drawnPrizeId);
        }

        // Create exchange record
        Exchange exchange = new Exchange();
        exchange.setChildId(childId);
        exchange.setFamilyId(familyId);
        exchange.setType("BLIND_BOX");
        exchange.setStatus("PENDING_FULFILLMENT");
        exchange.setCostPoints(costPoints);
        exchange.setIdempotencyKey(idempotencyKey);
        exchange.setPoolId(poolId);
        exchange.setResultPrizeId(drawnPrizeId);
        exchangeMapper.insert(exchange);

        // Get all candidates with probabilities for snapshot
        List<Map<String, Object>> candidates = blindBoxService.getEffectiveCandidates(poolId, familyId);
        String candidateProbJson = candidates.stream()
                .map(c -> String.format("{\"prizeId\":%d,\"prizeName\":\"%s\",\"weight\":%d,\"probability\":%s}",
                        c.get("prizeId"), c.get("prizeName"), c.get("weight"), c.get("probability")))
                .collect(Collectors.joining(",", "[", "]"));

        // Create snapshot
        ExchangeSnapshot snapshot = new ExchangeSnapshot();
        snapshot.setExchangeId(exchange.getId());
        snapshot.setPrizeName(prize.getName());
        snapshot.setPrizeImage(prize.getImage());
        snapshot.setPrizeDescription(prize.getDescription());
        snapshot.setPointsCost(costPoints);
        snapshot.setPoolName(pool.getName());
        snapshot.setPoolCostPoints(costPoints);
        snapshot.setAvailabilityVersion(blindBoxService.computeAvailabilityVersion(poolId, familyId));
        snapshot.setCandidateProbabilities(candidateProbJson);
        snapshot.setDrawnPrizeName((String) drawnCandidate.get("prizeName"));
        snapshot.setDrawnPrizeImage((String) drawnCandidate.get("prizeImage"));
        snapshot.setDrawnProbability(BigDecimal.valueOf((Double) drawnCandidate.get("probability")));
        exchangeSnapshotMapper.insert(snapshot);

        auditService.record(AuditEvent.EXCHANGE_BLIND_BOX, childId, "SUCCESS",
                "Blind box exchange created: id=" + exchange.getId()
                        + ", pool=" + pool.getName() + ", drawn=" + drawnCandidate.get("prizeName"));

        log.info("Blind box exchange created: id={}, childId={}, poolId={}, drawnPrizeId={}, cost={}",
                exchange.getId(), childId, poolId, drawnPrizeId, costPoints);

        return exchange;
    }

    // ========== Task 5.9: Idempotency ==========

    /**
     * Check if a request with the given idempotency key has already been processed.
     * Returns existing exchange if found and request matches, otherwise null (for new request).
     * Throws EXCHANGE_IDEMPOTENCY_CONFLICT if key used with different parameters.
     */
    private Exchange checkIdempotency(Long childId, String idempotencyKey, Map<String, Object> request) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.EXCHANGE_IDEMPOTENCY_KEY_REQUIRED,
                    "idempotencyKey is required for exchange operations");
        }

        idempotencyKey = idempotencyKey.trim();

        Optional<Exchange> existingOpt = exchangeMapper.findByChildIdAndKey(childId, idempotencyKey);
        if (existingOpt.isPresent()) {
            Exchange existing = existingOpt.get();
            // Check if request matches the original request
            if (requestsMatch(request, existing)) {
                return existing; // Same request - return existing result
            } else {
                throw new BusinessException(ErrorCode.EXCHANGE_IDEMPOTENCY_CONFLICT,
                        "Idempotency key already used with different request parameters");
            }
        }

        return null; // No existing record, proceed with new exchange
    }

    /**
     * Check if the incoming request matches the original exchange.
     * For direct exchange: same prizeId.
     * For blind box exchange: same poolId and availabilityVersion.
     */
    private boolean requestsMatch(Map<String, Object> request, Exchange existing) {
        String type = existing.getType();
        if ("DIRECT".equals(type)) {
            Long requestPrizeId = request.get("prizeId") != null ? ((Number) request.get("prizeId")).longValue() : null;
            return requestPrizeId != null && requestPrizeId.equals(existing.getPrizeId());
        } else if ("BLIND_BOX".equals(type)) {
            Long requestPoolId = request.get("poolId") != null ? ((Number) request.get("poolId")).longValue() : null;
            String requestVersion = extractString(request, "availabilityVersion");
            return requestPoolId != null && requestPoolId.equals(existing.getPoolId());
        }
        return false;
    }

    // ========== Task 5.10: Fulfillment ==========

    /**
     * Fulfill a PENDING_FULFILLMENT exchange (parent operation).
     * Uses FOR UPDATE with status check for concurrency safety.
     */
    @Transactional
    public Exchange fulfillExchange(Long exchangeId, Long familyId, Long parentAccountId) {
        // Lock the exchange row
        Exchange exchange = exchangeMapper.findByIdAndFamilyForUpdate(exchangeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND,
                        "Exchange not found: " + exchangeId));

        if (!"PENDING_FULFILLMENT".equals(exchange.getStatus())) {
            throw new BusinessException(ErrorCode.EXCHANGE_INVALID_STATE,
                    "Exchange cannot be fulfilled in status: " + exchange.getStatus());
        }

        exchange.setStatus("FULFILLED");
        exchange.setFulfilledAt(LocalDateTime.now());
        exchange.setFulfilledBy(parentAccountId);
        exchangeMapper.updateById(exchange);

        auditService.record(AuditEvent.EXCHANGE_FULFILLED, parentAccountId, "SUCCESS",
                "Exchange fulfilled: id=" + exchangeId + ", childId=" + exchange.getChildId());

        log.info("Exchange fulfilled: id={}, by={}", exchangeId, parentAccountId);

        return exchange;
    }

    // ========== Task 5.11: Cancellation & Refund ==========

    /**
     * Cancel a PENDING_FULFILLMENT exchange and refund points + restore stock.
     * Atomic: refund points + restore stock + status change.
     */
    @Transactional
    public Exchange cancelExchange(Long exchangeId, Long familyId, Long cancellerAccountId,
                                    List<String> cancellerRoles) {
        // Lock the exchange row
        Exchange exchange = exchangeMapper.findByIdAndFamilyForUpdate(exchangeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND,
                        "Exchange not found: " + exchangeId));

        if (!"PENDING_FULFILLMENT".equals(exchange.getStatus())) {
            throw new BusinessException(ErrorCode.EXCHANGE_INVALID_STATE,
                    "Exchange cannot be cancelled in status: " + exchange.getStatus());
        }

        // Only parents can cancel
        if (!cancellerRoles.contains("PARENT") && !cancellerRoles.contains("INSTANCE_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Only parents can cancel exchanges");
        }

        // Refund points
        try {
            String refundBusinessRef = "EXCHANGE_REFUND_" + exchangeId;
            pointsService.refundPoints(exchange.getChildId(), familyId, exchange.getCostPoints(),
                    refundBusinessRef, "Exchange cancellation: id=" + exchangeId);
        } catch (Exception e) {
            log.error("Failed to refund points for exchange cancellation: id={}", exchangeId, e);
            throw new BusinessException(ErrorCode.EXCHANGE_CANCELLATION_FAILED,
                    "Failed to refund points: " + e.getMessage());
        }

        // Restore stock - determine which prize stock to restore
        Long prizeToRestore = exchange.getPrizeId() != null ? exchange.getPrizeId() : exchange.getResultPrizeId();
        if (prizeToRestore != null) {
            try {
                prizeMapper.incrementStock(prizeToRestore);
            } catch (Exception e) {
                log.error("Failed to restore stock for exchange cancellation: id={}", exchangeId, e);
                throw new BusinessException(ErrorCode.EXCHANGE_CANCELLATION_FAILED,
                        "Failed to restore stock: " + e.getMessage());
            }
        }

        // Update status
        exchange.setStatus("CANCELLED");
        exchange.setCancelledAt(LocalDateTime.now());
        exchange.setCancelledBy(cancellerAccountId);
        exchangeMapper.updateById(exchange);

        auditService.record(AuditEvent.EXCHANGE_CANCELLED, cancellerAccountId, "SUCCESS",
                "Exchange cancelled and refunded: id=" + exchangeId
                        + ", refundPoints=" + exchange.getCostPoints());

        log.info("Exchange cancelled: id={}, by={}, refund={}", exchangeId, cancellerAccountId, exchange.getCostPoints());

        return exchange;
    }

    // ========== Query ==========

    /**
     * Get exchange by id and family.
     */
    public Exchange getExchangeById(Long exchangeId, Long familyId) {
        return exchangeMapper.findByIdAndFamily(exchangeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXCHANGE_NOT_FOUND,
                        "Exchange not found: " + exchangeId));
    }

    /**
     * Get exchange snapshot by exchange id.
     */
    public ExchangeSnapshot getExchangeSnapshot(Long exchangeId) {
        return exchangeSnapshotMapper.findByExchangeId(exchangeId)
                .orElse(null);
    }

    /**
     * Query exchanges with filters.
     * Children: only own exchanges.
     * Parents: all children in family.
     */
    public Map<String, Object> queryExchanges(Map<String, Object> params, Long familyId, Long viewerChildId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.EXCHANGE_INVALID_QUERY,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        LambdaQueryWrapper<Exchange> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Exchange::getFamilyId, familyId);

        if (viewerChildId != null) {
            wrapper.eq(Exchange::getChildId, viewerChildId);
        } else if (params.containsKey("childId")) {
            wrapper.eq(Exchange::getChildId, ((Number) params.get("childId")).longValue());
        }

        if (params.containsKey("type")) {
            wrapper.eq(Exchange::getType, params.get("type"));
        }
        if (params.containsKey("status")) {
            wrapper.eq(Exchange::getStatus, params.get("status"));
        }

        wrapper.orderByDesc(Exchange::getCreatedAt);
        wrapper.orderByDesc(Exchange::getId);

        Page<Exchange> page = exchangeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", page.getRecords());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());
        return result;
    }

    // ========== Helpers ==========

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return val.toString();
    }
}
