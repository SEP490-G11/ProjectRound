package project.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = PRIVATE)
@Entity
@Table(name = "subtasks", indexes = {
        @Index(name = "idx_subtasks_task", columnList = "task_id"),
        @Index(name = "idx_subtasks_active", columnList = "active")
})
public class SubTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    Task task;

    @Column(nullable = false, length = 200)
    String title;

    @Builder.Default
    @Column(nullable = false)
    boolean done = false;

    @Builder.Default
    @Column(nullable = false)
    boolean active = true;

    Instant deletedAt;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    Instant createdAt = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    Instant updatedAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
