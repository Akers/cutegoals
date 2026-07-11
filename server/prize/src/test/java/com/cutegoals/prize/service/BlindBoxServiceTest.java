package com.cutegoals.prize.service;

import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.prize.BlindBoxItem;
import com.cutegoals.common.entity.prize.BlindBoxPool;
import com.cutegoals.common.entity.prize.Prize;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.prize.mapper.BlindBoxItemMapper;
import com.cutegoals.prize.mapper.BlindBoxPoolMapper;
import com.cutegoals.prize.mapper.PrizeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlindBoxServiceTest {

    @Mock private BlindBoxPoolMapper blindBoxPoolMapper;
    @Mock private BlindBoxItemMapper blindBoxItemMapper;
    @Mock private PrizeMapper prizeMapper;
    @Mock private AuditService auditService;
    @InjectMocks private BlindBoxService blindBoxService;

    private final Long familyId = 1L;
    private final Long accountId = 100L;

    // ========== Task 5.3: Pool CRUD ==========

    @Test
    void shouldCreatePool() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Test Pool");
        request.put("costPoints", 100);
        request.put("description", "A test pool");
        request.put("enabled", false); // Disabled pool does not require items

        BlindBoxPool savedPool = new BlindBoxPool();
        savedPool.setId(1L);
        savedPool.setFamilyId(familyId);

        when(blindBoxPoolMapper.insert(any(BlindBoxPool.class))).thenAnswer(invocation -> {
            BlindBoxPool p = invocation.getArgument(0);
            p.setId(1L); // Set ID on the actual pool object passed to insert
            return 1;
        });
        // Mock the updateAvailabilityVersion chain
        when(blindBoxPoolMapper.findById(1L)).thenReturn(Optional.of(savedPool));
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(Optional.of(savedPool));
        when(blindBoxItemMapper.findByPoolId(1L)).thenReturn(List.of());

        BlindBoxPool result = blindBoxService.createPool(request, familyId, accountId);

        assertNotNull(result);
        verify(blindBoxPoolMapper).insert(any(BlindBoxPool.class));
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldThrowWhenPoolNameIsEmpty() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "");
        request.put("costPoints", 100);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> blindBoxService.createPool(request, familyId, accountId));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenCostIsZero() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Test Pool");
        request.put("costPoints", 0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> blindBoxService.createPool(request, familyId, accountId));
        assertEquals(ErrorCode.BLIND_BOX_INVALID_COST, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenEmptyPoolIsEnabled() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Empty Pool");
        request.put("costPoints", 100);
        request.put("enabled", true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> blindBoxService.createPool(request, familyId, accountId));
        assertEquals(ErrorCode.BLIND_BOX_EMPTY_POOL, ex.getErrorCode());
    }

    // ========== Task 5.4: Item Management ==========

    @Test
    void shouldThrowWhenWeightIsZero() {
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, false)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> blindBoxService.addItem(1L, 100L, 0, familyId, accountId));
        assertEquals(ErrorCode.BLIND_BOX_INVALID_WEIGHT, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenPrizeAlreadyInPool() {
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, true)));

        Prize prize = new Prize();
        prize.setId(100L);
        prize.setFamilyId(familyId);
        prize.setDeleted(false);
        when(prizeMapper.findByIdAndFamily(100L, familyId)).thenReturn(Optional.of(prize));

        BlindBoxItem existingItem = new BlindBoxItem();
        existingItem.setPoolId(1L);
        existingItem.setPrizeId(100L);
        when(blindBoxItemMapper.findByPoolAndPrize(1L, 100L)).thenReturn(Optional.of(existingItem));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> blindBoxService.addItem(1L, 100L, 5, familyId, accountId));
        assertEquals(ErrorCode.BLIND_BOX_DUPLICATE_PRIZE, ex.getErrorCode());
    }

    // ========== Task 5.5: Effective Candidates ==========

    @Test
    void shouldGetEffectiveCandidates() {
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, true)));

        BlindBoxItem item1 = new BlindBoxItem();
        item1.setId(1L);
        item1.setPoolId(1L);
        item1.setPrizeId(100L);
        item1.setWeight(3);

        BlindBoxItem item2 = new BlindBoxItem();
        item2.setId(2L);
        item2.setPoolId(1L);
        item2.setPrizeId(101L);
        item2.setWeight(7);

        when(blindBoxItemMapper.findByPoolId(1L)).thenReturn(List.of(item1, item2));

        Prize prize1 = new Prize();
        prize1.setId(100L);
        prize1.setName("Prize A");
        prize1.setEnabled(true);
        prize1.setDeleted(false);
        prize1.setStock(10);

        Prize prize2 = new Prize();
        prize2.setId(101L);
        prize2.setName("Prize B");
        prize2.setEnabled(true);
        prize2.setDeleted(false);
        prize2.setStock(5);

        when(prizeMapper.findById(100L)).thenReturn(Optional.of(prize1));
        when(prizeMapper.findById(101L)).thenReturn(Optional.of(prize2));

        List<Map<String, Object>> candidates = blindBoxService.getEffectiveCandidates(1L, familyId);

        assertEquals(2, candidates.size());
        assertEquals("Prize A", candidates.get(0).get("prizeName"));
        assertEquals(3, candidates.get(0).get("weight"));
        assertEquals("Prize B", candidates.get(1).get("prizeName"));
        assertEquals(7, candidates.get(1).get("weight"));
    }

    @Test
    void shouldFilterOutDisabledPrizeFromCandidates() {
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, true)));

        BlindBoxItem item = new BlindBoxItem();
        item.setId(1L);
        item.setPoolId(1L);
        item.setPrizeId(100L);
        item.setWeight(5);

        when(blindBoxItemMapper.findByPoolId(1L)).thenReturn(List.of(item));

        Prize prize = new Prize();
        prize.setId(100L);
        prize.setEnabled(false); // Disabled prize
        prize.setDeleted(false);
        prize.setStock(10);

        when(prizeMapper.findById(100L)).thenReturn(Optional.of(prize));

        List<Map<String, Object>> candidates = blindBoxService.getEffectiveCandidates(1L, familyId);

        assertTrue(candidates.isEmpty(), "Disabled prize should not appear in candidates");
    }

    // ========== Task 5.6: Weighted Random Draw ==========

    @Test
    void shouldDrawSingleCandidateDirectly() {
        // Only one candidate - should short-circuit
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, true)));

        BlindBoxItem item = new BlindBoxItem();
        item.setPoolId(1L);
        item.setPrizeId(100L);
        item.setWeight(10);

        when(blindBoxItemMapper.findByPoolId(1L)).thenReturn(List.of(item));

        Prize prize = new Prize();
        prize.setId(100L);
        prize.setName("Only Prize");
        prize.setEnabled(true);
        prize.setDeleted(false);
        prize.setStock(5);

        when(prizeMapper.findById(100L)).thenReturn(Optional.of(prize));

        Map<String, Object> result = blindBoxService.drawPrize(1L, familyId);

        assertEquals(100L, result.get("prizeId"));
        assertEquals("Only Prize", result.get("prizeName"));
    }

    @Test
    void shouldThrowWhenNoCandidates() {
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, true)));

        when(blindBoxItemMapper.findByPoolId(1L)).thenReturn(List.of());

        assertThrows(BusinessException.class,
                () -> blindBoxService.drawPrize(1L, familyId));
    }

    @Test
    void shouldDrawWithDeterministicRandomSource() {
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, true)));

        BlindBoxItem item1 = new BlindBoxItem();
        item1.setPoolId(1L);
        item1.setPrizeId(100L);
        item1.setWeight(1);

        BlindBoxItem item2 = new BlindBoxItem();
        item2.setPoolId(1L);
        item2.setPrizeId(101L);
        item2.setWeight(99);

        when(blindBoxItemMapper.findByPoolId(1L)).thenReturn(List.of(item1, item2));

        Prize prize1 = new Prize();
        prize1.setId(100L);
        prize1.setName("Light Prize");
        prize1.setEnabled(true);
        prize1.setDeleted(false);
        prize1.setStock(10);

        Prize prize2 = new Prize();
        prize2.setId(101L);
        prize2.setName("Heavy Prize");
        prize2.setEnabled(true);
        prize2.setDeleted(false);
        prize2.setStock(10);

        when(prizeMapper.findById(100L)).thenReturn(Optional.of(prize1));
        when(prizeMapper.findById(101L)).thenReturn(Optional.of(prize2));

        // Set deterministic random source: always returns 0.5, lands in heavy prize range (0.01-1.0)
        blindBoxService.setRandomSource(() -> 0.5);

        // With weight 1+99=100 total, randomValue = 0.5*100 = 50
        // Cumulative: item1 covers [0,1), item2 covers [1,100) => should hit item2
        Map<String, Object> result = blindBoxService.drawPrize(1L, familyId);

        assertEquals(101L, result.get("prizeId"));
        assertEquals("Heavy Prize", result.get("prizeName"));
    }

    @Test
    void shouldDrawLightPrizeWithLowRandomValue() {
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, true)));

        BlindBoxItem item1 = new BlindBoxItem();
        item1.setPoolId(1L);
        item1.setPrizeId(100L);
        item1.setWeight(1);

        BlindBoxItem item2 = new BlindBoxItem();
        item2.setPoolId(1L);
        item2.setPrizeId(101L);
        item2.setWeight(99);

        when(blindBoxItemMapper.findByPoolId(1L)).thenReturn(List.of(item1, item2));

        Prize prize1 = new Prize();
        prize1.setId(100L);
        prize1.setName("Light Prize");
        prize1.setEnabled(true);
        prize1.setDeleted(false);
        prize1.setStock(10);

        Prize prize2 = new Prize();
        prize2.setId(101L);
        prize2.setName("Heavy Prize");
        prize2.setEnabled(true);
        prize2.setDeleted(false);
        prize2.setStock(10);

        when(prizeMapper.findById(100L)).thenReturn(Optional.of(prize1));
        when(prizeMapper.findById(101L)).thenReturn(Optional.of(prize2));

        // Random value near 0 -> falls in first prize range
        blindBoxService.setRandomSource(() -> 0.005);

        Map<String, Object> result = blindBoxService.drawPrize(1L, familyId);

        assertEquals(100L, result.get("prizeId"));
        assertEquals("Light Prize", result.get("prizeName"));
    }

    @Test
    void shouldHaveCorrectProbabilityDistribution() {
        // Statistical test: run 10000 draws with 2 prizes (30% and 70% weights)
        when(blindBoxPoolMapper.findByIdAndFamily(1L, familyId)).thenReturn(
                Optional.of(createPoolEntity(1L, true)));

        BlindBoxItem item1 = new BlindBoxItem();
        item1.setPoolId(1L);
        item1.setPrizeId(100L);
        item1.setWeight(30);

        BlindBoxItem item2 = new BlindBoxItem();
        item2.setPoolId(1L);
        item2.setPrizeId(101L);
        item2.setWeight(70);

        when(blindBoxItemMapper.findByPoolId(1L)).thenReturn(List.of(item1, item2));

        Prize prize1 = new Prize();
        prize1.setId(100L);
        prize1.setName("30% Prize");
        prize1.setEnabled(true);
        prize1.setDeleted(false);
        prize1.setStock(100000);

        Prize prize2 = new Prize();
        prize2.setId(101L);
        prize2.setName("70% Prize");
        prize2.setEnabled(true);
        prize2.setDeleted(false);
        prize2.setStock(100000);

        when(prizeMapper.findById(100L)).thenReturn(Optional.of(prize1));
        when(prizeMapper.findById(101L)).thenReturn(Optional.of(prize2));

        // Use a seeded Random for deterministic but realistic distribution
        Random rng = new Random(42);
        blindBoxService.setRandomSource(rng::nextDouble);

        int samples = 10000;
        int countPrize1 = 0;

        for (int i = 0; i < samples; i++) {
            Map<String, Object> result = blindBoxService.drawPrize(1L, familyId);
            if ((Long) result.get("prizeId") == 100L) {
                countPrize1++;
            }
        }

        double observedProb = (double) countPrize1 / samples;
        // Expected: 30%, tolerance 2% (28%-32%)
        assertTrue(observedProb > 0.28 && observedProb < 0.32,
                "Observed probability " + observedProb + " outside expected range 0.28-0.32");
    }

    // ========== Helpers ==========

    private BlindBoxPool createPoolEntity(Long id, boolean enabled) {
        BlindBoxPool pool = new BlindBoxPool();
        pool.setId(id);
        pool.setFamilyId(familyId);
        pool.setName("Pool " + id);
        pool.setCostPoints(100);
        pool.setEnabled(enabled);
        pool.setDeleted(false);
        return pool;
    }
}
