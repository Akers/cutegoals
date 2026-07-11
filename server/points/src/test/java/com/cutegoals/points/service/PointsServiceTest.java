package com.cutegoals.points.service;

import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.points.PointsBalance;
import com.cutegoals.common.entity.points.PointsLedger;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.points.mapper.PointsBalanceMapper;
import com.cutegoals.points.mapper.PointsLedgerMapper;
import com.cutegoals.task.mapper.TaskChildMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointsServiceTest {

    @Mock private PointsLedgerMapper pointsLedgerMapper;
    @Mock private PointsBalanceMapper pointsBalanceMapper;
    @Mock private TaskChildMapper taskChildMapper;
    @Mock private FamilyMapper familyMapper;
    @Mock private AuditService auditService;

    private PointsService pointsService;

    private final Long familyId = 1L;
    private final Long childId = 10L;
    private final Long accountId = 100L;

    @BeforeEach
    void setUp() {
        pointsService = new PointsService(
                pointsLedgerMapper, pointsBalanceMapper, taskChildMapper,
                familyMapper, auditService);
    }

    // ========== Task 4.8: Balance Query ==========

    @Test
    void shouldGetBalance() {
        mockChildInFamily(childId, familyId);

        PointsBalance balance = new PointsBalance();
        balance.setChildId(childId);
        balance.setBalance(100);
        balance.setTotalEarned(200);

        when(pointsBalanceMapper.findByChildId(childId)).thenReturn(Optional.of(balance));

        PointsBalance result = pointsService.getBalance(childId, familyId, null);
        assertEquals(100, result.getBalance());
        assertEquals(200, result.getTotalEarned());
    }

    @Test
    void shouldThrowForbiddenWhenChildViewsOtherBalance() {
        Long otherChildId = 99L;
        // viewerChildId check happens before validateChildInFamily, no mock needed
        assertThrows(BusinessException.class, () ->
                pointsService.getBalance(otherChildId, familyId, childId));
    }

    @Test
    void shouldThrowNotFoundWhenBalanceMissing() {
        mockChildInFamily(childId, familyId);
        when(pointsBalanceMapper.findByChildId(childId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () ->
                pointsService.getBalance(childId, familyId, null));
    }

    // ========== Task 4.9: Adjustment ==========

    @Test
    void shouldAdjustPointsPositively() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", 50);
        request.put("reason", "Good behavior bonus");
        request.put("businessRef", "ADJ-001");

        List<String> parentRoles = List.of("PARENT");

        ChildProfile child = new ChildProfile();
        child.setId(childId);
        child.setFamilyId(familyId);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));

        when(pointsLedgerMapper.findByBusinessRef(childId, "ADJ-001")).thenReturn(Optional.empty());

        PointsBalance balance = new PointsBalance();
        balance.setChildId(childId);
        balance.setBalance(100);
        balance.setTotalEarned(100);
        balance.setVersion(1);
        when(pointsBalanceMapper.findByChildIdForUpdate(childId)).thenReturn(Optional.of(balance));

        when(pointsBalanceMapper.updateBalanceOnlyWithVersion(eq(childId), eq(150), eq(1))).thenReturn(1);

        doAnswer(invocation -> {
            PointsLedger l = invocation.getArgument(0);
            l.setId(500L);
            return 1;
        }).when(pointsLedgerMapper).insert(any(PointsLedger.class));

        PointsLedger result = pointsService.adjustPoints(request, childId, familyId, accountId, parentRoles);

        assertNotNull(result);
        assertEquals("ADJUST", result.getType());
        assertEquals(50, result.getAmount());
        verify(auditService).record(anyString(), eq(accountId), eq("SUCCESS"), anyString());
    }

    @Test
    void shouldAdjustPointsNegatively() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", -30);
        request.put("reason", "Behavior correction");
        request.put("businessRef", "ADJ-002");

        List<String> parentRoles = List.of("PARENT");

        ChildProfile child = new ChildProfile();
        child.setId(childId);
        child.setFamilyId(familyId);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));

        when(pointsLedgerMapper.findByBusinessRef(childId, "ADJ-002")).thenReturn(Optional.empty());

        PointsBalance balance = new PointsBalance();
        balance.setChildId(childId);
        balance.setBalance(100);
        balance.setTotalEarned(100);
        balance.setVersion(1);
        when(pointsBalanceMapper.findByChildIdForUpdate(childId)).thenReturn(Optional.of(balance));

        when(pointsBalanceMapper.updateBalanceOnlyWithVersion(eq(childId), eq(70), eq(1))).thenReturn(1);

        doAnswer(invocation -> {
            PointsLedger l = invocation.getArgument(0);
            l.setId(501L);
            return 1;
        }).when(pointsLedgerMapper).insert(any(PointsLedger.class));

        PointsLedger result = pointsService.adjustPoints(request, childId, familyId, accountId, parentRoles);

        assertNotNull(result);
        assertEquals(-30, result.getAmount());
    }

    @Test
    void shouldThrowInsufficientBalanceForNegativeAdjust() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", -200);
        request.put("reason", "Too much deduction");
        request.put("businessRef", "ADJ-003");

        List<String> parentRoles = List.of("PARENT");

        ChildProfile child = new ChildProfile();
        child.setId(childId);
        child.setFamilyId(familyId);
        when(taskChildMapper.findById(childId)).thenReturn(Optional.of(child));

        when(pointsLedgerMapper.findByBusinessRef(childId, "ADJ-003")).thenReturn(Optional.empty());

        PointsBalance balance = new PointsBalance();
        balance.setChildId(childId);
        balance.setBalance(100);
        balance.setVersion(1);
        when(pointsBalanceMapper.findByChildIdForUpdate(childId)).thenReturn(Optional.of(balance));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                pointsService.adjustPoints(request, childId, familyId, accountId, parentRoles));
        assertEquals(ErrorCode.POINTS_INSUFFICIENT_BALANCE, ex.getErrorCode());
    }

    @Test
    void shouldThrowForbiddenWhenChildAdjusts() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", 10);
        request.put("reason", "Test");
        request.put("businessRef", "ADJ-004");

        List<String> childRoles = List.of("CHILD");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                pointsService.adjustPoints(request, childId, familyId, accountId, childRoles));
        assertEquals(ErrorCode.POINTS_FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenAdjustWithoutReason() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", 10);
        request.put("businessRef", "ADJ-005");

        List<String> parentRoles = List.of("PARENT");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                pointsService.adjustPoints(request, childId, familyId, accountId, parentRoles));
        assertEquals(ErrorCode.POINTS_ADJUST_REASON_REQUIRED, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenAdjustAmountExceedsLimit() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", 2_000_000_000);
        request.put("reason", "Too much");
        request.put("businessRef", "ADJ-006");

        List<String> parentRoles = List.of("PARENT");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                pointsService.adjustPoints(request, childId, familyId, accountId, parentRoles));
        assertEquals(ErrorCode.POINTS_INVALID_TRANSACTION, ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenAmountIsZero() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", 0);
        request.put("reason", "Zero adjust");
        request.put("businessRef", "ADJ-007");

        List<String> parentRoles = List.of("PARENT");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                pointsService.adjustPoints(request, childId, familyId, accountId, parentRoles));
        assertEquals(ErrorCode.POINTS_INVALID_TRANSACTION, ex.getErrorCode());
    }

    // ========== Task 4.10: Ledger Query ==========

    @Test
    void shouldQueryLedger() {
        mockChildInFamily(childId, familyId);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", 1);
        params.put("pageSize", 20);

        when(pointsBalanceMapper.findByChildId(childId)).thenReturn(
                Optional.of(createBalance(50, 100)));

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsLedger> mpPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20, 0);
        mpPage.setRecords(List.of());
        when(pointsLedgerMapper.selectPage(any(), any())).thenReturn(mpPage);

        Map<String, Object> result = pointsService.queryLedger(childId, params, familyId, null);

        assertNotNull(result);
        assertEquals(50, result.get("currentBalance"));
        assertEquals(100, result.get("totalEarned"));
    }

    @Test
    void shouldThrowInvalidQueryForBadPageSize() {
        mockChildInFamily(childId, familyId);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pageSize", 200);

        assertThrows(BusinessException.class, () ->
                pointsService.queryLedger(childId, params, familyId, null));
    }

    // ========== Helpers ==========

    private void mockChildInFamily(Long cId, Long fId) {
        ChildProfile childProfile = new ChildProfile();
        childProfile.setId(cId);
        childProfile.setFamilyId(fId);
        when(taskChildMapper.findById(cId)).thenReturn(Optional.of(childProfile));
    }

    private PointsBalance createBalance(int balance, int totalEarned) {
        PointsBalance pb = new PointsBalance();
        pb.setChildId(childId);
        pb.setBalance(balance);
        pb.setTotalEarned(totalEarned);
        pb.setVersion(1);
        return pb;
    }
}
