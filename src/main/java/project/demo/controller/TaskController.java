package project.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.demo.dto.TaskDtos;
import project.demo.enums.TaskPriority;
import project.demo.enums.TaskStatus;
import project.demo.security.CustomUserDetails;
import project.demo.service.TaskService;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    private Long uid(CustomUserDetails principal) {
        if (principal == null) throw new RuntimeException("UNAUTHORIZED");
        return principal.getId();
    }

    // ADMIN only
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TaskDtos.TaskSummaryResponse> create(
            @RequestBody @Valid TaskDtos.CreateTaskRequest req,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.createTask(uid(principal), req));
    }

    // ADMIN: xem tất cả | CUSTOMER: chỉ thấy task assigned cho mình (service sẽ ép assigneeId)
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<Page<TaskDtos.TaskSummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        String[] s = sort.split(",");
        Sort sortObj = Sort.by(Sort.Direction.fromString(s.length > 1 ? s[1] : "desc"), s[0]);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        return ResponseEntity.ok(
                taskService.listTasks(uid(principal), q, status, priority, assigneeId, dueFrom, dueTo, tag, pageable)
        );
    }

    // ADMIN: xem mọi task | CUSTOMER: chỉ xem task assigned cho mình
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskDtos.TaskDetailResponse> detail(
            @PathVariable Long taskId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.getTaskDetail(uid(principal), taskId));
    }

    // ADMIN only
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{taskId}")
    public ResponseEntity<TaskDtos.TaskSummaryResponse> patch(
            @PathVariable Long taskId,
            @RequestBody TaskDtos.PatchTaskRequest req,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.patchTask(uid(principal), taskId, req));
    }

    // ADMIN only
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{taskId}/assignee")
    public ResponseEntity<TaskDtos.TaskSummaryResponse> assign(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskDtos.AssignRequest req,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.assignTask(uid(principal), taskId, req.assigneeId()));
    }

    // CUSTOMER được update status cho task của mình | ADMIN được update status mọi task
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskDtos.TaskSummaryResponse> status(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskDtos.UpdateStatusRequest req,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.updateStatus(uid(principal), taskId, req.status()));
    }

    // ADMIN only
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long taskId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        taskService.softDeleteTask(uid(principal), taskId);
        return ResponseEntity.noContent().build();
    }

    // CUSTOMER tạo subtask cho task của mình | ADMIN cho mọi task
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{taskId}/subtasks")
    public ResponseEntity<TaskDtos.SubTaskResponse> createSub(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskDtos.CreateSubTaskRequest req,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.createSubTask(uid(principal), taskId, req));
    }

    // CUSTOMER patch subtask cho task của mình | ADMIN cho mọi task
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/{taskId}/subtasks/{subTaskId}")
    public ResponseEntity<TaskDtos.SubTaskResponse> patchSub(
            @PathVariable Long taskId,
            @PathVariable Long subTaskId,
            @RequestBody TaskDtos.PatchSubTaskRequest req,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.patchSubTask(uid(principal), taskId, subTaskId, req));
    }

    // (tuỳ bạn) Nếu muốn CUSTOMER không được delete subtask thì đổi thành hasRole('ADMIN')
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{taskId}/subtasks/{subTaskId}")
    public ResponseEntity<Void> deleteSub(
            @PathVariable Long taskId,
            @PathVariable Long subTaskId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        taskService.softDeleteSubTask(uid(principal), taskId, subTaskId);
        return ResponseEntity.noContent().build();
    }

    // CUSTOMER comment task của mình | ADMIN comment mọi task
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{taskId}/comments")
    public ResponseEntity<TaskDtos.CommentResponse> comment(
            @PathVariable Long taskId,
            @RequestBody @Valid TaskDtos.CreateCommentRequest req,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.addComment(uid(principal), taskId, req));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{taskId}/subtasks/{subTaskId}")
    public ResponseEntity<TaskDtos.SubTaskResponse> subtaskDetail(
            @PathVariable Long taskId,
            @PathVariable Long subTaskId,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(taskService.getSubTaskDetail(uid(principal), taskId, subTaskId));
    }
}
