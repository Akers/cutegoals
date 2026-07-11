package com.cutegoals.prize.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.prize.BlindBoxItem;
import com.cutegoals.common.entity.prize.BlindBoxPool;
import com.cutegoals.common.entity.prize.Prize;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.prize.mapper.BlindBoxItemMapper;
import com.cutegoals.prize.mapper.BlindBoxPoolMapper;
import com.cutegoals.prize.mapper.PrizeMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service for blind box pool CRUD, item management, availability version,
 * probability normalization, and weighted random draw (Phase 5, Tasks 5.3-5.6).
 */
@Service
@RequiredArgsConstructor
public class BlindBoxService {

    private static final Logger log = LoggerFactory.getLogger(BlindBoxService.class);

    private final BlindBoxPoolMapper blindBoxPoolMapper;
    private final BlindBoxItemMapper blindBoxItemMapper;
    private final PrizeMapper prizeMapper;
    private final AuditService auditService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // Random source for weighted drawing, injectable for testing
    private Supplier<Double> randomSource = () -> new SecureRandom().nextDouble();

    /**
     * Set a custom random source (for deterministic testing).
     */
    public void setRandomSource(Supplier<Double> randomSource) {
        this.randomSource = randomSource;
    }

    /**
     * Reset to default SecureRandom.
     */
    public void resetRandomSource() {
        this.randomSource = () -> new SecureRandom().nextDouble();
    }

    // ========== Task 5.3: Pool CRUD ==========

    /**
     * Create a blind box pool.
     */
    @Transactional
    public BlindBoxPool createPool(Map<String, Object> request, Long familyId, Long accountId) {
        String name = extractString(request, "name");
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Pool name is required");
        }

        Integer costPoints = extractInteger(request, "costPoints");
        if (costPoints == null || costPoints < 1) {
            throw new BusinessException(ErrorCode.BLIND_BOX_INVALID_COST,
                    "costPoints must be a positive integer, got: " + costPoints);
        }

        String description = extractString(request, "description");
        Boolean enabled = request.containsKey("enabled") ?
                Boolean.TRUE.equals(request.get("enabled")) : true;

        // Check items (must be at least one for an enabled pool)
        Boolean requestHasItems = request.containsKey("items");
        if (enabled && (!requestHasItems || getItemsFromRequest(request).isEmpty())) {
            throw new BusinessException(ErrorCode.BLIND_BOX_EMPTY_POOL,
                    "Pool must contain at least one item when enabled");
        }

        BlindBoxPool pool = new BlindBoxPool();
        pool.setFamilyId(familyId);
        pool.setName(name.trim());
        pool.setDescription(description);
        pool.setCostPoints(costPoints);
        pool.setEnabled(enabled);
        pool.setDeleted(false);

        blindBoxPoolMapper.insert(pool);

        // If items provided, add them
        if (requestHasItems) {
            addItemsToPool(pool.getId(), getItemsFromRequest(request), familyId);
        }

        // Compute availability version
        updateAvailabilityVersion(pool.getId());

        BlindBoxPool saved = blindBoxPoolMapper.findById(pool.getId()).orElse(pool);

        auditService.record(AuditEvent.BLIND_BOX_POOL_CREATED, accountId, "SUCCESS",
                "Blind box pool created: id=" + pool.getId() + ", name=" + name + ", cost=" + costPoints);

        log.info("Blind box pool created: id={}, name={}, cost={}, familyId={}",
                pool.getId(), name, costPoints, familyId);

