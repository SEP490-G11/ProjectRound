package project.demo.repository;

import org.springframework.data.jpa.repository.*;
import project.demo.entity.Task;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {
}
