package com.cutegoals.prize.service;

import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.prize.Prize;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.prize.mapper.PrizeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class PrizeServiceTest {

    @Mock private PrizeMapper prizeMapper;
    @Mock private AuditService auditService;
    @InjectMocks private PrizeService prizeService;

    private final Long familyId = 1L;
    private final Long accountId = 100L;

    // ========== Task 5.1: Prize CRUD ==========

    @Test
    void shouldCreatePrize() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Test Prize");
        request.put("pointsCost", 50);
        request.put("stock", 10);
        request.put("description", "A test prize");
        request.put("image", "test.jpg");

        when(prizeMapper.insert(any(Prize.class))).thenAnswer(invocation -> {
            Prize p = invocation.getArgument(0);
            p.setId(1L);
            return 1;
        });

        Prize result = prizeService.createPrize(request, familyId, accountId);

        assertNotNull(result);
        assertEquals("Test Prize", result.getName());
        assertEquals(50, result.getPointsCost());
        assertEquals(10, result.getStock());
        assertTrue(result.getEnabled());
        assertFalse(result.getDeleted());
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowWhenNameIsEmpty() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "");
        request.put("pointsCost", 50);
        request.put("stock", 10);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> prizeService.createPrize(request, familyId, accountId));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenNameIsNull() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("pointsCost", 50);
        request.put("stock", 10);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> prizeService.createPrize(request, familyId, accountId));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenPointsCostIsZero() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Test Prize");
        request.put("pointsCost", 0);
        request.put("stock", 10);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> prizeService.createPrize(request, familyId, accountId));
        assertEquals(ErrorCode.PRIZE_INVALID_POINTS_COST, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenPointsCostIsNull() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Test Prize");
        request.put("stock", 10);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> prizeService.createPrize(request, familyId, accountId));
        assertEquals(ErrorCode.PRIZE_INVALID_POINTS_COST, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenStockIsNegative() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Test Prize");
        request.put("pointsCost", 50);
        request.put("stock", -1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> prizeService.createPrize(request, familyId, accountId));
        assertEquals(ErrorCode.PRIZE_INVALID_STOCK, ex.getErrorCode());
    }

    // ========== Task 5.2: Stock Adjustment & Soft Delete ==========

    @Test
    void shouldUpdatePrize() {
        Prize existing = new Prize();
        existing.setId(1L);
        existing.setFamilyId(familyId);
        existing.setName("Original");
        existing.setPointsCost(30);
        existing.setStock(5);
        existing.setEnabled(true);
        existing.setDeleted(false);

        when(prizeMapper.findByIdAndFamily(1L, familyId)).thenReturn(Optional.of(existing));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Updated Prize");
        request.put("pointsCost", 60);
        request.put("stock", 20);

        Prize result = prizeService.updatePrize(1L, request, familyId, accountId);

        assertEquals("Updated Prize", result.getName());
        assertEquals(60, result.getPointsCost());
        assertEquals(20, result.getStock());
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowOnUpdateWhenNameIsEmpty() {
        Prize existing = new Prize();
        existing.setId(1L);
        existing.setFamilyId(familyId);
        existing.setName("Original");
        existing.setDeleted(false);

        when(prizeMapper.findByIdAndFamily(1L, familyId)).thenReturn(Optional.of(existing));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> prizeService.updatePrize(1L, request, familyId, accountId));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void shouldSoftDeletePrize() {
        Prize existing = new Prize();
        existing.setId(1L);
        existing.setFamilyId(familyId);
        existing.setName("Delete Me");
        existing.setDeleted(false);

        when(prizeMapper.findByIdAndFamily(1L, familyId)).thenReturn(Optional.of(existing));
        when(prizeMapper.countExchangesByPrizeId(1L)).thenReturn(0);

        prizeService.deletePrize(1L, familyId, accountId);

        ArgumentCaptor<Prize> captor = ArgumentCaptor.forClass(Prize.class);
        verify(prizeMapper).updateById(captor.capture());
        Prize captured = captor.getValue();
        assertTrue(captured.getDeleted());
        assertFalse(captured.getEnabled());
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowOnDeleteAlreadyDeleted() {
        Prize existing = new Prize();
        existing.setId(1L);
        existing.setFamilyId(familyId);
        existing.setDeleted(true);

        when(prizeMapper.findByIdAndFamily(1L, familyId)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class,
                () -> prizeService.deletePrize(1L, familyId, accountId));
    }

    @Test
    void shouldAdjustStock() {
        Prize existing = new Prize();
        existing.setId(1L);
        existing.setFamilyId(familyId);
        existing.setName("Test");
        existing.setStock(5);
        existing.setDeleted(false);

        when(prizeMapper.findByIdAndFamily(1L, familyId)).thenReturn(Optional.of(existing));

        Prize result = prizeService.adjustStock(1L, 100, familyId, accountId);

        assertEquals(100, result.getStock());
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowWhenAdjustStockToNegative() {
        assertThrows(BusinessException.class,
                () -> prizeService.adjustStock(1L, -1, familyId, accountId));
    }

    // ========== Child view filtering ==========

    @Test
    void shouldGetAvailablePrizeForChild() {
        Prize available = new Prize();
        available.setId(1L);
        available.setName("Available Prize");
        available.setStock(5);

        when(prizeMapper.findAvailableByIdAndFamily(1L, familyId)).thenReturn(Optional.of(available));

        Prize result = prizeService.getAvailablePrizeById(1L, familyId);

        assertEquals("Available Prize", result.getName());
    }

    @Test
    void shouldThrowWhenPrizeNotAvailableForChild() {
        when(prizeMapper.findAvailableByIdAndFamily(1L, familyId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> prizeService.getAvailablePrizeById(1L, familyId));
    }
}
