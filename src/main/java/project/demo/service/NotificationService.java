package project.demo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import project.demo.dto.NotificationDtos;
import project.demo.enums.NotificationType;

import java.time.Instant;

public interface NotificationService {

    void createNotification(Long recipientId,
                            Long actorId,
                            NotificationType type,
                            String message,
                            Long taskId);

    Page<NotificationDtos.NotificationResponse> listMyNotifications(
            Long meId,
            Boolean unreadOnly,
            NotificationType type,
            Instant from,
            Instant to,
            Pageable pageable
    );

    void markRead(Long meId, Long notificationId);

    void markAllRead(Long meId);
}
