package com.cutegoals.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cutegoals.auth.mapper.FamilyMapper;
import com.cutegoals.auth.service.AuditEvent;
import com.cutegoals.auth.service.AuditService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.family.ChildProfile;
import com.cutegoals.common.entity.family.Family;
import com.cutegoals.common.entity.task.*;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.task.mapper.*;
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
 * Service for task template management (Tasks 3.1-3.4).
 */
@Service
@RequiredArgsConstructor
public class TaskTemplateService {

    private static final Logger log = LoggerFactory.getLogger(TaskTemplateService.class);

    private final TaskTemplateMapper taskTemplateMapper;
    private final TaskDifficultyMapper taskDifficultyMapper;
    private final TaskRecurrenceRuleMapper taskRecurrenceRuleMapper;
    private final TaskAssignmentMapper taskAssignmentMapper;
    private final FamilyMapper familyMapper;
    private final TaskChildMapper taskChildMapper;
    private final AuditService auditService;

    // Validation constants
    private static final int NAME_MAX_LENGTH = 100;
    private static final int CATEGORY_MAX_LENGTH = 50;
    private static final int DESCRIPTION_MAX_LENGTH = 2000;
    private static final int ICON_MAX_LENGTH = 500;
    private static final int DIFFICULTY_NAME_MAX_LENGTH = 50;
    private static final int DIFFICULTY_MIN_REWARD = 1;
    private static final int DIFFICULTY_MAX_REWARD = 1_000_000;
    private static final int MAX_DIFFICULTIES = 20;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // ========== Task 3.1: Create ==========

