package project.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.demo.dto.TaskDtos;
import project.demo.entity.*;
import project.demo.enums.*;
import project.demo.repository.*;
import project.demo.spec.TaskSpecifications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final SubTaskRepository subTaskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskLogRepository taskLogRepository;
    private final NotificationService notificationService;

    // ---------- Task CRUD ----------

    @Transactional
    public TaskDtos.TaskSummaryResponse createTask(Long actorId, TaskDtos.CreateTaskRequest req) {
        User actor = mustUser(actorId);
        mustAdmin(actor);

        User assignee = null;
        if (req.assigneeId() != null) {
            assignee = mustUser(req.assigneeId());
        }

        Task task = Task.builder()
                .title(req.title())
                .description(req.description())
                .priority(req.priority())
                .status(TaskStatus.TODO)
                .dueDate(req.dueDate())
                .tags(req.tags() == null ? new HashSet<>() : new HashSet<>(req.tags()))
                .createdBy(actor)
                .assignee(assignee)
                .active(true)
                .build();

        task = taskRepository.save(task);
        log(task, actor, TaskLogAction.CREATED, null, null, null);

        if (assignee != null) {
            notificationService.createNotification(
                    assignee.getId(),
                    actor.getId(),
                    NotificationType.TASK_ASSIGNED,
                    "You were assigned to task: " + task.getTitle(),
                    task.getId()
            );
            log(task, actor, TaskLogAction.ASSIGNED, "assigneeId", null, String.valueOf(assignee.getId()));
        }

        return toSummary(task);
    }

    @Transactional(readOnly = true)
    public Page<TaskDtos.TaskSummaryResponse> listTasks(
            Long actorId,
            String q,
            TaskStatus status,
            TaskPriority priority,
            Long assigneeId,
            LocalDate dueFrom,
            LocalDate dueTo,
            String tag,
            Pageable pageable
    ) {
        User actor = mustUser(actorId);

        // CUSTOMER chỉ được thấy task assigned cho mình
        if (actor.getRole() != Role.ADMIN) {
            assigneeId = actor.getId();
        }

        Specification<Task> spec = TaskSpecifications.activeOnly()
                .and(TaskSpecifications.keyword(q))
                .and(TaskSpecifications.status(status))
                .and(TaskSpecifications.priority(priority))
                .and(TaskSpecifications.assigneeId(assigneeId))
                .and(TaskSpecifications.dueFrom(dueFrom))
                .and(TaskSpecifications.dueTo(dueTo))
                .and(TaskSpecifications.tag(tag));

        return taskRepository.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public TaskDtos.TaskDetailResponse getTaskDetail(Long actorId, Long taskId) {
        User actor = mustUser(actorId);
        Task task = mustActiveTask(taskId);
        assertCanAccessTask(actor, task);

        List<TaskDtos.SubTaskResponse> subs = subTaskRepository.findAllByTaskIdAndActiveTrue(taskId)
                .stream().map(this::toSub).toList();

        List<TaskDtos.CommentResponse> comments = taskCommentRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId)
                .stream().map(this::toComment).toList();

        List<TaskDtos.LogResponse> logs = taskLogRepository.findAllByTaskIdOrderByCreatedAtDesc(taskId)
                .stream().map(this::toLog).toList();

        return new TaskDtos.TaskDetailResponse(toSummary(task), subs, comments, logs);
    }

    @Transactional
    public TaskDtos.TaskSummaryResponse patchTask(Long actorId, Long taskId, TaskDtos.PatchTaskRequest req) {
        User actor = mustUser(actorId);
        mustAdmin(actor);

        Task task = mustActiveTask(taskId);

        if (req.title() != null && !req.title().isBlank() && !req.title().equals(task.getTitle())) {
            log(task, actor, TaskLogAction.UPDATED, "title", task.getTitle(), req.title());
            task.setTitle(req.title());
        }
        if (req.description() != null && !Objects.equals(req.description(), task.getDescription())) {
            log(task, actor, TaskLogAction.UPDATED, "description", task.getDescription(), req.description());
            task.setDescription(req.description());
        }
        if (req.priority() != null && req.priority() != task.getPriority()) {
            log(task, actor, TaskLogAction.UPDATED, "priority", String.valueOf(task.getPriority()), String.valueOf(req.priority()));
            task.setPriority(req.priority());
        }
        if (req.dueDate() != null && !Objects.equals(req.dueDate(), task.getDueDate())) {
            log(task, actor, TaskLogAction.UPDATED, "dueDate", String.valueOf(task.getDueDate()), String.valueOf(req.dueDate()));
            task.setDueDate(req.dueDate());
        }
        if (req.tags() != null) {
            log(task, actor, TaskLogAction.UPDATED, "tags", String.valueOf(task.getTags()), String.valueOf(req.tags()));
            task.setTags(new HashSet<>(req.tags()));
        }

        task = taskRepository.save(task);
        return toSummary(task);
    }

    @Transactional
    public TaskDtos.TaskSummaryResponse assignTask(Long actorId, Long taskId, Long assigneeId) {
        User actor = mustUser(actorId);
        mustAdmin(actor);

        Task task = mustActiveTask(taskId);
        User assignee = mustUser(assigneeId);

        Long old = task.getAssignee() == null ? null : task.getAssignee().getId();
        task.setAssignee(assignee);
        taskRepository.save(task);

        log(task, actor, TaskLogAction.ASSIGNED, "assigneeId",
                old == null ? null : String.valueOf(old),
                String.valueOf(assigneeId));

        notificationService.createNotification(
                assignee.getId(),
                actor.getId(),
                NotificationType.TASK_ASSIGNED,
                "You were assigned to task: " + task.getTitle(),
                task.getId()
        );

        return toSummary(task);
    }

    @Transactional
    public TaskDtos.TaskSummaryResponse updateStatus(Long actorId, Long taskId, TaskStatus status) {
        User actor = mustUser(actorId);
        Task task = mustActiveTask(taskId);
        assertCanAccessTask(actor, task);

        TaskStatus old = task.getStatus();
        if (old != status) {
            task.setStatus(status);
            taskRepository.save(task);

            log(task, actor, TaskLogAction.STATUS_CHANGED, "status", String.valueOf(old), String.valueOf(status));

            if (task.getAssignee() != null) {
                notificationService.createNotification(
                        task.getAssignee().getId(),
                        actor.getId(),
                        NotificationType.TASK_STATUS_CHANGED,
                        "Task status changed: " + task.getTitle() + " -> " + status,
                        task.getId()
                );
            }
        }
        return toSummary(task);
    }

    @Transactional
    public void softDeleteTask(Long actorId, Long taskId) {
        User actor = mustUser(actorId);
        mustAdmin(actor);

        Task task = mustActiveTask(taskId);

        task.setActive(false);
        task.setDeletedAt(Instant.now());
        taskRepository.save(task);

        log(task, actor, TaskLogAction.DELETED, "active", "true", "false");
    }

    // ---------- Subtasks ----------

    @Transactional
    public TaskDtos.SubTaskResponse createSubTask(Long actorId, Long taskId, TaskDtos.CreateSubTaskRequest req) {
        User actor = mustUser(actorId);
        Task task = mustActiveTask(taskId);
        assertCanAccessTask(actor, task);

        SubTask st = SubTask.builder()
                .task(task)
                .title(req.title())
                .done(false)
                .active(true)
                .build();
        st = subTaskRepository.save(st);

        log(task, actor, TaskLogAction.SUBTASK_CREATED, "subtask", null, st.getTitle());
        return toSub(st);
    }

    @Transactional
    public TaskDtos.SubTaskResponse patchSubTask(Long actorId, Long taskId, Long subTaskId, TaskDtos.PatchSubTaskRequest req) {
        User actor = mustUser(actorId);
        Task task = mustActiveTask(taskId);
        assertCanAccessTask(actor, task);

        SubTask st = subTaskRepository.findById(subTaskId).orElseThrow(() -> new RuntimeException("SUBTASK_NOT_FOUND"));
        if (!Objects.equals(st.getTask().getId(), taskId) || !st.isActive()) throw new RuntimeException("SUBTASK_NOT_FOUND");

        if (req.title() != null && !req.title().isBlank() && !req.title().equals(st.getTitle())) {
            log(task, actor, TaskLogAction.SUBTASK_UPDATED, "subtask.title", st.getTitle(), req.title());
            st.setTitle(req.title());
        }

        // ✅ FIX: done update ổn định
        if (req.done() != null && !Objects.equals(req.done(), st.isDone())) {
            log(task, actor, TaskLogAction.SUBTASK_UPDATED, "subtask.done", String.valueOf(st.isDone()), String.valueOf(req.done()));
            st.setDone(req.done());
        }

        st = subTaskRepository.save(st);
        return toSub(st);
    }

    @Transactional(readOnly = true)
    public TaskDtos.SubTaskResponse getSubTaskDetail(Long actorId, Long taskId, Long subTaskId) {
        User actor = mustUser(actorId);
        Task task = mustActiveTask(taskId);
        assertCanAccessTask(actor, task);

        SubTask st = subTaskRepository.findById(subTaskId).orElseThrow(() -> new RuntimeException("SUBTASK_NOT_FOUND"));
        if (!Objects.equals(st.getTask().getId(), taskId) || !st.isActive()) throw new RuntimeException("SUBTASK_NOT_FOUND");

        return toSub(st);
    }

    @Transactional
    public void softDeleteSubTask(Long actorId, Long taskId, Long subTaskId) {
        User actor = mustUser(actorId);
        Task task = mustActiveTask(taskId);
        assertCanAccessTask(actor, task);

        SubTask st = subTaskRepository.findById(subTaskId)
                .orElseThrow(() -> new RuntimeException("SUBTASK_NOT_FOUND"));
        if (!Objects.equals(st.getTask().getId(), taskId) || !st.isActive())
            throw new RuntimeException("SUBTASK_NOT_FOUND");

        st.setActive(false);
        st.setDeletedAt(Instant.now());
        subTaskRepository.save(st);

        log(task, actor, TaskLogAction.SUBTASK_DELETED, "subtask", st.getTitle(), null);
    }

    // ---------- Comments ----------

    @Transactional
    public TaskDtos.CommentResponse addComment(Long actorId, Long taskId, TaskDtos.CreateCommentRequest req) {
        User actor = mustUser(actorId);
        Task task = mustActiveTask(taskId);
        assertCanAccessTask(actor, task);

        TaskComment c = TaskComment.builder()
                .task(task)
                .author(actor)
                .content(req.content())
                .build();
        c = taskCommentRepository.save(c);

        log(task, actor, TaskLogAction.COMMENTED, "comment", null, "(added)");

        if (task.getAssignee() != null && !Objects.equals(task.getAssignee().getId(), actorId)) {
            notificationService.createNotification(
                    task.getAssignee().getId(),
                    actor.getId(),
                    NotificationType.COMMENT_ADDED,
                    "New comment on task: " + task.getTitle(),
                    task.getId()
            );
        }
        return toComment(c);
    }

    // ---------- helpers ----------

    private void mustAdmin(User actor) {
        if (actor.getRole() != Role.ADMIN) throw new RuntimeException("FORBIDDEN");
    }

    private void assertCanAccessTask(User actor, Task task) {
        if (actor.getRole() == Role.ADMIN) return;

        // CUSTOMER chỉ được thao tác trên task assigned cho mình
        if (task.getAssignee() == null || !Objects.equals(task.getAssignee().getId(), actor.getId())) {
            throw new RuntimeException("FORBIDDEN");
        }
    }

    private User mustUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));
    }

    private Task mustActiveTask(Long id) {
        Task t = taskRepository.findById(id).orElseThrow(() -> new RuntimeException("TASK_NOT_FOUND"));
        if (!t.isActive()) throw new RuntimeException("TASK_NOT_FOUND");
        return t;
    }

    private void log(Task task, User actor, TaskLogAction action, String fieldName, String oldVal, String newVal) {
        TaskLog l = TaskLog.builder()
                .task(task)
                .actor(actor)
                .action(action)
                .fieldName(fieldName)
                .oldValue(oldVal)
                .newValue(newVal)
                .build();
        taskLogRepository.save(l);
    }

    private TaskDtos.TaskSummaryResponse toSummary(Task t) {
        TaskDtos.UserBrief assignee = null;
        if (t.getAssignee() != null) {
            var a = t.getAssignee();
            assignee = new TaskDtos.UserBrief(a.getId(), a.getEmail(), a.getFullName());
        }
        return new TaskDtos.TaskSummaryResponse(
                t.getId(),
                t.getTitle(),
                t.getStatus(),
                t.getPriority(),
                t.getDueDate(),
                t.getTags(),
                assignee,
                t.isActive(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    private TaskDtos.SubTaskResponse toSub(SubTask s) {
        return new TaskDtos.SubTaskResponse(s.getId(), s.getTitle(), s.isDone(), s.isActive(), s.getCreatedAt());
    }

    private TaskDtos.CommentResponse toComment(TaskComment c) {
        var a = c.getAuthor();
        return new TaskDtos.CommentResponse(
                c.getId(),
                c.getContent(),
                new TaskDtos.UserBrief(a.getId(), a.getEmail(), a.getFullName()),
                c.getCreatedAt()
        );
    }

    private TaskDtos.LogResponse toLog(TaskLog l) {
        var a = l.getActor();
        return new TaskDtos.LogResponse(
                l.getId(),
                l.getAction().name(),
                l.getFieldName(),
                l.getOldValue(),
                l.getNewValue(),
                new TaskDtos.UserBrief(a.getId(), a.getEmail(), a.getFullName()),
                l.getCreatedAt()
        );
    }
}
