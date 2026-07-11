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

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for task assignment management (Tasks 3.5-3.10).
 */
@Service
@RequiredArgsConstructor
public class TaskAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(TaskAssignmentService.class);

    private final TaskTemplateMapper taskTemplateMapper;
    private final TaskDifficultyMapper taskDifficultyMapper;
    private final TaskRecurrenceRuleMapper taskRecurrenceRuleMapper;
    private final TaskAssignmentMapper taskAssignmentMapper;
    private final TaskAssignmentSnapshotMapper taskAssignmentSnapshotMapper;
    private final TaskChildMapper taskChildMapper;
    private final FamilyMapper familyMapper;
    private final TaskTemplateService taskTemplateService;
    private final AuditService auditService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DATE_RANGE_DAYS = 366;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;

    // ========== Task 3.5: Single Task Assignment ==========

    @Transactional
    public TaskAssignment createAssignment(Map<String, Object> request, Long familyId, Long accountId) {
        // Validate idempotency key
        String idempotencyKey = extractAndValidateIdempotencyKey(request);
        if (idempotencyKey != null) {
            Optional<TaskAssignment> existing = taskAssignmentMapper.findByIdempotencyKey(idempotencyKey, familyId);
            if (existing.isPresent()) {
                // Idempotency: return existing
                return existing.get();
            }
        }

        // Extract fields
        Long templateId = extractLong(request, "templateId");
        Long difficultyId = extractLong(request, "difficultyId");
        Long childId = extractLong(request, "childId");
        String deadlineStr = extractString(request, "deadline");

        // Validate template active
        TaskTemplate template = taskTemplateService.getActiveTemplate(templateId, familyId);
        TaskDifficulty difficulty = taskTemplateService.getEnabledDifficulty(difficultyId, templateId);

        // Validate child belongs to family
        ChildProfile child = taskChildMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_CHILD_NOT_FOUND));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_CHILD_NOT_FOUND);
        }

        // Validate deadline
        LocalDateTime deadline = parseDeadline(deadlineStr);
        if (deadline.isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_DEADLINE,
                    "Deadline must be in the future");
        }

        // Get late policy from family default
        String latePolicy = getFamilyLatePolicy(familyId);

        // Create assignment with snapshot
        TaskAssignment assignment = createAssignmentEntity(template, difficulty, childId, familyId,
                deadline, latePolicy, idempotencyKey, null);

        auditService.record(AuditEvent.ASSIGNMENT_CREATED, accountId, "SUCCESS",
                "Task assignment created: id=" + assignment.getId() + ", childId=" + childId
                        + ", template=" + template.getName());

        log.info("Task assignment created: id={}, childId={}, template={}",
                assignment.getId(), childId, template.getName());

        return assignment;
    }

    // ========== Task 3.6: Batch Assignment ==========

    @Transactional
    public List<TaskAssignment> createBatchAssignments(Map<String, Object> request, Long familyId, Long accountId) {
        // Validate idempotency key
        String idempotencyKey = extractAndValidateIdempotencyKey(request);
        if (idempotencyKey != null) {
            Optional<TaskAssignment> existing = taskAssignmentMapper.findByIdempotencyKey(idempotencyKey, familyId);
            if (existing.isPresent()) {
                // If the same key was already used, check if it's a batch - we can't return all from a single lookup
                // For simplicity, return the first one found and let client query
                return List.of(existing.get());
            }
        }

        Long templateId = extractLong(request, "templateId");
        Long difficultyId = extractLong(request, "difficultyId");
        String startDateStr = extractString(request, "startDate");
        String endDateStr = extractString(request, "endDate");

        @SuppressWarnings("unchecked")
        List<Long> childIds = (List<Long>) request.get("childIds");
        if (childIds == null || childIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "At least one childId is required");
        }

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        // Validate date range
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_DATE_RANGE,
                    "Start date must not be after end date");
        }
        long daysBetween = startDate.datesUntil(endDate.plusDays(1)).count();
        if (daysBetween > MAX_DATE_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_DATE_RANGE,
                    "Date range must not exceed " + MAX_DATE_RANGE_DAYS + " days");
        }

        // Validate template and difficulty
        TaskTemplate template = taskTemplateService.getActiveTemplate(templateId, familyId);
        TaskDifficulty difficulty = taskTemplateService.getEnabledDifficulty(difficultyId, templateId);

        // Validate all children
        for (Long childId : childIds) {
            ChildProfile child = taskChildMapper.findById(childId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_CHILD_NOT_FOUND,
                            "Child not found: " + childId));
            if (!child.getFamilyId().equals(familyId)) {
                throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_CHILD_NOT_FOUND,
                        "Child not in family: " + childId);
            }
        }

        // Get late policy from family default
        String latePolicy = getFamilyLatePolicy(familyId);

        // Create deadline based on end date (last day of range)
        LocalDateTime deadline = endDate.atTime(23, 59, 59);

        // Create assignments
        List<TaskAssignment> assignments = new ArrayList<>();
        for (Long childId : childIds) {
            TaskAssignment assignment = createAssignmentEntity(template, difficulty, childId, familyId,
                    deadline, latePolicy, idempotencyKey, null);
            assignments.add(assignment);
        }

        auditService.record(AuditEvent.ASSIGNMENT_BATCH_CREATED, accountId, "SUCCESS",
                "Batch task assignments created: count=" + assignments.size()
                        + ", template=" + template.getName());

        log.info("Batch task assignments created: count={}", assignments.size());

        return assignments;
    }

    // ========== Task 3.7: Recurring Generation ==========

    @Transactional
    public Map<String, Object> generateRecurringAssignments(Map<String, Object> request, Long familyId, Long accountId) {
        Long templateId = extractLong(request, "templateId");
        Long difficultyId = extractLong(request, "difficultyId");
        Long childId = extractLong(request, "childId");
        String startDateStr = extractString(request, "startDate");
        String endDateStr = extractString(request, "endDate");

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        // Validate date range
        if (startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_DATE_RANGE,
                    "Start date must not be after end date");
        }
        long daysBetween = startDate.datesUntil(endDate.plusDays(1)).count();
        if (daysBetween > MAX_DATE_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_DATE_RANGE,
                    "Date range must not exceed " + MAX_DATE_RANGE_DAYS + " days");
        }

        // Validate template active
        TaskTemplate template = taskTemplateService.getActiveTemplate(templateId, familyId);

        // Get recurrence rule
        TaskRecurrenceRule rule = taskRecurrenceRuleMapper.findByTemplateId(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_RECURRENCE_NOT_CONFIGURED));

        // Validate child
        ChildProfile child = taskChildMapper.findById(childId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_CHILD_NOT_FOUND));
        if (!child.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_CHILD_NOT_FOUND);
        }

        // Validate difficulty (optional - use first enabled if not specified)
        TaskDifficulty difficulty;
        if (difficultyId != null) {
            difficulty = taskTemplateService.getEnabledDifficulty(difficultyId, templateId);
        } else {
            List<TaskDifficulty> enabledDifficulties = taskDifficultyMapper.findEnabledByTemplateId(templateId);
            if (enabledDifficulties.isEmpty()) {
                throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_DIFFICULTY_INACTIVE);
            }
            difficulty = enabledDifficulties.get(0);
        }

        // Get late policy
        String latePolicy = getFamilyLatePolicy(familyId);

        // Generate occurrences based on recurrence rule
        Set<DayOfWeek> matchedDays = getMatchedDays(rule);
        String occurrenceKeyPrefix = familyId + "_" + childId + "_" + templateId + "_";

        int created = 0;
        int skipped = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!matchedDays.contains(date.getDayOfWeek())) {
                continue;
            }

            String occurrenceKey = occurrenceKeyPrefix + date;
            // Check if already exists
            int existing = taskAssignmentMapper.countByOccurrenceKey(occurrenceKey);
            if (existing > 0) {
                skipped++;
                continue;
            }

            LocalDateTime deadline = date.atTime(23, 59, 59);
            TaskAssignment assignment = createAssignmentEntity(template, difficulty, childId, familyId,
                    deadline, latePolicy, null, occurrenceKey);
            created++;
        }

        auditService.record(AuditEvent.ASSIGNMENT_GENERATED, accountId, "SUCCESS",
                "Recurring assignments generated: created=" + created + ", skipped=" + skipped
                        + ", template=" + template.getName());

        log.info("Recurring assignments generated: created={}, skipped={}", created, skipped);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", created);
        result.put("skipped", skipped);
        return result;
    }

    private Set<DayOfWeek> getMatchedDays(TaskRecurrenceRule rule) {
        return switch (rule.getRuleType()) {
            case "DAILY" -> Set.of(DayOfWeek.values());
            case "WEEKDAYS" -> Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
            case "WEEKENDS" -> Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            case "CUSTOM_WEEKDAYS" -> {
                if (rule.getCustomWeekdays() == null || rule.getCustomWeekdays().isBlank()) {
                    throw new BusinessException(ErrorCode.TASK_TEMPLATE_INVALID_RECURRENCE);
                }
                Set<DayOfWeek> days = new HashSet<>();
                for (String s : rule.getCustomWeekdays().split(",")) {
                    int dayNum = Integer.parseInt(s.trim());
                    // ISO: 1=Monday...7=Sunday
                    days.add(DayOfWeek.of(dayNum));
                }
                yield days;
            }
            default -> throw new BusinessException(ErrorCode.TASK_TEMPLATE_INVALID_RECURRENCE);
        };
    }

    // ========== Task 3.7: Snapshot persistence ==========

    private TaskAssignment createAssignmentEntity(TaskTemplate template, TaskDifficulty difficulty,
                                                   Long childId, Long familyId, LocalDateTime deadline,
                                                   String latePolicy, String idempotencyKey,
                                                   String occurrenceKey) {
        TaskAssignment assignment = new TaskAssignment();
        assignment.setFamilyId(familyId);
        assignment.setTemplateId(template.getId());
        assignment.setChildId(childId);
        assignment.setDifficultyId(difficulty.getId());
        assignment.setDeadline(deadline);
        assignment.setStatus("PENDING");
        assignment.setLatePolicy(latePolicy);
        assignment.setCancelled(false);
        assignment.setVersion(1);
        assignment.setIdempotencyKey(idempotencyKey);
        assignment.setOccurrenceKey(occurrenceKey);

        // Snapshot fields
        assignment.setSnapshotTemplateName(template.getName());
        assignment.setSnapshotTemplateDescription(template.getDescription());
        assignment.setSnapshotTemplateCategory(template.getCategory());
        assignment.setSnapshotTemplateIcon(template.getIcon());
        assignment.setSnapshotDifficultyName(difficulty.getName());
        assignment.setSnapshotDifficultyReward(difficulty.getRewardPoints());

        taskAssignmentMapper.insert(assignment);

        // Also write to snapshot table for history tracking
        TaskAssignmentSnapshot snapshot = new TaskAssignmentSnapshot();
        snapshot.setAssignmentId(assignment.getId());
        snapshot.setTemplateName(template.getName());
        snapshot.setTemplateDescription(template.getDescription());
        snapshot.setTemplateCategory(template.getCategory());
        snapshot.setTemplateIcon(template.getIcon());
        snapshot.setDifficultyName(difficulty.getName());
        snapshot.setDifficultyRewardPoints(difficulty.getRewardPoints());
        taskAssignmentSnapshotMapper.insert(snapshot);

        return assignment;
    }

    // ========== Task 3.8: Assignment List & Calendar Query ==========

    public Map<String, Object> queryAssignments(Map<String, Object> params, Long familyId, Long viewerChildId) {
        int pageNum = params.containsKey("page") ? ((Number) params.get("page")).intValue() : 1;
        int pageSize = params.containsKey("pageSize") ? ((Number) params.get("pageSize")).intValue() : DEFAULT_PAGE_SIZE;

        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_QUERY,
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        LambdaQueryWrapper<TaskAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskAssignment::getFamilyId, familyId);

        // Filter by child (always for child viewer)
        if (viewerChildId != null) {
            wrapper.eq(TaskAssignment::getChildId, viewerChildId);
        } else if (params.containsKey("childId")) {
            wrapper.eq(TaskAssignment::getChildId, ((Number) params.get("childId")).longValue());
        }

        // Filter by template
        if (params.containsKey("templateId")) {
            wrapper.eq(TaskAssignment::getTemplateId, ((Number) params.get("templateId")).longValue());
        }

        // Filter by status
        if (params.containsKey("status")) {
            String status = (String) params.get("status");
            wrapper.eq(TaskAssignment::getStatus, status);
        }

        // Filter by cancelled
        if (params.containsKey("cancelled")) {
            wrapper.eq(TaskAssignment::getCancelled, Boolean.valueOf((String) params.get("cancelled")));
        }

        // Filter by overdue (derived, computed after query)
        // overdue filter is applied in-memory after fetching

        // Date range filter
        if (params.containsKey("startDate") && params.containsKey("endDate")) {
            LocalDate startDate = LocalDate.parse((String) params.get("startDate"));
            LocalDate endDate = LocalDate.parse((String) params.get("endDate"));

            long daysBetween = startDate.datesUntil(endDate.plusDays(1)).count();
            if (daysBetween > MAX_DATE_RANGE_DAYS) {
                throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_QUERY,
                        "Date range must not exceed " + MAX_DATE_RANGE_DAYS + " days");
            }

            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
            wrapper.ge(TaskAssignment::getDeadline, startDateTime);
            wrapper.le(TaskAssignment::getDeadline, endDateTime);
        }

        // Order by deadline asc, id asc
        wrapper.orderByAsc(TaskAssignment::getDeadline);
        wrapper.orderByAsc(TaskAssignment::getId);

        Page<TaskAssignment> page = taskAssignmentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        // Enrich with overdue info
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> enrichedContent = new ArrayList<>();
        for (TaskAssignment assignment : page.getRecords()) {
            enrichedContent.add(enrichAssignment(assignment, now));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", enrichedContent);
        result.put("page", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalElements", page.getTotal());
        result.put("totalPages", page.getPages());

        return result;
    }

    // ========== Calendar Query ==========

    public Map<String, Object> getCalendar(Map<String, Object> params, Long familyId, Long viewerChildId) {
        int year = Integer.parseInt((String) params.get("year"));
        int month = Integer.parseInt((String) params.get("month"));

        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_QUERY, "Invalid month");
        }

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        LambdaQueryWrapper<TaskAssignment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskAssignment::getFamilyId, familyId);

        if (viewerChildId != null) {
            wrapper.eq(TaskAssignment::getChildId, viewerChildId);
        } else if (params.containsKey("childId")) {
            wrapper.eq(TaskAssignment::getChildId, ((Number) params.get("childId")).longValue());
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        wrapper.ge(TaskAssignment::getDeadline, startDateTime);
        wrapper.le(TaskAssignment::getDeadline, endDateTime);

        List<TaskAssignment> allAssignments = taskAssignmentMapper.selectList(wrapper);
        LocalDateTime now = LocalDateTime.now();

        // Group by local date
        Map<LocalDate, Map<String, Object>> calendarData = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("total", 0);
            dayData.put("pending", 0);
            dayData.put("submitted", 0);
            dayData.put("approved", 0);
            dayData.put("rejected", 0);
            dayData.put("cancelled", 0);
            dayData.put("overdue", 0);
            calendarData.put(date, dayData);
        }

        for (TaskAssignment assignment : allAssignments) {
            LocalDate date = assignment.getDeadline().toLocalDate();
            Map<String, Object> dayData = calendarData.get(date);
            if (dayData == null) continue;

            dayData.merge("total", 1, (a, b) -> ((int) a) + 1);

            if (Boolean.TRUE.equals(assignment.getCancelled())) {
                dayData.merge("cancelled", 1, (a, b) -> ((int) a) + 1);
            } else {
                String status = assignment.getStatus() != null ? assignment.getStatus() : "PENDING";
                dayData.merge(status.toLowerCase(), 1, (a, b) -> ((int) a) + 1);

                // Overdue check
                boolean isOverdue = now.isAfter(assignment.getDeadline())
                        && !"APPROVED".equals(assignment.getStatus())
                        && !Boolean.TRUE.equals(assignment.getCancelled());
                if (isOverdue) {
                    dayData.merge("overdue", 1, (a, b) -> ((int) a) + 1);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", year);
        result.put("month", month);
        result.put("days", calendarData);
        return result;
    }

    // ========== Task 3.9: Cancel ==========

    @Transactional
    public TaskAssignment cancelAssignment(Long assignmentId, String reason, Long familyId, Long accountId) {
        TaskAssignment assignment = taskAssignmentMapper.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));

        if (!assignment.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND);
        }

        // Already cancelled - idempotent
        if (Boolean.TRUE.equals(assignment.getCancelled())) {
            return assignment;
        }

        // Cannot cancel approved assignments
        if ("APPROVED".equals(assignment.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_ALREADY_APPROVED);
        }

        // Validate reason
        if (reason == null || reason.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Cancellation reason is required");
        }

        assignment.setCancelled(true);
        assignment.setCancelledAt(LocalDateTime.now());
        assignment.setCancelledBy(accountId);
        assignment.setCancelledReason(reason.trim());
        taskAssignmentMapper.updateById(assignment);

        auditService.record(AuditEvent.ASSIGNMENT_CANCELLED, accountId, "SUCCESS",
                "Task assignment cancelled: id=" + assignmentId + ", reason=" + reason);

        log.info("Task assignment cancelled: id={}", assignmentId);
        return assignment;
    }

    // ========== Task 3.10: Update Assignment ==========

    @Transactional
    public TaskAssignment updateAssignment(Long assignmentId, Map<String, Object> request, Long familyId, Long accountId) {
        TaskAssignment assignment = taskAssignmentMapper.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));

        if (!assignment.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND);
        }

        // Check if cancelled
        if (Boolean.TRUE.equals(assignment.getCancelled())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_EDITABLE,
                    "Cannot edit a cancelled assignment");
        }

        // Check if editable status
        String status = assignment.getStatus();
        if (!"PENDING".equals(status) && !"REJECTED".equals(status)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_EDITABLE,
                    "Only PENDING or REJECTED assignments can be edited");
        }

        // Version conflict check
        if (request.containsKey("version")) {
            Integer clientVersion = ((Number) request.get("version")).intValue();
            if (!clientVersion.equals(assignment.getVersion())) {
                throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_VERSION_CONFLICT,
                        "Assignment was modified. Current version: " + assignment.getVersion());
            }
        }

        // Update difficulty if provided
        if (request.containsKey("difficultyId")) {
            Long difficultyId = extractLong(request, "difficultyId");
            TaskTemplate template = taskTemplateMapper.findById(assignment.getTemplateId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TASK_TEMPLATE_NOT_FOUND));
            TaskDifficulty difficulty = taskTemplateService.getEnabledDifficulty(difficultyId, template.getId());

            assignment.setDifficultyId(difficulty.getId());
            assignment.setSnapshotDifficultyName(difficulty.getName());
            assignment.setSnapshotDifficultyReward(difficulty.getRewardPoints());
        }

        // Update deadline if provided
        if (request.containsKey("deadline")) {
            String deadlineStr = extractString(request, "deadline");
            LocalDateTime deadline = parseDeadline(deadlineStr);
            if (deadline.isBefore(LocalDateTime.now())) {
                throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_DEADLINE,
                        "Deadline must be in the future");
            }
            assignment.setDeadline(deadline);
        }

        // Update late policy if provided
        if (request.containsKey("latePolicy")) {
            String latePolicy = extractString(request, "latePolicy");
            if (!Set.of("ALLOW", "REJECT").contains(latePolicy)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "latePolicy must be ALLOW or REJECT");
            }
            assignment.setLatePolicy(latePolicy);
        }

        // Increment version
        assignment.setVersion(assignment.getVersion() + 1);

        // Optimistic lock: update only if version matches
        int updated = taskAssignmentMapper.updateById(assignment);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_VERSION_CONFLICT);
        }

        auditService.record(AuditEvent.ASSIGNMENT_UPDATED, accountId, "SUCCESS",
                "Task assignment updated: id=" + assignmentId);

        log.info("Task assignment updated: id={}", assignmentId);
        return taskAssignmentMapper.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
    }

    // ========== Get single assignment ==========

    public Map<String, Object> getAssignmentDetail(Long assignmentId, Long familyId, Long viewerChildId) {
        TaskAssignment assignment = taskAssignmentMapper.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));

        if (!assignment.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND);
        }

        if (viewerChildId != null && !assignment.getChildId().equals(viewerChildId)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_FORBIDDEN);
        }

        return enrichAssignment(assignment, LocalDateTime.now());
    }

    // ========== Late Policy Management ==========

    @Transactional
    public void updateLatePolicy(Long assignmentId, String latePolicy, Long familyId, Long accountId) {
        TaskAssignment assignment = taskAssignmentMapper.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND));

        if (!assignment.getFamilyId().equals(familyId)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_FOUND);
        }

        if (!"PENDING".equals(assignment.getStatus())) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_NOT_EDITABLE,
                    "Can only override late policy for PENDING assignments");
        }

        assignment.setLatePolicy(latePolicy);
        taskAssignmentMapper.updateById(assignment);

        auditService.record(AuditEvent.ASSIGNMENT_LATE_POLICY_OVERRIDE, accountId, "SUCCESS",
                "Late policy override: id=" + assignmentId + ", policy=" + latePolicy);
    }

    // ========== Helpers ==========

    private Map<String, Object> enrichAssignment(TaskAssignment assignment, LocalDateTime now) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", assignment.getId());
        item.put("templateId", assignment.getTemplateId());
        item.put("childId", assignment.getChildId());
        item.put("difficultyId", assignment.getDifficultyId());
        item.put("deadline", assignment.getDeadline());
        item.put("status", assignment.getStatus());
        item.put("latePolicy", assignment.getLatePolicy());
        item.put("cancelled", assignment.getCancelled());
        item.put("cancelledAt", assignment.getCancelledAt());
        item.put("cancelledReason", assignment.getCancelledReason());
        item.put("cancelledBy", assignment.getCancelledBy());
        item.put("version", assignment.getVersion());
        item.put("createdAt", assignment.getCreatedAt());
        item.put("updatedAt", assignment.getUpdatedAt());

        // Snapshot fields
        item.put("snapshotTemplateName", assignment.getSnapshotTemplateName());
        item.put("snapshotTemplateDescription", assignment.getSnapshotTemplateDescription());
        item.put("snapshotTemplateCategory", assignment.getSnapshotTemplateCategory());
        item.put("snapshotTemplateIcon", assignment.getSnapshotTemplateIcon());
        item.put("snapshotDifficultyName", assignment.getSnapshotDifficultyName());
        item.put("snapshotDifficultyReward", assignment.getSnapshotDifficultyReward());

        // Overdue is derived: strictly later than deadline
        boolean isOverdue = now.isAfter(assignment.getDeadline())
                && !Boolean.TRUE.equals(assignment.getCancelled())
                && !"APPROVED".equals(assignment.getStatus());
        item.put("overdue", isOverdue);

        return item;
    }

    private String getFamilyLatePolicy(Long familyId) {
        // Default late policy is ALLOW if not configured
        // In MVP, we use a simple default; could be extended to per-family setting
        return "ALLOW";
    }

    private String extractAndValidateIdempotencyKey(Map<String, Object> request) {
        Object keyObj = request.get("idempotencyKey");
        if (keyObj == null) {
            return null;
        }
        String key = keyObj.toString().trim();
        if (key.isEmpty()) {
            return null;
        }
        if (key.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "idempotencyKey must not exceed " + IDEMPOTENCY_KEY_MAX_LENGTH + " characters");
        }
        return key;
    }

    private Long extractLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return ((Number) val).longValue();
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        return val.toString();
    }

    private LocalDateTime parseDeadline(String deadlineStr) {
        if (deadlineStr == null) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_DEADLINE,
                    "Deadline is required");
        }
        try {
            return LocalDateTime.parse(deadlineStr);
        } catch (Exception e) {
            try {
                LocalDate date = LocalDate.parse(deadlineStr);
                return date.atTime(23, 59, 59);
            } catch (Exception e2) {
                throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_INVALID_DEADLINE,
                        "Invalid deadline format. Use ISO format: yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss");
            }
        }
    }

    public Long getSingleFamilyId() {
        List<Family> families = familyMapper.selectList(null);
        if (families.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "No family found");
        }
        return families.get(0).getId();
    }

    public void verifyParentRole(List<String> roles) {
        if (!roles.contains(AuthConstants.ROLE_PARENT) && !roles.contains(AuthConstants.ROLE_INSTANCE_ADMIN)) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNMENT_FORBIDDEN);
        }
    }
}
