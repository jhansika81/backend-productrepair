package com.example.productrepair.controller;

import com.example.productrepair.dto.RepairContactResponse;
import com.example.productrepair.dto.RepairMessageResponse;
import com.example.productrepair.entity.*;
import com.example.productrepair.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/repairs")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class RepairController {
    private final RepairRepository repairRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TechnicianApplicationRepository applicationRepository;
    private final RepairMessageRepository messageRepository;
    private final RepairStatusUpdateRepository updateRepository;

    public RepairController(RepairRepository repairRepository, ProductRepository productRepository,
            UserRepository userRepository, TechnicianApplicationRepository applicationRepository,
            RepairMessageRepository messageRepository, RepairStatusUpdateRepository updateRepository) {
        this.repairRepository = repairRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.messageRepository = messageRepository;
        this.updateRepository = updateRepository;
    }

    @PostMapping("/request/{userId}/{productId}")
    @Transactional
    public Repair createRepairRequest(@PathVariable Long userId, @PathVariable Long productId,
            @RequestBody Repair repair, Authentication authentication) {
        User customer = authenticatedUser(authentication);
        if (!customer.getId().equals(userId) || customer.getRole() != Role.CUSTOMER) {
            throw new IllegalArgumentException("Only the signed-in customer may request a repair");
        }
        if (repair == null || blank(repair.getIssue()) || blank(repair.getDescription())
                || blank(repair.getServiceAddress())) {
            throw new IllegalArgumentException("Issue, description, and service address are required");
        }
        if (repair.getIssue().length() > 120 || repair.getDescription().length() > 2000
                || repair.getServiceAddress().length() > 1000) {
            throw new IllegalArgumentException("Issue, description, or service address exceeds its maximum length");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        if (!product.getUser().getId().equals(customer.getId())) {
            throw new IllegalArgumentException("Repairs may only be requested for your own products");
        }
        repair.setId(null);
        repair.setCustomer(customer);
        repair.setProduct(product);
        repair.setRequestDate(LocalDate.now());
        repair.setStatus(RepairStatus.REQUESTED);
        repair.setTechnician(null);
        repair.setPreferredContactMethod(normalizeContactMethod(repair.getPreferredContactMethod()));
        Repair saved = repairRepository.save(repair);
        updateRepository.save(new RepairStatusUpdate(saved, customer, RepairStatus.REQUESTED,
                "Repair request submitted"));
        return saved;
    }

    @GetMapping("/customer/{userId}")
    public List<Repair> getCustomerRepairs(@PathVariable Long userId, Authentication authentication) {
        User customer = authenticatedUser(authentication);
        if (!customer.getId().equals(userId) || customer.getRole() != Role.CUSTOMER) {
            throw new IllegalArgumentException("You may only view your own repair requests");
        }
        return repairRepository.findByCustomer(customer);
    }

    @GetMapping("/technician/{technicianId}")
    public List<Repair> getTechnicianRepairs(@PathVariable Long technicianId, Authentication authentication) {
        User technician = approvedTechnician(authentication);
        if (!technician.getId().equals(technicianId)) {
            throw new IllegalArgumentException("You may only view repairs assigned to you");
        }
        return repairRepository.findByTechnician(technician);
    }

    @GetMapping("/admin/all")
    public List<Repair> getAllRepairs() {
        return repairRepository.findAll();
    }

    @PutMapping("/{id}/assign/{technicianId}")
    @Transactional
    public Repair assignTechnician(@PathVariable Long id, @PathVariable Long technicianId,
            Authentication authentication) {
        if (authenticatedUser(authentication).getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Only administrators may assign technicians");
        }
        Repair repair = findRepair(id);
        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new IllegalArgumentException("Technician not found"));
        TechnicianApplication application = applicationRepository.findByUserId(technicianId)
                .orElseThrow(() -> new IllegalArgumentException("Technician application not found"));
        if (technician.getRole() != Role.TECHNICIAN
                || application.getStatus() != TechnicianApplicationStatus.APPROVED) {
            throw new IllegalArgumentException("Only approved technicians can be assigned repairs");
        }
        repair.setTechnician(technician);
        repair.setStatus(RepairStatus.ASSIGNED);
        Repair saved = repairRepository.save(repair);
        updateRepository.save(new RepairStatusUpdate(saved, authenticatedUser(authentication),
                RepairStatus.ASSIGNED, "Technician assigned"));
        return saved;
    }

    @GetMapping("/{id}")
    public Repair getRepair(@PathVariable Long id, Authentication authentication) {
        Repair repair = findRepair(id);
        authorizeRepairAccess(repair, authentication);
        return repair;
    }

    @GetMapping("/{id}/contact")
    public RepairContactResponse getCustomerContact(@PathVariable Long id, Authentication authentication) {
        Repair repair = findRepair(id);
        User current = authenticatedUser(authentication);
        if (current.getRole() != Role.ADMIN
                && (current.getRole() != Role.TECHNICIAN || repair.getTechnician() == null
                    || !repair.getTechnician().getId().equals(current.getId())
                    || !isApproved(current))) {
            throw new IllegalArgumentException("Customer contact is available only to the assigned approved technician");
        }
        User customer = repair.getCustomer();
        return new RepairContactResponse(repair.getId(), customer.getName(), repair.getIssue(),
                repair.getDescription(), repair.getServiceAddress(), repair.getPreferredContactMethod(),
                customer.getPhone(), customer.getEmail());
    }

    @PutMapping("/{id}/status")
    @Transactional
    public Repair updateStatus(@PathVariable Long id, @RequestParam RepairStatus status,
            @RequestParam(required = false) String note, Authentication authentication) {
        Repair repair = findRepair(id);
        User technician = approvedTechnician(authentication);
        if (repair.getTechnician() == null || !repair.getTechnician().getId().equals(technician.getId())) {
            throw new IllegalArgumentException("Only the assigned technician may update this repair");
        }
        if (status != RepairStatus.IN_PROGRESS && status != RepairStatus.COMPLETED
                && status != RepairStatus.DELIVERED) {
            throw new IllegalArgumentException("Technicians may set a repair to in progress, completed, or delivered");
        }
        repair.setStatus(status);
        Repair saved = repairRepository.save(repair);
        String updateNote = blank(note) ? defaultStatusNote(status) : note.trim();
        updateRepository.save(new RepairStatusUpdate(saved, technician, status, updateNote));
        return saved;
    }

    @GetMapping("/{id}/messages")
    public List<RepairMessageResponse> getMessages(@PathVariable Long id, Authentication authentication) {
        Repair repair = findRepair(id);
        authorizeRepairAccess(repair, authentication);
        return messageRepository.findByRepairIdOrderBySentAtAsc(id).stream()
                .map(message -> new RepairMessageResponse(message.getId(), message.getSender().getName(),
                        message.getSender().getRole().name(), message.getContent(), message.getSentAt()))
                .toList();
    }

    @PostMapping("/{id}/messages")
    public RepairMessageResponse sendMessage(@PathVariable Long id, @RequestBody MessageRequest request,
            Authentication authentication) {
        Repair repair = findRepair(id);
        User sender = authenticatedUser(authentication);
        authorizeRepairAccess(repair, authentication);
        if (request == null || blank(request.content()) || request.content().trim().length() > 2000) {
            throw new IllegalArgumentException("Message must contain 1 to 2000 characters");
        }
        RepairMessage message = new RepairMessage();
        message.setRepair(repair);
        message.setSender(sender);
        message.setContent(request.content().trim());
        message = messageRepository.save(message);
        return new RepairMessageResponse(message.getId(), sender.getName(), sender.getRole().name(),
                message.getContent(), message.getSentAt());
    }

    @GetMapping("/{id}/updates")
    public List<Map<String, Object>> getStatusUpdates(@PathVariable Long id, Authentication authentication) {
        Repair repair = findRepair(id);
        authorizeRepairAccess(repair, authentication);
        return updateRepository.findByRepairIdOrderByUpdatedAtAsc(id).stream()
                .map(update -> Map.<String, Object>of(
                        "status", update.getStatus(),
                        "note", update.getNote(),
                        "updatedAt", update.getUpdatedAt()))
                .toList();
    }

    @DeleteMapping("/{id}")
    public String deleteRepair(@PathVariable Long id, Authentication authentication) {
        if (!authenticatedUser(authentication).getRole().equals(Role.ADMIN)) {
            throw new IllegalArgumentException("Only administrators may delete repair requests");
        }
        if (!repairRepository.existsById(id)) {
            throw new IllegalArgumentException("Repair request not found");
        }
        repairRepository.deleteById(id);
        return "Repair request deleted successfully";
    }

    private User authenticatedUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    private User approvedTechnician(Authentication authentication) {
        User technician = authenticatedUser(authentication);
        if (technician.getRole() != Role.TECHNICIAN || !isApproved(technician)) {
            throw new IllegalArgumentException("An approved technician account is required");
        }
        return technician;
    }

    private boolean isApproved(User user) {
        return applicationRepository.findByUserId(user.getId())
                .map(application -> application.getStatus() == TechnicianApplicationStatus.APPROVED)
                .orElse(false);
    }

    private Repair findRepair(Long id) {
        return repairRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repair request not found"));
    }

    private void authorizeRepairAccess(Repair repair, Authentication authentication) {
        User current = authenticatedUser(authentication);
        if (current.getRole() == Role.ADMIN) return;
        if (current.getRole() == Role.CUSTOMER && repair.getCustomer().getId().equals(current.getId())) return;
        if (current.getRole() == Role.TECHNICIAN && repair.getTechnician() != null
                && repair.getTechnician().getId().equals(current.getId()) && isApproved(current)) return;
        throw new IllegalArgumentException("You are not authorized to access this repair");
    }

    private String normalizeContactMethod(String method) {
        String normalized = blank(method) ? "PHONE" : method.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("PHONE") && !normalized.equals("EMAIL")) {
            throw new IllegalArgumentException("Preferred contact method must be PHONE or EMAIL");
        }
        return normalized;
    }

    private String defaultStatusNote(RepairStatus status) {
        return switch (status) {
            case IN_PROGRESS -> "Repair accepted";
            case COMPLETED -> "Repair completed";
            case DELIVERED -> "Repair delivered";
            default -> status.name();
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record MessageRequest(String content) {}
}
