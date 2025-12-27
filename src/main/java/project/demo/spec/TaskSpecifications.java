package project.demo.spec;

import org.springframework.data.jpa.domain.Specification;
import project.demo.entity.Task;
import project.demo.enums.TaskPriority;
import project.demo.enums.TaskStatus;

import java.time.LocalDate;

public class TaskSpecifications {

    public static Specification<Task> activeOnly() {
        return (root, q, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Task> status(TaskStatus status) {
        return (root, q, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Task> priority(TaskPriority priority) {
        return (root, q, cb) -> priority == null ? cb.conjunction() : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Task> assigneeId(Long assigneeId) {
        return (root, q, cb) -> assigneeId == null ? cb.conjunction() : cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<Task> dueFrom(LocalDate from) {
        return (root, q, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("dueDate"), from);
    }

    public static Specification<Task> dueTo(LocalDate to) {
        return (root, q, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("dueDate"), to);
    }

    public static Specification<Task> tag(String tag) {
        return (root, q, cb) -> {
            if (tag == null || tag.isBlank()) return cb.conjunction();
            var join = root.joinSet("tags"); // ElementCollection join
            return cb.equal(join, tag.trim());
        };
    }

    public static Specification<Task> keyword(String qStr) {
        return (root, q, cb) -> {
            if (qStr == null || qStr.isBlank()) return cb.conjunction();
            String like = "%" + qStr.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)
            );
        };
    }
}
