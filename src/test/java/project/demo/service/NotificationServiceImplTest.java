package project.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import project.demo.dto.NotificationDtos;
import project.demo.entity.Notification;
import project.demo.entity.User;
import project.demo.enums.NotificationType;
import project.demo.repository.NotificationRepository;
import project.demo.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;

    @InjectMocks NotificationServiceImpl service;

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@test.com");
        u.setFullName("User " + id);
        u.setActive(true);
        u.setEmailVerified(true);
        return u;
    }

    @BeforeEach
    void setup() {
        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createNotification_actorNull_shouldSaveWithNullActor() {
        User recipient = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(recipient));

        service.createNotification(1L, null, NotificationType.TASK_ASSIGNED, "msg", 10L);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());

        Notification n = cap.getValue();
        assertEquals(1L, n.getRecipient().getId());
        assertNull(n.getActor());
        assertEquals(NotificationType.TASK_ASSIGNED, n.getType());
        assertEquals("msg", n.getMessage());
        assertEquals(10L, n.getTaskId());
        assertTrue(n.isActive());
    }

    @Test
    void createNotification_withActor_shouldSaveActor() {
        User recipient = user(1L);
        User actor = user(2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(recipient));
        when(userRepository.findById(2L)).thenReturn(Optional.of(actor));

        service.createNotification(1L, 2L, NotificationType.COMMENT_ADDED, "hello", 99L);

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        assertNotNull(cap.getValue().getActor());
        assertEquals(2L, cap.getValue().getActor().getId());
    }

    @Test
    void listMyNotifications_nullFilters_shouldNotThrow() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        when(notificationRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        assertDoesNotThrow(() ->
                service.listMyNotifications(1L, null, null, null, null, pageable)
        );

        verify(notificationRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void markRead_shouldSetReadAt_whenNull() {
        User recipient = user(1L);
        Notification n = new Notification();
        n.setId(10L);
        n.setRecipient(recipient);
        n.setActive(true);
        n.setReadAt(null);

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(n));

        service.markRead(1L, 10L);

        assertNotNull(n.getReadAt());
        verify(notificationRepository).save(n);
    }

    @Test
    void markRead_alreadyRead_shouldNotSaveAgain() {
        User recipient = user(1L);
        Notification n = new Notification();
        n.setId(10L);
        n.setRecipient(recipient);
        n.setActive(true);
        n.setReadAt(Instant.now());

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(n));

        service.markRead(1L, 10L);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAllRead_shouldSetReadAtForAllUnread() {
        User recipient = user(1L);

        Notification n1 = new Notification();
        n1.setId(1L); n1.setRecipient(recipient); n1.setActive(true); n1.setReadAt(null);

        Notification n2 = new Notification();
        n2.setId(2L); n2.setRecipient(recipient); n2.setActive(true); n2.setReadAt(null);

        when(notificationRepository.findAllByRecipientIdAndActiveTrueAndReadAtIsNull(1L))
                .thenReturn(List.of(n1, n2));

        service.markAllRead(1L);

        assertNotNull(n1.getReadAt());
        assertNotNull(n2.getReadAt());
        verify(notificationRepository).saveAll(anyList());
    }
}
