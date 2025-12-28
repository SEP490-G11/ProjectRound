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
    @Mock TaskCommentRepository commentRepository;
    @Mock NotificationService notificationService;

    @InjectMocks TaskService taskService;

    private final User admin = user(1L, Role.ADMIN);
    private final User customer = user(2L, Role.CUSTOMER);
    private final User other = user(3L, Role.CUSTOMER);

    // =========================
    // CREATE TASK
    // =========================

    @Test
    void createTask_admin_success() {
        stubUser(admin);

        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(10L);
            return t;
        });

        var req = new TaskDtos.CreateTaskRequest(
                "Task", "desc", TaskPriority.HIGH,
                LocalDate.now(), Set.of("backend"), null
        );

        var res = taskService.createTask(admin.getId(), req);

        assertEquals(10L, res.id());
        verify(taskLogRepository, atLeastOnce()).save(any());
    }

    @Test
    void createTask_customer_forbidden() {
        stubUser(customer);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> taskService.createTask(customer.getId(),
                        new TaskDtos.CreateTaskRequest("x", null, null, null, null, null)));

        assertEquals("FORBIDDEN", ex.getMessage());
    }

    // =========================
    // PATCH TASK
    // =========================

    @Test
    void patchTask_notFound() {
        stubUser(admin);
        when(taskRepository.findById(10L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> taskService.patchTask(admin.getId(), 10L,
                        new TaskDtos.PatchTaskRequest("x", null, null, null, null)));

        assertEquals("TASK_NOT_FOUND", ex.getMessage());
    }

    @Test
    void patchTask_admin_success() {
        stubUser(admin);
        Task task = task(10L, admin, customer);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = taskService.patchTask(admin.getId(), 10L,
                new TaskDtos.PatchTaskRequest("New", "Desc", null, null, null));

        assertEquals("New", res.title());
        verify(taskLogRepository, atLeastOnce()).save(any());

    }

    // =========================
    // DELETE TASK
    // =========================

    @Test
    void softDeleteTask_notFound() {
        stubUser(admin);
        when(taskRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> taskService.softDeleteTask(admin.getId(), 10L));
    }

    @Test
    void softDeleteTask_admin_success() {
        stubUser(admin);
        Task task = task(10L, admin, customer);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskService.softDeleteTask(admin.getId(), 10L);

        assertFalse(task.isActive());
        verify(taskLogRepository, atLeastOnce()).save(any());

    }

    // =========================
    // ASSIGN TASK
    // =========================

    @Test
    void assignTask_assigneeNotFound() {
        stubUser(admin);
        Task task = task(10L, admin, null);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> taskService.assignTask(admin.getId(), 10L, 99L));

        assertEquals("USER_NOT_FOUND", ex.getMessage());
    }

    @Test
    void assignTask_admin_success() {
        stubUser(admin);
        stubUser(customer);

        Task task = task(10L, admin, null);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = taskService.assignTask(admin.getId(), 10L, customer.getId());

        assertEquals(customer.getId(), res.assignee().id());
        verify(taskLogRepository, atLeastOnce()).save(any());

    }

    // =========================
    // GET DETAIL
    // =========================

    @Test
    void getTaskDetail_forbidden() {
        stubUser(customer);
        Task task = task(10L, admin, other);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> taskService.getTaskDetail(customer.getId(), 10L));

        assertEquals("FORBIDDEN", ex.getMessage());
    }

    @Test
    void getTaskDetail_success() {
        stubUser(customer);
        Task task = task(10L, admin, customer);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(subTaskRepository.findAllByTaskIdAndActiveTrue(10L)).thenReturn(List.of());
        when(commentRepository.findAllByTaskIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());
        when(taskLogRepository.findAllByTaskIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        var res = taskService.getTaskDetail(customer.getId(), 10L);

        assertEquals(10L, res.task().id());
    }

    // =========================
    // LIST TASKS
    // =========================

    @Test
    void listTasks_withFilters_shouldHitSpecBranches() {
        stubUser(customer);

        Pageable pageable = PageRequest.of(0, 10);
        when(taskRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(Page.empty());

        taskService.listTasks(
                customer.getId(),              // actorId
                "Task",                        // q
                TaskStatus.TODO,               // status
                TaskPriority.HIGH,             // priority
                customer.getId(),              // assigneeId
                LocalDate.now().minusDays(1),  // dueFrom
                LocalDate.now().plusDays(1),   // dueTo
                "backend",                     // tag
                pageable
        );

        verify(taskRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void listTasks_noFilters_shouldWork() {
        stubUser(customer);

        Pageable pageable = PageRequest.of(0, 10);
        when(taskRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(Page.empty());

        taskService.listTasks(
                customer.getId(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                pageable
        );

        verify(taskRepository).findAll(any(Specification.class), eq(pageable));
    }

    // =========================
    // HELPERS
    // =========================

    private void stubUser(User u) {
        when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
    }

    private static User user(Long id, Role role) {
        return User.builder()
                .id(id)
                .email("u" + id + "@test.com")
                .fullName("User " + id)
                .role(role)
                .isActive(true)
                .emailVerified(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private static Task task(Long id, User createdBy, User assignee) {
        return Task.builder()
                .id(id)
                .title("Task")
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .createdBy(createdBy)
                .assignee(assignee)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
