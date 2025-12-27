package project.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.demo.entity.TaskComment;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findAllByTaskIdOrderByCreatedAtAsc(Long taskId);
}