        return saved;
    }

    /**
     * Update a blind box pool.
     */
    @Transactional
    public BlindBoxPool updatePool(Long poolId, Map<String, Object> request, Long familyId, Long accountId) {
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamily(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));

        if (Boolean.TRUE.equals(pool.getDeleted())) {
            throw new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                    "Blind box pool has been deleted: " + poolId);
        }

        if (request.containsKey("name")) {
            String name = extractString(request, "name");
            if (name == null || name.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Pool name is required");
            }
            pool.setName(name.trim());
        }

        if (request.containsKey("description")) {
            pool.setDescription(extractString(request, "description"));
        }

        if (request.containsKey("costPoints")) {
            Integer costPoints = extractInteger(request, "costPoints");
            if (costPoints == null || costPoints < 1) {
                throw new BusinessException(ErrorCode.BLIND_BOX_INVALID_COST,
                        "costPoints must be a positive integer, got: " + costPoints);
            }
            pool.setCostPoints(costPoints);
        }

        if (request.containsKey("enabled")) {
            boolean enabled = Boolean.TRUE.equals(request.get("enabled"));
            if (enabled) {
                // Check pool has at least one item
                int itemCount = blindBoxItemMapper.countByPoolId(poolId);
                if (itemCount == 0) {
                    throw new BusinessException(ErrorCode.BLIND_BOX_EMPTY_POOL,
                            "Cannot enable pool with no items");
                }
            }
            pool.setEnabled(enabled);
        }

        // If items provided, replace all items
        if (request.containsKey("items")) {
            List<Map<String, Object>> items = getItemsFromRequest(request);
            if (pool.getEnabled() && items.isEmpty()) {
                throw new BusinessException(ErrorCode.BLIND_BOX_EMPTY_POOL,
                        "Pool must contain at least one item when enabled");
            }
            replacePoolItems(poolId, items, familyId);
        }

        blindBoxPoolMapper.updateById(pool);

        // Recompute availability version
        updateAvailabilityVersion(poolId);

        BlindBoxPool saved = blindBoxPoolMapper.findById(poolId).orElse(pool);

        auditService.record(AuditEvent.BLIND_BOX_POOL_UPDATED, accountId, "SUCCESS",
                "Blind box pool updated: id=" + poolId + ", name=" + saved.getName());

        log.info("Blind box pool updated: id={}, familyId={}", poolId, familyId);

        return saved;
    }

    /**
     * Soft delete a blind box pool.
     */
    @Transactional
    public void deletePool(Long poolId, Long familyId, Long accountId) {
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamily(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));

        if (Boolean.TRUE.equals(pool.getDeleted())) {
            throw new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                    "Pool already deleted: " + poolId);
        }

        pool.setEnabled(false);
        pool.setDeleted(true);
        pool.setDeletedAt(LocalDateTime.now());
        blindBoxPoolMapper.updateById(pool);

        auditService.record(AuditEvent.BLIND_BOX_POOL_DELETED, accountId, "SUCCESS",
                "Blind box pool deleted: id=" + poolId + ", name=" + pool.getName());

        log.info("Blind box pool deleted: id={}, name={}", poolId, pool.getName());
    }

    /**
     * Query pools with pagination (parent endpoint).
     */
    public Map<String, Object> queryPools(Map<String, Object> params, Long familyId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        LambdaQueryWrapper<BlindBoxPool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlindBoxPool::getFamilyId, familyId);

        if (params.containsKey("enabled")) {
            wrapper.eq(BlindBoxPool::getEnabled, Boolean.TRUE.equals(params.get("enabled")));
        }
        if (params.containsKey("deleted")) {
            wrapper.eq(BlindBoxPool::getDeleted, Boolean.TRUE.equals(params.get("deleted")));
        } else {
            wrapper.eq(BlindBoxPool::getDeleted, false);
        }

        wrapper.orderByDesc(BlindBoxPool::getCreatedAt);
        wrapper.orderByDesc(BlindBoxPool::getId);

        Page<BlindBoxPool> page = blindBoxPoolMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", page.getRecords());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());
        return result;
    }

    /**
     * Query pools visible to children.
     * Only pools that are enabled, not deleted, and have at least one available item.
     */
    public Map<String, Object> queryAvailablePools(Map<String, Object> params, Long familyId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        LambdaQueryWrapper<BlindBoxPool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlindBoxPool::getFamilyId, familyId);
        wrapper.eq(BlindBoxPool::getEnabled, true);
        wrapper.eq(BlindBoxPool::getDeleted, false);
        wrapper.isNotNull(BlindBoxPool::getAvailabilityVersion);
        wrapper.orderByDesc(BlindBoxPool::getCreatedAt);
        wrapper.orderByDesc(BlindBoxPool::getId);

        Page<BlindBoxPool> page = blindBoxPoolMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // Filter out pools with no available items
        List<BlindBoxPool> poolsWithItems = new ArrayList<>();
        for (BlindBoxPool pool : page.getRecords()) {
            List<Map<String, Object>> candidates = getEffectiveCandidates(pool.getId(), familyId);
            if (!candidates.isEmpty()) {
                poolsWithItems.add(pool);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", poolsWithItems);
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", (long) poolsWithItems.size());
        result.put("totalPages", 1L);
        return result;
    }

    /**
     * Get a pool by id (parent view).
     */
    public BlindBoxPool getPoolById(Long poolId, Long familyId) {
        return blindBoxPoolMapper.findByIdAndFamily(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));
    }

    // ========== Task 5.4: Item Management ==========

    /**
     * Add an item to a pool.
     */
    @Transactional
    public BlindBoxItem addItem(Long poolId, Long prizeId, Integer weight, Long familyId, Long accountId) {
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamily(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));

        if (Boolean.TRUE.equals(pool.getDeleted())) {
            throw new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                    "Pool has been deleted: " + poolId);
        }

        // Validate weight
        if (weight == null || weight < 1) {
            throw new BusinessException(ErrorCode.BLIND_BOX_INVALID_WEIGHT,
                    "Weight must be a positive integer, got: " + weight);
        }

        // Validate prize belongs to family and exists
        Prize prize = prizeMapper.findByIdAndFamily(prizeId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize not found: " + prizeId));

        if (Boolean.TRUE.equals(prize.getDeleted())) {
            throw new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                    "Prize has been deleted: " + prizeId);
        }

        // Check for duplicate prize in pool
        Optional<BlindBoxItem> existing = blindBoxItemMapper.findByPoolAndPrize(poolId, prizeId);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.BLIND_BOX_DUPLICATE_PRIZE,
                    "Prize already exists in pool: " + prizeId);
        }

        BlindBoxItem item = new BlindBoxItem();
        item.setPoolId(poolId);
        item.setPrizeId(prizeId);
        item.setWeight(weight);
        blindBoxItemMapper.insert(item);

        // Recompute availability version
        updateAvailabilityVersion(poolId);

        auditService.record(AuditEvent.BLIND_BOX_ITEM_ADDED, accountId, "SUCCESS",
                "Item added: poolId=" + poolId + ", prizeId=" + prizeId + ", weight=" + weight);

        log.info("Blind box item added: poolId={}, prizeId={}, weight={}", poolId, prizeId, weight);

        return item;
    }

    /**
     * Remove an item from a pool.
     */
    @Transactional
    public void removeItem(Long itemId, Long poolId, Long familyId, Long accountId) {
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamily(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));

        // Find the item
        List<BlindBoxItem> items = blindBoxItemMapper.findByPoolId(poolId);
        BlindBoxItem target = items.stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Item not found in pool: " + itemId));

        blindBoxItemMapper.deleteById(itemId);

        // If pool is enabled and has no items left, auto-disable
        if (Boolean.TRUE.equals(pool.getEnabled()) && blindBoxItemMapper.countByPoolId(poolId) == 0) {
            pool.setEnabled(false);
            blindBoxPoolMapper.updateById(pool);
        }

        // Recompute availability version
        updateAvailabilityVersion(poolId);

        auditService.record(AuditEvent.BLIND_BOX_ITEM_REMOVED, accountId, "SUCCESS",
                "Item removed: poolId=" + poolId + ", itemId=" + itemId);

        log.info("Blind box item removed: poolId={}, itemId={}", poolId, itemId);
    }

    /**
     * Get items for a pool.
     */
    public List<BlindBoxItem> getPoolItems(Long poolId, Long familyId) {
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamily(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));
        return blindBoxItemMapper.findByPoolId(poolId);
    }

    // ========== Task 5.5: Effective Candidates & Availability Version ==========

    /**
     * Get the current effective candidates (items that pass all filters).
     * Each entry contains: prizeId, prizeName, weight, probability.
     */
    public List<Map<String, Object>> getEffectiveCandidates(Long poolId, Long familyId) {
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamily(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));

        List<BlindBoxItem> items = blindBoxItemMapper.findByPoolId(poolId);
        List<Map<String, Object>> candidates = new ArrayList<>();

        for (BlindBoxItem item : items) {
            if (item.getWeight() == null || item.getWeight() < 1) continue;

            Optional<Prize> prizeOpt = prizeMapper.findById(item.getPrizeId());
            if (prizeOpt.isEmpty()) continue;
            Prize prize = prizeOpt.get();

            // Filter: prize must be enabled, not deleted, stock > 0
            if (Boolean.FALSE.equals(prize.getEnabled())) continue;
            if (Boolean.TRUE.equals(prize.getDeleted())) continue;
            if (prize.getStock() == null || prize.getStock() <= 0) continue;

            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("prizeId", prize.getId());
            candidate.put("prizeName", prize.getName());
            candidate.put("prizeImage", prize.getImage());
            candidate.put("weight", item.getWeight());
            candidates.add(candidate);
        }

        // Calculate probabilities
        int totalWeight = candidates.stream()
                .mapToInt(c -> (Integer) c.get("weight"))
                .sum();

        for (Map<String, Object> candidate : candidates) {
            int weight = (Integer) candidate.get("weight");
            double probability = totalWeight > 0 ? (double) weight / totalWeight : 0.0;
            candidate.put("probability", Math.round(probability * 10000.0) / 100.0); // percentage with 2 decimals
        }

        return candidates;
    }

    /**
     * Compute the availability version (SHA-256 hash) for a pool's current effective state.
     */
    public String computeAvailabilityVersion(Long poolId, Long familyId) {
        List<Map<String, Object>> candidates = getEffectiveCandidates(poolId, familyId);
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamily(poolId, familyId).orElse(null);
        if (pool == null) return null;

        // Build a deterministic string from sorted candidates
        StringBuilder sb = new StringBuilder();
        sb.append("poolId=").append(poolId).append("|");
        sb.append("costPoints=").append(pool.getCostPoints()).append("|");

        // Sort candidates by prizeId for deterministic ordering
        candidates.sort(Comparator.comparing(c -> ((Long) c.get("prizeId"))));

        for (Map<String, Object> c : candidates) {
            sb.append("prizeId=").append(c.get("prizeId")).append(",");
            sb.append("weight=").append(c.get("weight")).append(";");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Get candidate probabilities with availability version for children.
     */
    public Map<String, Object> getCandidateProbabilities(Long poolId, Long familyId) {
        BlindBoxPool pool = blindBoxPoolMapper.findByIdAndFamily(poolId, familyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLIND_BOX_NOT_FOUND,
                        "Blind box pool not found: " + poolId));

        List<Map<String, Object>> candidates = getEffectiveCandidates(poolId, familyId);
        String version = computeAvailabilityVersion(poolId, familyId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("poolId", poolId);
        result.put("poolName", pool.getName());
        result.put("costPoints", pool.getCostPoints());
        result.put("availabilityVersion", version);
        result.put("candidates", candidates);
        return result;
    }

    /**
     * Verify that a pool has at least one effective candidate.
     */
    public boolean hasEffectiveCandidates(Long poolId, Long familyId) {
        return !getEffectiveCandidates(poolId, familyId).isEmpty();
    }

    // ========== Task 5.6: Weighted Random Draw ==========

    /**
     * Perform a weighted random draw from the pool's effective candidates.
     * Returns the selected prize ID, or throws BLIND_BOX_UNAVAILABLE if no candidates.
     * <p>
     * This method uses the injected random source, allowing deterministic testing.
     */
    public Map<String, Object> drawPrize(Long poolId, Long familyId) {
        List<Map<String, Object>> candidates = getEffectiveCandidates(poolId, familyId);

        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.BLIND_BOX_UNAVAILABLE,
                    "No available items in pool: " + poolId);
        }

        if (candidates.size() == 1) {
            // Only one candidate - short-circuit
            return candidates.get(0);
        }

        // Calculate total weight
        int totalWeight = candidates.stream()
                .mapToInt(c -> (Integer) c.get("weight"))
                .sum();

        // Weighted random selection
        double randomValue = randomSource.get() * totalWeight; // 0 <= value < totalWeight
        double cumulativeWeight = 0.0;

        for (Map<String, Object> candidate : candidates) {
            cumulativeWeight += ((Integer) candidate.get("weight")).doubleValue();
            if (randomValue < cumulativeWeight) {
                return candidate;
            }
        }

        // Fallback (should not reach here with valid weights)
        return candidates.get(candidates.size() - 1);
    }

    // ========== Internal Helpers ==========

    private void updateAvailabilityVersion(Long poolId) {
        BlindBoxPool pool = blindBoxPoolMapper.findById(poolId).orElse(null);
        if (pool == null) return;

        String version = computeAvailabilityVersion(poolId, pool.getFamilyId());
        pool.setAvailabilityVersion(version);
        blindBoxPoolMapper.updateById(pool);
    }

    private void addItemsToPool(Long poolId, List<Map<String, Object>> items, Long familyId) {
        for (Map<String, Object> item : items) {
            Long prizeId = item.get("prizeId") != null ? ((Number) item.get("prizeId")).longValue() : null;
            Integer weight = item.get("weight") != null ? ((Number) item.get("weight")).intValue() : null;

            if (prizeId == null) continue;
            if (weight == null || weight < 1) {
                throw new BusinessException(ErrorCode.BLIND_BOX_INVALID_WEIGHT,
                        "Weight must be a positive integer, got: " + weight);
            }

            // Validate prize
            Prize prize = prizeMapper.findByIdAndFamily(prizeId, familyId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                            "Prize not found: " + prizeId));

            if (Boolean.TRUE.equals(prize.getDeleted())) {
                throw new BusinessException(ErrorCode.PRIZE_NOT_FOUND,
                        "Prize has been deleted: " + prizeId);
            }

            // Check duplicate
            Optional<BlindBoxItem> existing = blindBoxItemMapper.findByPoolAndPrize(poolId, prizeId);
            if (existing.isPresent()) {
                throw new BusinessException(ErrorCode.BLIND_BOX_DUPLICATE_PRIZE,
                        "Duplicate prize in pool: " + prizeId);
            }

            BlindBoxItem newItem = new BlindBoxItem();
            newItem.setPoolId(poolId);
            newItem.setPrizeId(prizeId);
            newItem.setWeight(weight);
            blindBoxItemMapper.insert(newItem);
        }
    }

    private void replacePoolItems(Long poolId, List<Map<String, Object>> items, Long familyId) {
        // Delete all existing items
        blindBoxItemMapper.deleteByPoolId(poolId);
        // Add new items
        addItemsToPool(poolId, items, familyId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getItemsFromRequest(Map<String, Object> request) {
        Object itemsObj = request.get("items");
        if (itemsObj instanceof List) {
            return (List<Map<String, Object>>) itemsObj;
        }
        return List.of();
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
}
