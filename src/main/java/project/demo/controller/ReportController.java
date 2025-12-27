package project.demo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import project.demo.security.CustomUserDetails;
import project.demo.service.ReportService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/tasks/export")
    public ResponseEntity<byte[]> exportMyTasks(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) throw new RuntimeException("UNAUTHORIZED");

        byte[] bytes = reportService.exportMyTasks(principal.getId());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tasks.xlsx")
                .body(bytes);
    }
}
