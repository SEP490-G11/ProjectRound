package project.demo.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import project.demo.dto.NotificationDtos;
import project.demo.enums.NotificationType;
import project.demo.security.CustomUserDetails;
import project.demo.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.Instant;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Page<NotificationDtos.NotificationResponse> myNotifications(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationService.listMyNotifications(me.getId(), unreadOnly, type, from, to, pageable);
    }

    @PatchMapping("/{id}/read")
    public void markRead(@AuthenticationPrincipal CustomUserDetails me, @PathVariable Long id) {
        notificationService.markRead(me.getId(), id);
    }

    @PatchMapping("/read-all")
    public void markAllRead(@AuthenticationPrincipal CustomUserDetails me) {
        notificationService.markAllRead(me.getId());
    }
}
