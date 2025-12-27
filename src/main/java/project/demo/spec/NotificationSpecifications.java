package project.demo.spec;

import org.springframework.data.jpa.domain.Specification;
import project.demo.entity.Notification;
import project.demo.enums.NotificationType;

import java.time.Instant;

public class NotificationSpecifications {

    public static Specification<Notification> activeOnly() {
        return (root, q, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Notification> recipientId(Long id) {
        return (root, q, cb) -> id == null
                ? cb.conjunction()
                : cb.equal(root.get("recipient").get("id"), id);
    }

    public static Specification<Notification> unreadOnly(Boolean unreadOnly) {
        return (root, q, cb) -> (unreadOnly == null || !unreadOnly)
                ? cb.conjunction()
                : cb.isNull(root.get("readAt"));
    }

    public static Specification<Notification> type(NotificationType type) {
        return (root, q, cb) -> type == null
                ? cb.conjunction()
                : cb.equal(root.get("type"), type);
    }

    public static Specification<Notification> createdFrom(Instant from) {
        return (root, q, cb) -> from == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Notification> createdTo(Instant to) {
        return (root, q, cb) -> to == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
