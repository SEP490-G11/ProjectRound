package project.demo.repository;

import org.springframework.data.jpa.repository.*;
import project.demo.entity.Notification;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {
    List<Notification> findAllByRecipientIdAndActiveTrueAndReadAtIsNull(Long recipientId);
}
