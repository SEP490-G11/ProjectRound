package project.demo.dto;

import project.demo.entity.Notification;
import project.demo.enums.NotificationType;

import java.time.Instant;

public class NotificationDtos {

    public record NotificationResponse(
            Long id,
            NotificationType type,
            String message,
            Long taskId,
            Instant createdAt,
            Instant readAt
    ) {}

    public static NotificationResponse fromEntity(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getMessage(),
                n.getTaskId(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }
}
