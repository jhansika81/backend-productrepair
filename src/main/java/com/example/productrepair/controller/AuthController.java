package com.example.productrepair.controller;

import com.example.productrepair.dto.UserResponse;
import com.example.productrepair.entity.*;
import com.example.productrepair.repository.TechnicianApplicationRepository;
import com.example.productrepair.repository.TechnicianApplicationEventRepository;
import com.example.productrepair.repository.UserRepository;
import com.example.productrepair.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AuthController {
    private static final long MAX_DOCUMENT_SIZE = 5 * 1024 * 1024;

    private final UserRepository userRepository;
    private final TechnicianApplicationRepository applicationRepository;
    private final TechnicianApplicationEventRepository applicationEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Path uploadDirectory;

    public AuthController(UserRepository userRepository,
                          TechnicianApplicationRepository applicationRepository,
                          TechnicianApplicationEventRepository applicationEventRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          @Value("${repaircare.upload-dir:uploads/technician-documents}") String uploadDirectory) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.applicationEventRepository = applicationEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @PostMapping("/register")
    public UserResponse register(@RequestBody User registration) {
        if (registration == null || blank(registration.getName()) || blank(registration.getEmail())
                || blank(registration.getPassword()) || blank(registration.getPhone())) {
            throw new IllegalArgumentException("Name, email, phone, and password are required");
        }
        if (registration.getRole() != null && registration.getRole() != Role.CUSTOMER) {
            throw new IllegalArgumentException("Technician accounts must use the technician application form");
        }
        validateLength(registration.getName(), 150, "Name");
        validateLength(registration.getPhone(), 40, "Phone number");
        String email = normalizeEmail(registration.getEmail());
        validateLength(email, 320, "Email address");
        ensureEmailAvailable(email);
        validatePassword(registration.getPassword());

        User customer = new User();
        customer.setName(registration.getName().trim());
        customer.setEmail(email);
        customer.setPhone(registration.getPhone().trim());
        customer.setPassword(passwordEncoder.encode(registration.getPassword()));
        customer.setRole(Role.CUSTOMER);
        return response(userRepository.save(customer), null);
    }

    @PostMapping(value = "/apply", consumes = "multipart/form-data")
    @Transactional
    public ResponseEntity<Map<String, String>> apply(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String password,
            @RequestParam String address,
            @RequestParam String serviceLocation,
            @RequestParam String skills,
            @RequestParam int yearsExperience,
            @RequestParam(required = false) String qualifications,
            @RequestParam(required = false) MultipartFile document) throws IOException {
        if (blank(name) || blank(email) || blank(phone) || blank(password) || blank(address)
                || blank(serviceLocation) || blank(skills)) {
            throw new IllegalArgumentException("All technician application fields except qualifications and identity document are required");
        }
        validateLength(name, 150, "Name");
        validateLength(phone, 40, "Phone number");
        validateLength(address, 1000, "Address");
        validateLength(serviceLocation, 1500, "Service location");
        validateLength(skills, 2000, "Technical skills");
        validateLength(qualifications, 2000, "Qualifications");
        if (yearsExperience < 0 || yearsExperience > 60) {
            throw new IllegalArgumentException("Years of experience must be between 0 and 60");
        }
        validatePassword(password);
        String normalizedEmail = normalizeEmail(email);
        validateLength(normalizedEmail, 320, "Email address");
        ensureEmailAvailable(normalizedEmail);

        User technician = new User();
        technician.setName(name.trim());
        technician.setEmail(normalizedEmail);
        technician.setPhone(phone.trim());
        technician.setAddress(address.trim());
        technician.setPassword(passwordEncoder.encode(password));
        technician.setRole(Role.TECHNICIAN);
        technician = userRepository.save(technician);

        TechnicianApplication application = new TechnicianApplication();
        application.setUser(technician);
        application.setServiceLocation(serviceLocation.trim());
        application.setSkills(skills.trim());
        application.setYearsExperience(yearsExperience);
        application.setQualifications(qualifications == null ? null : qualifications.trim());
        if (document != null && !document.isEmpty()) {
            validateDocument(document);
            String storageKey = UUID.randomUUID().toString() + allowedExtension(document.getOriginalFilename());
            Files.createDirectories(uploadDirectory);
            Files.copy(document.getInputStream(), uploadDirectory.resolve(storageKey));
            application.setDocumentStorageKey(storageKey);
            application.setDocumentName(safeFilename(document.getOriginalFilename()));
        }
        applicationRepository.save(application);
        applicationEventRepository.save(new TechnicianApplicationEvent(application, technician,
                TechnicianApplicationStatus.PENDING, "Application submitted"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Your technician application has been submitted and is awaiting verification."));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody User credentials) {
        User user = authenticatedCredentials(credentials);
        TechnicianApplication application = applicationRepository.findByUserId(user.getId()).orElse(null);
        if (user.getRole() == Role.TECHNICIAN) {
            if (application == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Technician application status could not be verified. Contact support.");
            }
            if (application.getStatus() == TechnicianApplicationStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Your technician application is pending admin approval.");
            }
            if (application.getStatus() == TechnicianApplicationStatus.REJECTED) {
                String message = "Your technician application was rejected.";
                if (!blank(application.getRejectionReason())) {
                    message += " Reason: " + application.getRejectionReason();
                }
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
            }
        }

        return new LoginResponse(jwtService.generateToken(user), response(user, application));
    }

    @PostMapping("/application-status")
    public ApplicationStatusResponse applicationStatus(@RequestBody User credentials) {
        User user = authenticatedCredentials(credentials);
        if (user.getRole() != Role.TECHNICIAN) {
            throw new BadCredentialsException("Invalid email or password");
        }
        TechnicianApplication application = applicationRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Technician application not found"));
        return new ApplicationStatusResponse(application.getStatus(), application.getRejectionReason());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        SecurityContextHolder.clearContext();
        jwtService.revokeToken(authorization.substring(7).trim());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        User user = currentUser(authentication);
        return response(user, applicationRepository.findByUserId(user.getId()).orElse(null));
    }

    @PostMapping("/reapply")
    @Transactional
    public UserResponse reapply(Authentication authentication) {
        User user = currentUser(authentication);
        if (user.getRole() != Role.TECHNICIAN) {
            throw new IllegalArgumentException("Only rejected technician applicants may reapply");
        }
        TechnicianApplication application = applicationRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Technician application not found"));
        if (application.getStatus() != TechnicianApplicationStatus.REJECTED) {
            throw new IllegalArgumentException("Only rejected applications may be resubmitted");
        }
        application.setStatus(TechnicianApplicationStatus.PENDING);
        application.setRejectionReason(null);
        application.setSubmittedAt(java.time.LocalDateTime.now());
        applicationRepository.save(application);
        applicationEventRepository.save(new TechnicianApplicationEvent(application, user,
                TechnicianApplicationStatus.PENDING, "Applicant resubmitted application"));
        return response(user, application);
    }

    @PutMapping("/profile")
    public ProfileUpdateResponse updateProfile(@RequestBody ProfileUpdate update, Authentication authentication) {
        if (update == null || blank(update.phone()) || blank(update.email())) {
            throw new IllegalArgumentException("Email and phone are required");
        }
        validateLength(update.phone(), 40, "Phone number");
        User user = currentUser(authentication);
        String email = normalizeEmail(update.email());
        validateLength(email, 320, "Email address");
        userRepository.findByEmail(email).filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(existing -> { throw new IllegalArgumentException("Email already registered"); });
        user.setPhone(update.phone().trim());
        user.setEmail(email);
        userRepository.save(user);
        UserResponse updatedUser = response(user, applicationRepository.findByUserId(user.getId()).orElse(null));
        return new ProfileUpdateResponse(jwtService.generateToken(user), updatedUser);
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));
    }

    private User authenticatedCredentials(User credentials) {
        String email = credentials == null || credentials.getEmail() == null
                ? "" : normalizeEmail(credentials.getEmail());
        if (credentials == null || email.isEmpty() || credentials.getPassword() == null
                || credentials.getPassword().isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }
        if (credentials.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be 72 UTF-8 bytes or fewer");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(credentials.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return user;
    }

    private UserResponse response(User user, TechnicianApplication application) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(),
                user.getRole(), application == null ? null : application.getStatus(),
                application == null ? null : application.getRejectionReason());
    }

    public record ApplicationStatusResponse(TechnicianApplicationStatus status, String rejectionReason) {}
    public record LoginResponse(String token, UserResponse user) {}
    public record ProfileUpdateResponse(String token, UserResponse user) {}

    private void ensureEmailAvailable(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
    }

    private void validatePassword(String password) {
        int passwordBytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (passwordBytes < 8 || passwordBytes > 72) {
            throw new IllegalArgumentException("Password must be 8 to 72 UTF-8 bytes");
        }
    }

    private void validateLength(String value, int maximum, String field) {
        if (value != null && value.trim().length() > maximum) {
            throw new IllegalArgumentException(field + " must be " + maximum + " characters or fewer");
        }
    }

    private void validateDocument(MultipartFile document) throws IOException {
        if (document.getSize() > MAX_DOCUMENT_SIZE) {
            throw new IllegalArgumentException("Identity document must be 5 MB or smaller");
        }
        String extension = allowedExtension(document.getOriginalFilename());
        String contentType = document.getContentType();
        boolean typeMatches = (extension.equals(".pdf") && "application/pdf".equalsIgnoreCase(contentType))
                || (extension.equals(".png") && "image/png".equalsIgnoreCase(contentType))
                || (extension.equals(".jpg") && ("image/jpeg".equalsIgnoreCase(contentType)
                    || "image/jpg".equalsIgnoreCase(contentType)))
                || (extension.equals(".jpeg") && ("image/jpeg".equalsIgnoreCase(contentType)
                    || "image/jpg".equalsIgnoreCase(contentType)));
        if (!typeMatches) {
            throw new IllegalArgumentException("Identity document must be a PDF, PNG, or JPEG file");
        }
        byte[] header = document.getInputStream().readNBytes(8);
        boolean validSignature = (extension.equals(".pdf") && startsWith(header, "%PDF-".getBytes()))
                || (extension.equals(".png") && startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47}))
                || ((extension.equals(".jpg") || extension.equals(".jpeg"))
                    && header.length >= 3 && header[0] == (byte) 0xff && header[1] == (byte) 0xd8 && header[2] == (byte) 0xff);
        if (!validSignature) {
            throw new IllegalArgumentException("Identity document content does not match its file type");
        }
    }

    private String allowedExtension(String filename) {
        if (filename == null) throw new IllegalArgumentException("Identity document filename is missing");
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> ".pdf";
            case "png" -> ".png";
            case "jpg" -> ".jpg";
            case "jpeg" -> ".jpeg";
            default -> throw new IllegalArgumentException("Identity document must be a PDF, PNG, or JPEG file");
        };
    }

    private String safeFilename(String filename) {
        return Path.of(filename).getFileName().toString();
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        return normalized;
    }

    public record ProfileUpdate(String email, String phone) {}
}
