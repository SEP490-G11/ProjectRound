package project.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.demo.entity.SubTask;

import java.util.List;

public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
    List<SubTask> findAllByTaskIdAndActiveTrue(Long taskId);
}
