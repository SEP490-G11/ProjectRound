package project.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import project.demo.dto.TaskDtos;
import project.demo.entity.*;
import project.demo.enums.*;
import project.demo.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock UserRepository userRepository;
    @Mock TaskRepository taskRepository;
    @Mock SubTaskRepository subTaskRepository;
    @Mock TaskLogRepository taskLogRepository;

    // Nếu repo comment của bạn khác tên -> đổi lại
    @Mock TaskCommentRepository commentRepository;

    @Mock NotificationService notificationService;

    @InjectMocks TaskService taskService;

    // ==== sample users ====
    private final User admin = user(1L, "admin@local.test", "Admin", Role.ADMIN);
    private final User customer = user(2L, "user1@gmail.com", "User One", Role.CUSTOMER);
    private final User otherCustomer = user(3L, "user2@gmail.com", "User Two", Role.CUSTOMER);

    // =========================
    // ADMIN-ONLY: create/patch/delete/assign task
    // =========================

    @Test
    void createTask_admin_shouldCreate_andLogCreated() {
        stubActor(admin);

        TaskDtos.CreateTaskRequest req = new TaskDtos.CreateTaskRequest(
                "Task 1", "desc", TaskPriority.HIGH,
                LocalDate.of(2025, 12, 30),
                Set.of("backend"),
                null
        );

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        TaskDtos.TaskSummaryResponse res = taskService.createTask(admin.getId(), req);

        assertNotNull(res);
        assertEquals(10L, res.id());
        assertEquals("Task 1", res.title());

        verify(taskRepository).save(any(Task.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    @Test
    void createTask_customer_shouldThrowForbidden() {
        stubActor(customer);

        TaskDtos.CreateTaskRequest req = new TaskDtos.CreateTaskRequest(
                "Task X", null, TaskPriority.LOW, null, null, null
        );

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> taskService.createTask(customer.getId(), req));

        assertEquals("FORBIDDEN", ex.getMessage());
    }

    @Test
    void patchTask_admin_shouldUpdateTitle_andLog() {
        stubActor(admin);

        Task existing = task(10L, admin, customer);
        existing.setTitle("Old");
        existing.setDescription("OldDesc");

        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskDtos.PatchTaskRequest req = new TaskDtos.PatchTaskRequest(
                "New", "NewDesc", null, null, null
        );

        TaskDtos.TaskSummaryResponse res = taskService.patchTask(admin.getId(), 10L, req);

        assertEquals("New", res.title());
        verify(taskRepository).save(any(Task.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    @Test
    void softDeleteTask_admin_shouldSetInactive() {
        stubActor(admin);

        Task existing = task(10L, admin, customer);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        taskService.softDeleteTask(admin.getId(), 10L);

        assertFalse(existing.isActive());
        assertNotNull(existing.getDeletedAt());
        verify(taskRepository).save(any(Task.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    @Test
    void assignTask_admin_shouldAssignAndLog() {
        stubActor(admin);

        Task existing = task(10L, admin, customer);
        existing.setAssignee(null);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer)); // assignee lookup
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskDtos.TaskSummaryResponse res = taskService.assignTask(admin.getId(), 10L, customer.getId());

        assertNotNull(res.assignee());
        assertEquals(customer.getId(), res.assignee().id());
        verify(taskRepository).save(any(Task.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    // =========================
    // CUSTOMER: thao tác trong task "của mình" (assignee/createdBy)
    // =========================

    @Test
    void getTaskDetail_customer_onOwnTask_shouldReturnSubtasksCommentsLogs() {
        stubActor(customer);

        Task existing = task(10L, admin, customer); // customer là assignee => có quyền
        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));

        when(subTaskRepository.findAllByTaskIdAndActiveTrue(eq(10L)))
                .thenReturn(List.of(subTask(existing, 101L, "S1", false)));

        // NOTE: nếu method repo của bạn khác tên -> đổi đúng theo repo thật
        when(commentRepository.findAllByTaskIdOrderByCreatedAtAsc(eq(10L)))
                .thenReturn(List.of(comment(existing, 201L, customer, "Hi")));

        when(taskLogRepository.findAllByTaskIdOrderByCreatedAtDesc(eq(10L)))
                .thenReturn(List.of(log(existing, 301L, customer, TaskLogAction.CREATED, null)));

        TaskDtos.TaskDetailResponse res = taskService.getTaskDetail(customer.getId(), 10L);

        assertEquals(10L, res.task().id());
        assertEquals(1, res.subtasks().size());
        assertEquals(1, res.comments().size());
        assertEquals(1, res.logs().size());
    }

    @Test
    void createSubTask_customer_onOwnTask_shouldCreateAndLog() {
        stubActor(customer);

        Task existing = task(10L, admin, customer);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));

        when(subTaskRepository.save(any(SubTask.class))).thenAnswer(inv -> {
            SubTask st = inv.getArgument(0);
            st.setId(1001L);
            return st;
        });

        TaskDtos.CreateSubTaskRequest req = new TaskDtos.CreateSubTaskRequest("Sub 1");

        TaskDtos.SubTaskResponse res = taskService.createSubTask(customer.getId(), 10L, req);

        assertEquals(1001L, res.id());
        assertEquals("Sub 1", res.title());
        verify(subTaskRepository).save(any(SubTask.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    @Test
    void patchSubTask_customer_onOwnTask_shouldUpdateTitleAndDone() {
        stubActor(customer);

        Task existing = task(10L, admin, customer);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));

        SubTask st = subTask(existing, 1001L, "Old", false);
        when(subTaskRepository.findById(1001L)).thenReturn(Optional.of(st));
        when(subTaskRepository.save(any(SubTask.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskDtos.PatchSubTaskRequest req = new TaskDtos.PatchSubTaskRequest("New", true);

        TaskDtos.SubTaskResponse res = taskService.patchSubTask(customer.getId(), 10L, 1001L, req);

        assertEquals("New", res.title());
        assertTrue(res.done());
        verify(subTaskRepository).save(any(SubTask.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    @Test
    void softDeleteSubTask_customer_onOwnTask_shouldInactiveAndLog() {
        stubActor(customer);

        Task existing = task(10L, admin, customer);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));

        SubTask st = subTask(existing, 1001L, "S", false);
        when(subTaskRepository.findById(1001L)).thenReturn(Optional.of(st));
        when(subTaskRepository.save(any(SubTask.class))).thenAnswer(inv -> inv.getArgument(0));

        taskService.softDeleteSubTask(customer.getId(), 10L, 1001L);

        assertFalse(st.isActive());
        assertNotNull(st.getDeletedAt());
        verify(subTaskRepository).save(any(SubTask.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    @Test
    void addComment_customer_onOwnTask_shouldSaveAndLog() {
        stubActor(customer);

        Task existing = task(10L, admin, customer);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));

        when(commentRepository.save(any(TaskComment.class))).thenAnswer(inv -> {
            TaskComment c = inv.getArgument(0);
            c.setId(2001L);
            return c;
        });

        TaskDtos.CreateCommentRequest req = new TaskDtos.CreateCommentRequest("Hello");

        TaskDtos.CommentResponse res = taskService.addComment(customer.getId(), 10L, req);

        assertEquals(2001L, res.id());
        assertEquals("Hello", res.content());
        verify(commentRepository).save(any(TaskComment.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    @Test
    void updateStatus_customer_onOwnTask_shouldChangeAndLog() {
        stubActor(customer);

        Task existing = task(10L, admin, customer);
        existing.setStatus(TaskStatus.TODO);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskDtos.TaskSummaryResponse res = taskService.updateStatus(customer.getId(), 10L, TaskStatus.IN_PROGRESS);

        assertEquals(TaskStatus.IN_PROGRESS, res.status());
        verify(taskRepository).save(any(Task.class));
        verify(taskLogRepository, atLeastOnce()).save(any(TaskLog.class));
    }

    @Test
    void listTasks_customer_shouldReturnPage() {
        stubActor(customer);

        Pageable pageable = PageRequest.of(0, 10, Sort.by("updatedAt").descending());

        Task t = task(10L, admin, customer);
        Page<Task> page = new PageImpl<>(List.of(t), pageable, 1);

        when(taskRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<TaskDtos.TaskSummaryResponse> res = taskService.listTasks(
                customer.getId(),
                "Task",
                null,
                null,
                null,
                null,
                null,
                null,
                pageable
        );

        assertEquals(1, res.getTotalElements());
        assertEquals(10L, res.getContent().get(0).id());
    }

    // =========================
    // ACCESS CONTROL NEGATIVE
    // =========================

    @Test
    void customer_accessOtherPeoplesTask_shouldThrowForbidden() {
        stubActor(customer);

        Task existing = task(10L, admin, otherCustomer); // customer không phải assignee/createdBy
        when(taskRepository.findById(10L)).thenReturn(Optional.of(existing));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> taskService.getTaskDetail(customer.getId(), 10L));

        assertEquals("FORBIDDEN", ex.getMessage());
    }

    // =========================
    // helpers
    // =========================

    private void stubActor(User actor) {
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
    }

    private static User user(Long id, String email, String fullName, Role role) {
        return User.builder()
                .id(id)
                .email(email)
                .fullName(fullName)
                .role(role)
                .isActive(true)
                .emailVerified(true)
                .passwordHash("HASH")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static Task task(Long id, User createdBy, User assignee) {
        return Task.builder()
                .id(id)
                .title("Task " + id)
                .description("Desc")
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.TODO)
                .dueDate(LocalDate.of(2025, 12, 30))
                .tags(new HashSet<>(Set.of("backend")))
                .createdBy(createdBy)
                .assignee(assignee)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static SubTask subTask(Task task, Long id, String title, boolean done) {
        return SubTask.builder()
                .id(id)
                .task(task)
                .title(title)
                .done(done)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static TaskComment comment(Task task, Long id, User author, String content) {
        return TaskComment.builder()
                .id(id)
                .task(task)
                .author(author)
                .content(content)
                .createdAt(Instant.now())
                .build();
    }

    private static TaskLog log(Task task, Long id, User actor, TaskLogAction action, String fieldName) {
        return TaskLog.builder()
                .id(id)
                .task(task)
                .actor(actor)
                .action(action)
                .fieldName(fieldName)
                .oldValue(null)
                .newValue(null)
                .createdAt(Instant.now())
                .build();
    }
}
