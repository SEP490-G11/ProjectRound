package project.demo.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import project.demo.entity.Task;
import project.demo.repository.TaskRepository;
import project.demo.spec.TaskSpecifications;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TaskRepository taskRepository;

    public byte[] exportMyTasks(Long userId) {
        // lấy tất cả task đang active mà assignee = userId
        List<Task> tasks = taskRepository.findAll(
                TaskSpecifications.activeOnly().and(TaskSpecifications.assigneeId(userId)),
                Pageable.unpaged()
        ).getContent();

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Tasks");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("Title");
            header.createCell(2).setCellValue("Status");
            header.createCell(3).setCellValue("Priority");
            header.createCell(4).setCellValue("DueDate");
            header.createCell(5).setCellValue("Tags");

            DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_DATE;

            int r = 1;
            for (Task t : tasks) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(t.getId());
                row.createCell(1).setCellValue(t.getTitle());
                row.createCell(2).setCellValue(String.valueOf(t.getStatus()));
                row.createCell(3).setCellValue(String.valueOf(t.getPriority()));
                row.createCell(4).setCellValue(t.getDueDate() == null ? "" : df.format(t.getDueDate()));
                row.createCell(5).setCellValue(String.join(",", t.getTags()));
            }

            for (int i = 0; i <= 5; i++) sheet.autoSizeColumn(i);

            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("EXPORT_EXCEL_FAILED: " + e.getMessage(), e);
        }
    }
}
