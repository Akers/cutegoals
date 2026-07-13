package com.cutegoals.family.controller;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.dto.ApiResponse;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.family.service.ChildProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChildProfileController, focusing on GET /api/family/children (fix-parent-pages-contract DD1).
 */
@ExtendWith(MockitoExtension.class)
class ChildProfileControllerTest {

    @Mock
    private ChildProfileService childProfileService;

    private ChildProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ChildProfileController(childProfileService);
    }

    @Test
    void listChildrenShouldThrow401WhenUnauthenticated() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // No account id attribute -> controller should throw UNAUTHORIZED
        when(request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.listChildren(1, 20, request));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void listChildrenShouldReturnPageResultShapeWhenAuthenticated() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(AuthConstants.ATTR_ACCOUNT_ID)).thenReturn(100L);
        when(request.getAttribute(AuthConstants.ATTR_FAMILY_ID)).thenReturn(7L);

        Map<String, Object> pageResult = new LinkedHashMap<>();
        pageResult.put("content", List.of(Map.of("id", 1L, "nickname", "小明")));
        pageResult.put("page", 1);
        pageResult.put("pageSize", 20);
        pageResult.put("totalElements", 1);
        pageResult.put("totalPages", 1);
        when(childProfileService.listChildrenPage(1, 20, 7L)).thenReturn(pageResult);

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                controller.listChildren(1, 20, request);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        Map<String, Object> data = response.getBody().getData();
        assertNotNull(data);
        assertEquals(1, data.get("page"));
        assertEquals(20, data.get("pageSize"));
        assertEquals(1, data.get("totalElements"));
        assertEquals(1, data.get("totalPages"));
        assertNotNull(data.get("content"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
        assertEquals(1, content.size());
        assertEquals("小明", content.get(0).get("nickname"));
    }
}
