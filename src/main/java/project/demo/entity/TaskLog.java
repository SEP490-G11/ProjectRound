package project.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import project.demo.enums.TaskLogAction;

import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = PRIVATE)
@Entity
@Table(name = "task_logs", indexes = {
        @Index(name = "idx_logs_task", columnList = "task_id")
})
public class TaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    TaskLogAction action;

    @Column(length = 80)
    String fieldName;

    @Column(columnDefinition = "TEXT")
    String oldValue;

    @Column(columnDefinition = "TEXT")
    String newValue;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
