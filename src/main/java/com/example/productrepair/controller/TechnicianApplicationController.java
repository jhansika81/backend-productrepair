package com.example.productrepair.controller;

import com.example.productrepair.dto.TechnicianApplicationResponse;
import com.example.productrepair.entity.*;
import com.example.productrepair.repository.TechnicianApplicationEventRepository;
import com.example.productrepair.repository.TechnicianApplicationRepository;
import com.example.productrepair.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/technician-applications")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class TechnicianApplicationController {
    private final TechnicianApplicationRepository applications;
    private final TechnicianApplicationEventRepository events;
    private final UserRepository users;
    private final Path uploadDirectory;

    public TechnicianApplicationController(TechnicianApplicationRepository applications,
            TechnicianApplicationEventRepository events, UserRepository users,
            @Value("${repaircare.upload-dir:uploads/technician-documents}") String uploadDirectory) {
        this.applications = applications;
        this.events = events;
        this.users = users;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @GetMapping
    public List<TechnicianApplicationResponse> getApplications(
            @RequestParam(required = false) TechnicianApplicationStatus status) {
        List<TechnicianApplication> results = status == null
                ? applications.findAll()
                : applications.findByStatusOrderBySubmittedAtAsc(status);
        return results.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public TechnicianApplicationResponse getApplication(@PathVariable Long id) {
        return toResponse(findApplication(id));
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<Resource> getDocument(@PathVariable Long id) throws Exception {
        TechnicianApplication application = findApplication(id);
        if (application.getDocumentStorageKey() == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = uploadDirectory.resolve(application.getDocumentStorageKey()).normalize();
        if (!file.startsWith(uploadDirectory) || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + application.getDocumentName().replace("\"", "") + "\"")
                .body(new FileSystemResource(file));
    }

    @PostMapping("/{id}/approve")
    @Transactional
    public TechnicianApplicationResponse approve(@PathVariable Long id, Authentication authentication) {
        TechnicianApplication application = findApplication(id);
        if (application.getStatus() != TechnicianApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending applications can be approved");
        }
        application.setStatus(TechnicianApplicationStatus.APPROVED);
        application.setRejectionReason(null);
        applications.save(application);
        events.save(new TechnicianApplicationEvent(application, admin(authentication),
                TechnicianApplicationStatus.APPROVED, null));
        return toResponse(application);
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public TechnicianApplicationResponse reject(@PathVariable Long id,
            @RequestBody RejectionRequest request, Authentication authentication) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("A rejection reason is required");
        }
        if (request.reason().trim().length() > 1000) {
            throw new IllegalArgumentException("Rejection reason must be 1000 characters or fewer");
        }
        TechnicianApplication application = findApplication(id);
        if (application.getStatus() != TechnicianApplicationStatus.PENDING) {
            throw new IllegalArgumentException("Only pending applications can be rejected");
        }
        application.setStatus(TechnicianApplicationStatus.REJECTED);
        application.setRejectionReason(request.reason().trim());
        applications.save(application);
        events.save(new TechnicianApplicationEvent(application, admin(authentication),
                TechnicianApplicationStatus.REJECTED, request.reason().trim()));
        return toResponse(application);
    }

    private TechnicianApplication findApplication(Long id) {
        return applications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Technician application not found"));
    }

    private User admin(Authentication authentication) {
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Administrator not found"));
    }

    private TechnicianApplicationResponse toResponse(TechnicianApplication application) {
        User user = application.getUser();
        LocalDateTime approvedAt = events.findFirstByApplication_IdAndStatusOrderByChangedAtDesc(
                application.getId(), TechnicianApplicationStatus.APPROVED)
                .map(TechnicianApplicationEvent::getChangedAt)
                .orElse(null);
        LocalDateTime rejectedAt = events.findFirstByApplication_IdAndStatusOrderByChangedAtDesc(
                application.getId(), TechnicianApplicationStatus.REJECTED)
                .map(TechnicianApplicationEvent::getChangedAt)
                .orElse(null);
        return new TechnicianApplicationResponse(application.getId(), user.getId(), user.getName(),
                user.getEmail(), user.getPhone(), user.getAddress(), application.getServiceLocation(),
                application.getSkills(), application.getYearsExperience(), application.getQualifications(),
                application.getStatus(), application.getRejectionReason(), application.getDocumentName(),
                application.getSubmittedAt(), approvedAt, rejectedAt);
    }

    public record RejectionRequest(String reason) {}
}