    @Transactional
    public TaskTemplate createTemplate(Map<String, Object> request, Long familyId, Long accountId) {
        // Extract and validate fields
        String name = extractAndValidateString(request, "name", true, 1, NAME_MAX_LENGTH, "name");
        String category = extractAndValidateString(request, "category", true, 1, CATEGORY_MAX_LENGTH, "category");
        String description = extractAndValidateString(request, "description", false, 0, DESCRIPTION_MAX_LENGTH, "description");
        String icon = extractAndValidateString(request, "icon", false, 0, ICON_MAX_LENGTH, "icon");

        // Validate difficulties
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> difficulties = (List<Map<String, Object>>) request.get("difficulties");
        if (difficulties == null || difficulties.isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    "At least one difficulty is required");
        }
        if (difficulties.size() > MAX_DIFFICULTIES) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    "Maximum " + MAX_DIFFICULTIES + " difficulties allowed");
        }

        // Create template
        TaskTemplate template = new TaskTemplate();
        template.setFamilyId(familyId);
        template.setName(name.trim());
        template.setCategory(category.trim());
        template.setDescription(description != null ? description.trim() : null);
        template.setIcon(icon != null ? icon.trim() : null);
        template.setVersion(1);
        template.setEnabled(true);
        template.setDeleted(false);

        // Task 11.6: Extract taskType and typeConfig
        String taskType = (String) request.get("taskType");
        if (taskType != null) {
            template.setTaskType(taskType);
        }
        String typeConfig = (String) request.get("typeConfig");
        if (typeConfig != null) {
            template.setTypeConfig(typeConfig);
        }

        taskTemplateMapper.insert(template);

        // Create difficulties
        boolean hasEnabledDifficulty = difficulties.stream()
                .anyMatch(d -> !Boolean.FALSE.equals(d.get("enabled")));
        if (!hasEnabledDifficulty) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY);
        }

        Set<Integer> usedOrders = new HashSet<>();
        for (Map<String, Object> diffReq : difficulties) {
            String diffName = extractAndValidateString(diffReq, "name", true, 1, DIFFICULTY_NAME_MAX_LENGTH, "difficulty.name");
            Integer displayOrder = extractAndValidateInt(diffReq, "displayOrder", true, 1, MAX_DIFFICULTIES, "difficulty.displayOrder");
            Integer rewardPoints = extractAndValidateInt(diffReq, "rewardPoints", true, DIFFICULTY_MIN_REWARD, DIFFICULTY_MAX_REWARD, "difficulty.rewardPoints");

            if (!usedOrders.add(displayOrder)) {
                throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                        "Duplicate displayOrder: " + displayOrder);
            }

            TaskDifficulty difficulty = new TaskDifficulty();
            difficulty.setTemplateId(template.getId());
            difficulty.setName(diffName.trim());
            difficulty.setDisplayOrder(displayOrder);
            difficulty.setRewardPoints(rewardPoints);
            difficulty.setEnabled(true);
            taskDifficultyMapper.insert(difficulty);
        }

        // Handle recurrence rule
        if (request.containsKey("recurrenceRule") && request.get("recurrenceRule") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> ruleReq = (Map<String, Object>) request.get("recurrenceRule");
            createRecurrenceRule(template.getId(), ruleReq);
        }

        auditService.record(AuditEvent.TEMPLATE_CREATED, accountId, "SUCCESS",
                "Task template created: id=" + template.getId() + ", name=" + template.getName());

        log.info("Task template created: id={}, name={}", template.getId(), template.getName());
        return taskTemplateMapper.findById(template.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    // ========== Task 3.2: Recurrence Rule ==========

    private void createRecurrenceRule(Long templateId, Map<String, Object> ruleReq) {
        String ruleType = extractAndValidateString(ruleReq, "ruleType", true, 1, 50, "recurrenceRule.ruleType");
        validateRecurrenceRuleType(ruleType);

        TaskRecurrenceRule rule = new TaskRecurrenceRule();
        rule.setTemplateId(templateId);
        rule.setRuleType(ruleType);

        if ("CUSTOM_WEEKDAYS".equals(ruleType)) {
            @SuppressWarnings("unchecked")
            List<Integer> weekdays = (List<Integer>) ruleReq.get("customWeekdays");
            if (weekdays == null || weekdays.isEmpty()) {
                throw new BusinessException(ErrorCode.TASK_TEMPLATE_INVALID_RECURRENCE,
                        "CUSTOM_WEEKDAYS requires at least one weekday");
            }
            // Validate ISO weekdays 1-7
            for (Integer day : weekdays) {
                if (day == null || day < 1 || day > 7) {
                    throw new BusinessException(ErrorCode.TASK_TEMPLATE_INVALID_RECURRENCE,
                            "Weekday must be between 1 (Monday) and 7 (Sunday), got: " + day);
                }
            }
            // Deduplicate and sort
            String customWeekdays = weekdays.stream()
                    .distinct()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            rule.setCustomWeekdays(customWeekdays);
        }

        taskRecurrenceRuleMapper.insert(rule);
    }

    private void validateRecurrenceRuleType(String ruleType) {
        if (!Set.of("DAILY", "WEEKDAYS", "WEEKENDS", "CUSTOM_WEEKDAYS").contains(ruleType)) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_INVALID_RECURRENCE,
                    "Unknown recurrence rule type: " + ruleType);
        }
    }

    // ========== Task 3.3: Update Difficulties ==========

    @Transactional
    public TaskTemplate updateTemplate(Long templateId, Map<String, Object> request, Long familyId, Long accountId) {
        TaskTemplate template = taskTemplateMapper.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND));

        if (!template.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND);
        }

        // Version check — REQUIRED (spec: concurrent update version conflict)
        if (!request.containsKey("version")) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VERSION_CONFLICT,
                    "Version is required for template update");
        }
        Integer clientVersion = ((Number) request.get("version")).intValue();
        if (!clientVersion.equals(template.getVersion())) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VERSION_CONFLICT,
                    "Template was modified by another user. Current version: " + template.getVersion());
        }

        // Task type is immutable after creation
        if (request.containsKey("taskType")) {
            String requestedType = (String) request.get("taskType");
            if (!requestedType.equals(template.getTaskType())) {
                throw new BusinessException(ErrorCode.TASK_TEMPLATE_TYPE_IMMUTABLE,
                        "Task type cannot be changed after creation");
            }
        }

        // Update fields
        if (request.containsKey("name")) {
            String name = extractAndValidateString(request, "name", true, 1, NAME_MAX_LENGTH, "name");
            template.setName(name.trim());
        }
        if (request.containsKey("category")) {
            String category = extractAndValidateString(request, "category", true, 1, CATEGORY_MAX_LENGTH, "category");
            template.setCategory(category.trim());
        }
        if (request.containsKey("description")) {
            String description = extractAndValidateString(request, "description", false, 0, DESCRIPTION_MAX_LENGTH, "description");
            template.setDescription(description != null ? description.trim() : null);
        }
        if (request.containsKey("icon")) {
            String icon = extractAndValidateString(request, "icon", false, 0, ICON_MAX_LENGTH, "icon");
            template.setIcon(icon != null ? icon.trim() : null);
        }

        // Task 11.6: Update typeConfig if provided (taskType is immutable, but config can change)
        if (request.containsKey("typeConfig")) {
            String typeConfig = (String) request.get("typeConfig");
            template.setTypeConfig(typeConfig);
        }

        // Optimistic lock via mapper (version-based)
        int updated = taskTemplateMapper.optimisticUpdate(template.getId(), clientVersion);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VERSION_CONFLICT,
                    "Template was modified by another user. Current version: " + template.getVersion());
        }
        template.setVersion(clientVersion + 1);

        // Update difficulties if provided
        if (request.containsKey("difficulties")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> difficulties = (List<Map<String, Object>>) request.get("difficulties");
            updateDifficulties(templateId, difficulties);
        }

        // Update recurrence rule if provided
        if (request.containsKey("recurrenceRule")) {
            // Delete existing rule
            taskRecurrenceRuleMapper.deleteByTemplateId(templateId);
            if (request.get("recurrenceRule") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ruleReq = (Map<String, Object>) request.get("recurrenceRule");
                createRecurrenceRule(templateId, ruleReq);
            }
        }

        auditService.record(AuditEvent.TEMPLATE_UPDATED, accountId, "SUCCESS",
                "Task template updated: id=" + templateId);

        log.info("Task template updated: id={}", templateId);
        return taskTemplateMapper.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    @Transactional
    public void updateDifficulties(Long templateId, List<Map<String, Object>> difficulties) {
        if (difficulties.size() > MAX_DIFFICULTIES) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    "Maximum " + MAX_DIFFICULTIES + " difficulties allowed");
        }

        // Validate each difficulty
        Set<Integer> usedOrders = new HashSet<>();
        boolean hasEnabled = false;

        for (Map<String, Object> diffReq : difficulties) {
            String diffName = extractAndValidateString(diffReq, "name", true, 1, DIFFICULTY_NAME_MAX_LENGTH, "difficulty.name");
            Integer displayOrder = extractAndValidateInt(diffReq, "displayOrder", true, 1, MAX_DIFFICULTIES, "difficulty.displayOrder");
            Integer rewardPoints = extractAndValidateInt(diffReq, "rewardPoints", true, DIFFICULTY_MIN_REWARD, DIFFICULTY_MAX_REWARD, "difficulty.rewardPoints");
            Boolean enabled = diffReq.containsKey("enabled") ? (Boolean) diffReq.get("enabled") : true;

            if (!usedOrders.add(displayOrder)) {
                throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                        "Duplicate displayOrder: " + displayOrder);
            }

            if (enabled) {
                hasEnabled = true;
            }
        }

        if (!hasEnabled) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_REQUIRES_ACTIVE_DIFFICULTY);
        }

        // Get existing difficulties, indexed by displayOrder
        List<TaskDifficulty> existingDifficulties = taskDifficultyMapper.findByTemplateId(templateId);
        Map<Integer, TaskDifficulty> existingByOrder = new HashMap<>();
        for (TaskDifficulty ed : existingDifficulties) {
            existingByOrder.put(ed.getDisplayOrder(), ed);
        }

        // Process each difficulty in the new request set
        for (Map<String, Object> diffReq : difficulties) {
            String diffName = (String) diffReq.get("name");
            Integer displayOrder = ((Number) diffReq.get("displayOrder")).intValue();
            Integer rewardPoints = ((Number) diffReq.get("rewardPoints")).intValue();
            Boolean enabled = diffReq.containsKey("enabled") ? (Boolean) diffReq.get("enabled") : true;

            TaskDifficulty existing = existingByOrder.get(displayOrder);
            if (existing != null) {
                // Update existing row (preserves ID for historical references)
                existing.setName(diffName.trim());
                existing.setDisplayOrder(displayOrder);
                existing.setRewardPoints(rewardPoints);
                existing.setEnabled(enabled);
                taskDifficultyMapper.updateById(existing);
                existingByOrder.remove(displayOrder);
            } else {
                // Insert new difficulty
                TaskDifficulty difficulty = new TaskDifficulty();
                difficulty.setTemplateId(templateId);
                difficulty.setName(diffName.trim());
                difficulty.setDisplayOrder(displayOrder);
                difficulty.setRewardPoints(rewardPoints);
                difficulty.setEnabled(enabled);
                taskDifficultyMapper.insert(difficulty);
            }
        }

        // Handle existing difficulties not in the new set (removed)
        for (Map.Entry<Integer, TaskDifficulty> entry : existingByOrder.entrySet()) {
            TaskDifficulty removed = entry.getValue();
            // Check if referenced by any assignment
            int refCount = taskAssignmentMapper.countByDifficultyId(removed.getId());
            if (refCount > 0) {
                // Referenced → soft-disable only, keep historical data intact
                removed.setEnabled(false);
                taskDifficultyMapper.updateById(removed);
            } else {
                // Not referenced → safe to physical delete
                taskDifficultyMapper.deleteById(removed.getId());
            }
        }
    }

    // ========== Task 3.4: Soft Delete ==========

    @Transactional
    public void deleteTemplate(Long templateId, Long familyId, Long accountId) {
        TaskTemplate template = taskTemplateMapper.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND));

        if (!template.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND);
        }

        if (Boolean.TRUE.equals(template.getDeleted())) {
            // Idempotent: already deleted
            return;
        }

        template.setDeleted(true);
        template.setDeletedAt(LocalDateTime.now());
        template.setDeletedBy(accountId);
        taskTemplateMapper.updateById(template);

        auditService.record(AuditEvent.TEMPLATE_DELETED, accountId, "SUCCESS",
                "Task template deleted: id=" + templateId);

        log.info("Task template deleted: id={}", templateId);
    }

    // ========== Enable/Disable ==========

    @Transactional
    public TaskTemplate setTemplateEnabled(Long templateId, boolean enabled, Long familyId, Long accountId) {
        TaskTemplate template = taskTemplateMapper.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND));

        if (!template.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND);
        }

        template.setEnabled(enabled);
        int updated = taskTemplateMapper.optimisticUpdate(template.getId(), template.getVersion());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VERSION_CONFLICT);
        }
        template.setVersion(template.getVersion() + 1);

        auditService.record(enabled ? AuditEvent.TEMPLATE_ENABLED : AuditEvent.TEMPLATE_DISABLED,
                accountId, "SUCCESS", "Task template " + (enabled ? "enabled" : "disabled") + ": id=" + templateId);

        return taskTemplateMapper.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    // ========== Query (Task 3.1) ==========

    public Map<String, Object> queryTemplates(Map<String, Object> params, Long familyId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_INVALID_QUERY,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (pageNum < 1) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_INVALID_QUERY,
                    "Page number must be >= 1");
        }

        LambdaQueryWrapper<TaskTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskTemplate::getFamilyId, familyId);

        // Filter by category
        if (params.containsKey("category")) {
            String category = (String) params.get("category");
            if (category != null && !category.isBlank()) {
                wrapper.eq(TaskTemplate::getCategory, category.trim());
            }
        }

        // Filter by enabled status
        if (params.containsKey("enabled")) {
            Object enabledVal = params.get("enabled");
            if (enabledVal instanceof Boolean) {
                wrapper.eq(TaskTemplate::getEnabled, (Boolean) enabledVal);
            }
        }

        // Filter by deleted status (default: exclude deleted)
        boolean includeDeleted = Boolean.TRUE.equals(params.get("includeDeleted"));
        if (!includeDeleted) {
            wrapper.eq(TaskTemplate::getDeleted, false);
        }

        // Search by name keyword
        if (params.containsKey("keyword")) {
            String keyword = (String) params.get("keyword");
            if (keyword != null && !keyword.isBlank()) {
                wrapper.like(TaskTemplate::getName, keyword.trim());
            }
        }

        // Order by updated_at desc, id asc
        wrapper.orderByDesc(TaskTemplate::getUpdatedAt);
        wrapper.orderByAsc(TaskTemplate::getId);

        Page<TaskTemplate> page = taskTemplateMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", page.getRecords());
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());

        // Enrich each template with difficulties and recurrence rule
        List<Map<String, Object>> enrichedContent = new ArrayList<>();
        for (TaskTemplate t : page.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("name", t.getName());
            item.put("category", t.getCategory());
            item.put("description", t.getDescription());
            item.put("icon", t.getIcon());
            item.put("enabled", t.getEnabled());
            item.put("deleted", t.getDeleted());
            item.put("version", t.getVersion());
            item.put("taskType", t.getTaskType());
            item.put("typeConfig", t.getTypeConfig());
            item.put("createdAt", t.getCreatedAt());
            item.put("updatedAt", t.getUpdatedAt());
            item.put("difficulties", taskDifficultyMapper.findByTemplateId(t.getId()));
            item.put("recurrenceRule", taskRecurrenceRuleMapper.findByTemplateId(t.getId()).orElse(null));
            enrichedContent.add(item);
        }
        result.put("content", enrichedContent);

        return result;
    }

    public Map<String, Object> getTemplateDetail(Long templateId, Long familyId) {
        TaskTemplate template = taskTemplateMapper.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND));

        if (!template.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", template.getId());
        result.put("name", template.getName());
        result.put("category", template.getCategory());
        result.put("description", template.getDescription());
        result.put("icon", template.getIcon());
        result.put("enabled", template.getEnabled());
        result.put("deleted", template.getDeleted());
        result.put("version", template.getVersion());
        result.put("taskType", template.getTaskType());
        result.put("typeConfig", template.getTypeConfig());
        result.put("createdAt", template.getCreatedAt());
        result.put("updatedAt", template.getUpdatedAt());
        result.put("difficulties", taskDifficultyMapper.findByTemplateId(template.getId()));
        result.put("recurrenceRule", taskRecurrenceRuleMapper.findByTemplateId(template.getId()).orElse(null));

        return result;
    }

    // Utility: Get the single family for this instance
    public Long getSingleFamilyId() {
        List<Family> families = familyMapper.selectList(null);
        if (families.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "No family found");
        }
        return families.get(0).getId();
    }

    public void verifyParentRole(List<String> roles) {
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_FORBIDDEN);
        }
    }

    // Validation helpers
    private String extractAndValidateString(Map<String, Object> map, String key, boolean required,
                                            int minLen, int maxLen, String fieldName) {
        Object val = map.get(key);
        if (val == null) {
            if (required) {
                throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                        fieldName + " is required");
            }
            return null;
        }
        if (!(val instanceof String s)) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    fieldName + " must be a string");
        }
        String trimmed = s.trim();
        if (required && trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    fieldName + " must not be blank");
        }
        if (trimmed.length() > maxLen) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    fieldName + " must not exceed " + maxLen + " characters");
        }
        if (trimmed.length() < minLen) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    fieldName + " must be at least " + minLen + " characters");
        }
        return trimmed;
    }

    private Integer extractAndValidateInt(Map<String, Object> map, String key, boolean required,
                                          int min, int max, String fieldName) {
        Object val = map.get(key);
        if (val == null) {
            if (required) {
                throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                        fieldName + " is required");
            }
            return null;
        }
        int intVal;
        if (val instanceof Number n) {
            intVal = n.intValue();
        } else {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    fieldName + " must be a number");
        }
        if (intVal < min || intVal > max) {
            throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                    fieldName + " must be between " + min + " and " + max);
        }
        if (val instanceof Double || val instanceof Float) {
            double doubleVal = ((Number) val).doubleValue();
            if (doubleVal != Math.floor(doubleVal)) {
                throw new BusinessException(ErrorCode.TASK_TEMPLATE_VALIDATION_FAILED,
                        fieldName + " must be an integer");
            }
        }
        return intVal;
    }

    // Check template active (not deleted, not disabled)
    public TaskTemplate getActiveTemplate(Long templateId, Long familyId) {
        TaskTemplate template = taskTemplateMapper.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_TEMPLATE_INACTIVE));

        if (!template.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_TEMPLATE_INACTIVE);
        }

        if (Boolean.TRUE.equals(template.getDeleted()) || Boolean.FALSE.equals(template.getEnabled())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_TEMPLATE_INACTIVE);
        }

        return template;
    }

    public TaskDifficulty getEnabledDifficulty(Long difficultyId, Long templateId) {
        TaskDifficulty difficulty = taskDifficultyMapper.selectById(difficultyId);
        if (difficulty == null || !difficulty.getTemplateId().equals(templateId) || Boolean.FALSE.equals(difficulty.getEnabled())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_DIFFICULTY_INACTIVE);
        }
        return difficulty;
    }
}
