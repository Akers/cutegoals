package com.cutegoals.exchange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock private ExchangeMapper exchangeMapper;
    @Mock private ExchangeSnapshotMapper exchangeSnapshotMapper;
    @Mock private PrizeMapper prizeMapper;
    @Mock private PrizeService prizeService;
    @Mock private BlindBoxPoolMapper blindBoxPoolMapper;
    @Mock private BlindBoxService blindBoxService;
    @Mock private PointsService pointsService;
    @Mock private PointsBalanceMapper pointsBalanceMapper;
    @Mock private PointsLedgerMapper pointsLedgerMapper;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;
    @InjectMocks private ExchangeService exchangeService;

    private final Long familyId = 1L;
    private final Long childId = 10L;
    private final Long parentAccountId = 100L;

    // ========== Task 5.7: Direct Exchange ==========

    @Test
    void shouldCreateDirectExchange() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("prizeId", 200L);
        request.put("idempotencyKey", "direct-key-1");

        Prize prize = createPrize(200L, "Test Prize", 50, 10);

        when(prizeMapper.findByIdAndFamilyForUpdate(200L, familyId)).thenReturn(Optional.of(prize));
        when(prizeMapper.decrementStock(200L)).thenReturn(1);

        when(exchangeMapper.findByChildIdAndKey(childId, "direct-key-1")).thenReturn(Optional.empty());

        when(exchangeMapper.insert(any(Exchange.class))).thenAnswer(invocation -> {
            Exchange e = invocation.getArgument(0);
            e.setId(1L);
            return 1;
        });
        when(exchangeSnapshotMapper.insert(any(ExchangeSnapshot.class))).thenReturn(1);

        Exchange result = exchangeService.createDirectExchange(request, childId, familyId);

        assertNotNull(result);
        assertEquals("DIRECT", result.getType());
        assertEquals("PENDING_FULFILLMENT", result.getStatus());
        assertEquals(childId, result.getChildId());
        assertEquals(50, result.getCostPoints());
        assertEquals("direct-key-1", result.getIdempotencyKey());
        verify(pointsService).spendPoints(eq(childId), eq(familyId), eq(50),
                eq("EXCHANGE_SPEND_1"), anyString());
    }

    @Test
    void shouldThrowWhenPrizeNotFoundForDirectExchange() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("prizeId", 999L);
        request.put("idempotencyKey", "direct-key-2");

        when(exchangeMapper.findByChildIdAndKey(childId, "direct-key-2")).thenReturn(Optional.empty());
        when(prizeMapper.findByIdAndFamilyForUpdate(999L, familyId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> exchangeService.createDirectExchange(request, childId, familyId));
    }

    @Test
    void shouldThrowWhenPrizeOutOfStock() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("prizeId", 200L);
        request.put("idempotencyKey", "direct-key-3");

        Prize prize = createPrize(200L, "Out Of Stock", 50, 0);

        when(exchangeMapper.findByChildIdAndKey(childId, "direct-key-3")).thenReturn(Optional.empty());
        when(prizeMapper.findByIdAndFamilyForUpdate(200L, familyId)).thenReturn(Optional.of(prize));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exchangeService.createDirectExchange(request, childId, familyId));
        assertEquals(ErrorCode.PRIZE_OUT_OF_STOCK, ex.getErrorCode());
    }

    // ========== Task 5.8: Blind Box Exchange ==========

    @Test
    void shouldCreateBlindBoxExchange() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("poolId", 300L);
        request.put("idempotencyKey", "blind-key-1");
        request.put("availabilityVersion", "abc123");

        BlindBoxPool pool = createPool(300L, "Test Pool", 100);

        when(exchangeMapper.findByChildIdAndKey(childId, "blind-key-1")).thenReturn(Optional.empty());
        when(blindBoxPoolMapper.findByIdAndFamilyForUpdate(300L, familyId)).thenReturn(Optional.of(pool));
        when(blindBoxService.computeAvailabilityVersion(300L, familyId)).thenReturn("abc123");

        Map<String, Object> drawnCandidate = new LinkedHashMap<>();
        drawnCandidate.put("prizeId", 200L);
        drawnCandidate.put("prizeName", "Drawn Prize");
        drawnCandidate.put("prizeImage", "img.jpg");
        drawnCandidate.put("weight", 5);
        drawnCandidate.put("probability", 100.0);
        when(blindBoxService.drawPrize(300L, familyId)).thenReturn(drawnCandidate);

        Prize drawnPrize = createPrize(200L, "Drawn Prize", 100, 10);
        when(prizeMapper.findByIdAndFamilyForUpdate(200L, familyId)).thenReturn(Optional.of(drawnPrize));
        when(prizeMapper.decrementStock(200L)).thenReturn(1);

        when(exchangeMapper.insert(any(Exchange.class))).thenAnswer(invocation -> {
            Exchange e = invocation.getArgument(0);
            e.setId(2L);
            return 1;
        });

        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(drawnCandidate);
        when(blindBoxService.getEffectiveCandidates(300L, familyId)).thenReturn(candidates);

        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("[{\"test\":\"value\"}]");
        } catch (Exception e) {
            fail("Mock setup failed");
        }

        when(exchangeSnapshotMapper.insert(any(ExchangeSnapshot.class))).thenReturn(1);

        Exchange result = exchangeService.createBlindBoxExchange(request, childId, familyId);

        assertNotNull(result);
        assertEquals("BLIND_BOX", result.getType());
        assertEquals(300L, result.getPoolId());
        assertEquals(200L, result.getResultPrizeId());
        verify(pointsService).spendPoints(eq(childId), eq(familyId), eq(100),
                eq("EXCHANGE_SPEND_2"), anyString());
    }

    @Test
    void shouldThrowWhenBlindBoxVersionMismatch() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("poolId", 300L);
        request.put("idempotencyKey", "blind-key-2");
        request.put("availabilityVersion", "old-version");

        BlindBoxPool pool = createPool(300L, "Test Pool", 100);

        when(exchangeMapper.findByChildIdAndKey(childId, "blind-key-2")).thenReturn(Optional.empty());
        when(blindBoxPoolMapper.findByIdAndFamilyForUpdate(300L, familyId)).thenReturn(Optional.of(pool));
        when(blindBoxService.computeAvailabilityVersion(300L, familyId)).thenReturn("new-version");

        Map<String, Object> candidatesData = new LinkedHashMap<>();
        candidatesData.put("poolId", 300L);
        candidatesData.put("availabilityVersion", "new-version");
        when(blindBoxService.getCandidateProbabilities(300L, familyId)).thenReturn(candidatesData);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exchangeService.createBlindBoxExchange(request, childId, familyId));
        assertEquals(ErrorCode.BLIND_BOX_POOL_CHANGED, ex.getErrorCode());
        assertNotNull(ex.getData());
        assertEquals("new-version", ex.getData().get("availabilityVersion"));
    }

    // ========== I-NEW-2: Concurrent stockout with candidate data ==========

    @Test
    void shouldThrowWithDataWhenBlindBoxDrawnPrizeOutOfStock() {
        // Path: drawn prize stock is 0 before decrement
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("poolId", 300L);
        request.put("idempotencyKey", "blind-stock-key-1");
        request.put("availabilityVersion", "abc123");

        BlindBoxPool pool = createPool(300L, "Test Pool", 100);

        when(exchangeMapper.findByChildIdAndKey(childId, "blind-stock-key-1")).thenReturn(Optional.empty());
        when(blindBoxPoolMapper.findByIdAndFamilyForUpdate(300L, familyId)).thenReturn(Optional.of(pool));
        when(blindBoxService.computeAvailabilityVersion(300L, familyId)).thenReturn("abc123");

        Map<String, Object> drawnCandidate = new LinkedHashMap<>();
        drawnCandidate.put("prizeId", 200L);
        drawnCandidate.put("prizeName", "Drawn Prize");
        drawnCandidate.put("prizeImage", "img.jpg");
        drawnCandidate.put("weight", 5);
        drawnCandidate.put("probability", 100.0);
        when(blindBoxService.drawPrize(300L, familyId)).thenReturn(drawnCandidate);

        // Drawn prize has 0 stock
        Prize drawnPrize = createPrize(200L, "Drawn Prize", 100, 0);
        when(prizeMapper.findByIdAndFamilyForUpdate(200L, familyId)).thenReturn(Optional.of(drawnPrize));

        Map<String, Object> candidatesData = new LinkedHashMap<>();
        candidatesData.put("poolId", 300L);
        candidatesData.put("availabilityVersion", "def456");
        when(blindBoxService.getCandidateProbabilities(300L, familyId)).thenReturn(candidatesData);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exchangeService.createBlindBoxExchange(request, childId, familyId));
        assertEquals(ErrorCode.BLIND_BOX_POOL_CHANGED, ex.getErrorCode());
        assertNotNull(ex.getData());
        assertEquals("def456", ex.getData().get("availabilityVersion"));
    }

    @Test
    void shouldThrowWithDataWhenBlindBoxConcurrentStockDecrementFails() {
        // Path: stock > 0 but concurrent decrement fails
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("poolId", 300L);
        request.put("idempotencyKey", "blind-stock-key-2");
        request.put("availabilityVersion", "abc123");

        BlindBoxPool pool = createPool(300L, "Test Pool", 100);

        when(exchangeMapper.findByChildIdAndKey(childId, "blind-stock-key-2")).thenReturn(Optional.empty());
        when(blindBoxPoolMapper.findByIdAndFamilyForUpdate(300L, familyId)).thenReturn(Optional.of(pool));
        when(blindBoxService.computeAvailabilityVersion(300L, familyId)).thenReturn("abc123");

        Map<String, Object> drawnCandidate = new LinkedHashMap<>();
        drawnCandidate.put("prizeId", 200L);
        drawnCandidate.put("prizeName", "Drawn Prize");
        drawnCandidate.put("prizeImage", "img.jpg");
        drawnCandidate.put("weight", 5);
        drawnCandidate.put("probability", 100.0);
        when(blindBoxService.drawPrize(300L, familyId)).thenReturn(drawnCandidate);

        // Drawn prize has stock but concurrent decrement fails (returns 0)
        Prize drawnPrize = createPrize(200L, "Drawn Prize", 100, 10);
        when(prizeMapper.findByIdAndFamilyForUpdate(200L, familyId)).thenReturn(Optional.of(drawnPrize));

        when(exchangeMapper.insert(any(Exchange.class))).thenAnswer(invocation -> {
            Exchange e = invocation.getArgument(0);
            e.setId(4L);
            return 1;
        });

        when(prizeMapper.decrementStock(200L)).thenReturn(0);

        Map<String, Object> candidatesData = new LinkedHashMap<>();
        candidatesData.put("poolId", 300L);
        candidatesData.put("availabilityVersion", "def456");
        when(blindBoxService.getCandidateProbabilities(300L, familyId)).thenReturn(candidatesData);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exchangeService.createBlindBoxExchange(request, childId, familyId));
        assertEquals(ErrorCode.BLIND_BOX_POOL_CHANGED, ex.getErrorCode());
        assertNotNull(ex.getData());
        assertEquals("def456", ex.getData().get("availabilityVersion"));

        // Points were deducted before stock failure (should not roll back in service layer)
        verify(pointsService).spendPoints(any(), any(), anyInt(), anyString(), anyString());
    }

    // ========== I-NEW-1: Blind box idempotency with availabilityVersion mismatch ==========

    @Test
    void shouldThrowWhenBlindBoxIdempotencyVersionMismatch() {
        // Same idempotency key, same poolId, but different availabilityVersion
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("poolId", 300L);
        request.put("idempotencyKey", "blind-idem-key");
        request.put("availabilityVersion", "v2");

        Exchange existing = new Exchange();
        existing.setId(5L);
        existing.setChildId(childId);
        existing.setFamilyId(familyId);
        existing.setType("BLIND_BOX");
        existing.setPoolId(300L);
        existing.setCreatedAt(LocalDateTime.now());

        ExchangeSnapshot snapshot = new ExchangeSnapshot();
        snapshot.setExchangeId(5L);
        snapshot.setAvailabilityVersion("v1"); // Different from request's "v2"

        when(exchangeMapper.findByChildIdAndKey(childId, "blind-idem-key")).thenReturn(Optional.of(existing));
        when(exchangeSnapshotMapper.findByExchangeId(5L)).thenReturn(Optional.of(snapshot));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exchangeService.createBlindBoxExchange(request, childId, familyId));
        assertEquals(ErrorCode.EXCHANGE_IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    @Test
    void shouldReturnExistingOnBlindBoxIdempotencyVersionMatch() {
        // Same idempotency key, same poolId, same availabilityVersion → idempotent return
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("poolId", 300L);
        request.put("idempotencyKey", "blind-idem-key-2");
        request.put("availabilityVersion", "v1");

        Exchange existing = new Exchange();
        existing.setId(6L);
        existing.setChildId(childId);
        existing.setFamilyId(familyId);
        existing.setType("BLIND_BOX");
        existing.setPoolId(300L);
        existing.setCreatedAt(LocalDateTime.now());

        ExchangeSnapshot snapshot = new ExchangeSnapshot();
        snapshot.setExchangeId(6L);
        snapshot.setAvailabilityVersion("v1"); // Same as request

        when(exchangeMapper.findByChildIdAndKey(childId, "blind-idem-key-2")).thenReturn(Optional.of(existing));
        when(exchangeSnapshotMapper.findByExchangeId(6L)).thenReturn(Optional.of(snapshot));

        Exchange result = exchangeService.createBlindBoxExchange(request, childId, familyId);
        assertNotNull(result);
        assertEquals(6L, result.getId());
    }

    // ========== Task 5.9: Idempotency ==========

    @Test
    void shouldReturnExistingExchangeOnIdempotentRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("prizeId", 200L);
        request.put("idempotencyKey", "same-key");

        Exchange existing = new Exchange();
        existing.setId(1L);
        existing.setChildId(childId);
        existing.setFamilyId(familyId);
        existing.setType("DIRECT");
        existing.setStatus("PENDING_FULFILLMENT");
        existing.setPrizeId(200L);
        existing.setCreatedAt(LocalDateTime.now());

        when(exchangeMapper.findByChildIdAndKey(childId, "same-key")).thenReturn(Optional.of(existing));

        Exchange result = exchangeService.createDirectExchange(request, childId, familyId);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        // Should NOT call spendPoints or decrementStock
        verify(pointsService, never()).spendPoints(any(), any(), anyInt(), anyString(), anyString());
    }

    @Test
    void shouldThrowWhenIdempotencyKeyUsedWithDifferentRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("prizeId", 201L); // Different prize
        request.put("idempotencyKey", "same-key");

        Exchange existing = new Exchange();
        existing.setId(1L);
        existing.setChildId(childId);
        existing.setType("DIRECT");
        existing.setPrizeId(200L); // Original prize
        existing.setCreatedAt(LocalDateTime.now());

        when(exchangeMapper.findByChildIdAndKey(childId, "same-key")).thenReturn(Optional.of(existing));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exchangeService.createDirectExchange(request, childId, familyId));
        assertEquals(ErrorCode.EXCHANGE_IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }

    @Test
    void shouldIgnoreExpiredIdempotencyKey() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("prizeId", 200L);
        request.put("idempotencyKey", "expired-key");

        // Created 25 hours ago - expired
        Exchange existing = new Exchange();
        existing.setId(1L);
        existing.setChildId(childId);
        existing.setType("DIRECT");
        existing.setPrizeId(200L);
        existing.setCreatedAt(LocalDateTime.now().minusHours(25));

        when(exchangeMapper.findByChildIdAndKey(childId, "expired-key")).thenReturn(Optional.of(existing));

        // Since expired, should proceed with new exchange
        Prize prize = createPrize(200L, "Fresh Prize", 50, 10);
        when(prizeMapper.findByIdAndFamilyForUpdate(200L, familyId)).thenReturn(Optional.of(prize));
        when(prizeMapper.decrementStock(200L)).thenReturn(1);

        when(exchangeMapper.insert(any(Exchange.class))).thenAnswer(invocation -> {
            Exchange e = invocation.getArgument(0);
            e.setId(3L);
            return 1;
        });
        when(exchangeSnapshotMapper.insert(any(ExchangeSnapshot.class))).thenReturn(1);

        Exchange result = exchangeService.createDirectExchange(request, childId, familyId);
        assertNotNull(result);
        assertEquals(3L, result.getId());
        verify(pointsService).spendPoints(any(), any(), anyInt(), anyString(), anyString());
    }

    @Test
    void shouldThrowWhenIdempotencyKeyMissing() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("prizeId", 200L);
        // No idempotencyKey

        assertThrows(BusinessException.class,
                () -> exchangeService.createDirectExchange(request, childId, familyId));
    }

    // ========== Task 5.10: Fulfillment ==========

    @Test
    void shouldFulfillExchange() {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        exchange.setChildId(childId);
        exchange.setFamilyId(familyId);
        exchange.setStatus("PENDING_FULFILLMENT");

        when(exchangeMapper.findByIdAndFamilyForUpdate(1L, familyId)).thenReturn(Optional.of(exchange));

        Exchange result = exchangeService.fulfillExchange(1L, familyId, parentAccountId);

        assertEquals("FULFILLED", result.getStatus());
        assertNotNull(result.getFulfilledAt());
        assertEquals(parentAccountId, result.getFulfilledBy());
        verify(auditService).record(anyString(), eq(parentAccountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowWhenFulfillingAlreadyFulfilled() {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        exchange.setStatus("FULFILLED");

        when(exchangeMapper.findByIdAndFamilyForUpdate(1L, familyId)).thenReturn(Optional.of(exchange));

        assertThrows(BusinessException.class,
                () -> exchangeService.fulfillExchange(1L, familyId, parentAccountId));
    }

    // ========== Task 5.11: Cancellation ==========

    // ========== Task: queryExchanges targetName denormalization ==========

    @Test
    void shouldQueryExchangesWithTargetNameFromSnapshot() {
        Exchange direct = new Exchange();
        direct.setId(1L);
        direct.setChildId(childId);
        direct.setFamilyId(familyId);
        direct.setType("DIRECT");
        direct.setStatus("PENDING_FULFILLMENT");
        direct.setPrizeId(200L);
        direct.setPoolId(null);
        direct.setCostPoints(50);
        direct.setCreatedAt(LocalDateTime.now());

        Exchange blindBox = new Exchange();
        blindBox.setId(2L);
        blindBox.setChildId(childId);
        blindBox.setFamilyId(familyId);
        blindBox.setType("BLIND_BOX");
        blindBox.setStatus("PENDING_FULFILLMENT");
        blindBox.setPrizeId(null);
        blindBox.setPoolId(300L);
        blindBox.setCostPoints(100);
        blindBox.setCreatedAt(LocalDateTime.now().minusHours(1));

        Page<Exchange> page = new Page<>(1, 20);
        page.setRecords(java.util.List.of(direct, blindBox));
        page.setTotal(2);

        when(exchangeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        ExchangeSnapshot directSnap = new ExchangeSnapshot();
        directSnap.setExchangeId(1L);
        directSnap.setPrizeName("直接奖品");
        directSnap.setPointsCost(50);

        ExchangeSnapshot blindBoxSnap = new ExchangeSnapshot();
        blindBoxSnap.setExchangeId(2L);
        blindBoxSnap.setPoolName("盲盒大礼包");
        blindBoxSnap.setPointsCost(100);

        when(exchangeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.List.of(directSnap, blindBoxSnap));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("childId", childId);

        Map<String, Object> result = exchangeService.queryExchanges(params, familyId, childId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertEquals(2, content.size());

        Map<String, Object> directMap = content.get(0);
        assertEquals("DIRECT", directMap.get("type"));
        assertEquals("直接奖品", directMap.get("targetName"));

        Map<String, Object> blindBoxMap = content.get(1);
        assertEquals("BLIND_BOX", blindBoxMap.get("type"));
        assertEquals("盲盒大礼包", blindBoxMap.get("targetName"));

        // Page shape preserved
        assertEquals(1, result.get("page"));
        assertEquals(20, result.get("pageSize"));
        assertEquals(2, result.get("totalElements"));
        assertEquals(1, result.get("totalPages"));
    }

    @Test
    void shouldFallbackToIdLabelWhenSnapshotMissing() {
        Exchange direct = new Exchange();
        direct.setId(1L);
        direct.setChildId(childId);
        direct.setFamilyId(familyId);
        direct.setType("DIRECT");
        direct.setStatus("PENDING_FULFILLMENT");
        direct.setPrizeId(200L);
        direct.setPoolId(null);
        direct.setCostPoints(50);
        direct.setCreatedAt(LocalDateTime.now());

        Exchange blindBox = new Exchange();
        blindBox.setId(2L);
        blindBox.setChildId(childId);
        blindBox.setFamilyId(familyId);
        blindBox.setType("BLIND_BOX");
        blindBox.setStatus("PENDING_FULFILLMENT");
        blindBox.setPrizeId(null);
        blindBox.setPoolId(300L);
        blindBox.setCostPoints(100);
        blindBox.setCreatedAt(LocalDateTime.now().minusHours(1));

        Page<Exchange> page = new Page<>(1, 20);
        page.setRecords(java.util.List.of(direct, blindBox));
        page.setTotal(2);

        when(exchangeMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(exchangeSnapshotMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.Collections.emptyList());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("childId", childId);

        Map<String, Object> result = exchangeService.queryExchanges(params, familyId, childId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertEquals(2, content.size());
        assertEquals("奖品 #200", content.get(0).get("targetName"));
        assertEquals("盲盒 #300", content.get(1).get("targetName"));
    }

    @Test
    void shouldCancelExchange() {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        exchange.setChildId(childId);
        exchange.setFamilyId(familyId);
        exchange.setStatus("PENDING_FULFILLMENT");
        exchange.setCostPoints(50);
        exchange.setPrizeId(200L);
        exchange.setResultPrizeId(null);

        when(exchangeMapper.findByIdAndFamilyForUpdate(1L, familyId)).thenReturn(Optional.of(exchange));
        when(prizeMapper.incrementStock(200L)).thenReturn(1);

        List<String> parentRoles = List.of("PARENT");

        Exchange result = exchangeService.cancelExchange(1L, familyId, parentAccountId, parentRoles);

        assertEquals("CANCELLED", result.getStatus());
        assertNotNull(result.getCancelledAt());
        assertEquals(parentAccountId, result.getCancelledBy());
        verify(pointsService).refundPoints(eq(childId), eq(familyId), eq(50),
                eq("EXCHANGE_REFUND_1"), anyString());
    }

    @Test
    void shouldThrowWhenCancellingNonPending() {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        exchange.setStatus("FULFILLED");

        when(exchangeMapper.findByIdAndFamilyForUpdate(1L, familyId)).thenReturn(Optional.of(exchange));

        List<String> parentRoles = List.of("PARENT");

        assertThrows(BusinessException.class,
                () -> exchangeService.cancelExchange(1L, familyId, parentAccountId, parentRoles));
    }

    @Test
    void shouldThrowWhenChildCancels() {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        exchange.setStatus("PENDING_FULFILLMENT");

        when(exchangeMapper.findByIdAndFamilyForUpdate(1L, familyId)).thenReturn(Optional.of(exchange));

        List<String> childRoles = List.of("CHILD");

        assertThrows(BusinessException.class,
                () -> exchangeService.cancelExchange(1L, familyId, parentAccountId, childRoles));
    }

    @Test
    void shouldPropagateBusinessExceptionOnRefundFailure() {
        Exchange exchange = new Exchange();
        exchange.setId(1L);
        exchange.setChildId(childId);
        exchange.setFamilyId(familyId);
        exchange.setStatus("PENDING_FULFILLMENT");
        exchange.setCostPoints(50);

        when(exchangeMapper.findByIdAndFamilyForUpdate(1L, familyId)).thenReturn(Optional.of(exchange));
        doThrow(new BusinessException(ErrorCode.POINTS_REFERENCE_CONFLICT, "Already refunded"))
                .when(pointsService).refundPoints(any(), any(), anyInt(), anyString(), anyString());

        List<String> parentRoles = List.of("PARENT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> exchangeService.cancelExchange(1L, familyId, parentAccountId, parentRoles));
        // Original error code should be preserved, not wrapped in EXCHANGE_CANCELLATION_FAILED
        assertEquals(ErrorCode.POINTS_REFERENCE_CONFLICT, ex.getErrorCode());
    }

    // ========== Helpers ==========

    private Prize createPrize(Long id, String name, int pointsCost, int stock) {
        Prize prize = new Prize();
        prize.setId(id);
        prize.setName(name);
        prize.setPointsCost(pointsCost);
        prize.setStock(stock);
        prize.setEnabled(true);
        prize.setDeleted(false);
        prize.setImage(name.toLowerCase().replace(" ", "_") + ".jpg");
        prize.setDescription("Description for " + name);
        return prize;
    }

    private BlindBoxPool createPool(Long id, String name, int costPoints) {
        BlindBoxPool pool = new BlindBoxPool();
        pool.setId(id);
        pool.setFamilyId(familyId);
        pool.setName(name);
        pool.setCostPoints(costPoints);
        pool.setEnabled(true);
        pool.setDeleted(false);
        return pool;
    }
}
