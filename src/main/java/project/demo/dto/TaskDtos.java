package project.demo.dto;

import jakarta.validation.constraints.*;
import project.demo.enums.TaskPriority;
import project.demo.enums.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public class TaskDtos {

    // -------- Requests --------

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            String description,
            @NotNull TaskPriority priority,
            LocalDate dueDate,
            Set<String> tags,
            Long assigneeId
    ) {}

    // PATCH: gửi field nào thì update field đó
    public record PatchTaskRequest(
            @Size(max = 200) String title,
            String description,
            TaskPriority priority,
            LocalDate dueDate,
            Set<String> tags
    ) {}

    public record AssignRequest(@NotNull Long assigneeId) {}

    public record UpdateStatusRequest(@NotNull TaskStatus status) {}

    public record CreateSubTaskRequest(@NotBlank @Size(max = 200) String title) {}

    public record PatchSubTaskRequest(String title, Boolean done) {}

    public record CreateCommentRequest(@NotBlank String content) {}

    // -------- Responses --------

    public record UserBrief(Long id, String email, String fullName) {}

    public record TaskSummaryResponse(
            Long id,
            String title,
            TaskStatus status,
            TaskPriority priority,
            LocalDate dueDate,
            Set<String> tags,
            UserBrief assignee,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SubTaskResponse(Long id, String title, boolean done, boolean active, Instant createdAt) {}

    public record CommentResponse(Long id, String content, UserBrief author, Instant createdAt) {}

    public record LogResponse(Long id, String action, String fieldName, String oldValue, String newValue, UserBrief actor, Instant createdAt) {}

    public record TaskDetailResponse(
            TaskSummaryResponse task,
            List<SubTaskResponse> subtasks,
            List<CommentResponse> comments,
            List<LogResponse> logs
    ) {}

    public record NotificationResponse(Long id, String type, String content, Long taskId, boolean read, Instant createdAt) {}
}
