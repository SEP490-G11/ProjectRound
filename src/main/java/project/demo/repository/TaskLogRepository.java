package project.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.demo.entity.TaskLog;

import java.util.List;

public interface TaskLogRepository extends JpaRepository<TaskLog, Long> {
    List<TaskLog> findAllByTaskIdOrderByCreatedAtDesc(Long taskId);
}
