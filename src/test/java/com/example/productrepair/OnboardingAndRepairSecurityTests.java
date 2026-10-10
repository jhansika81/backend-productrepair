package com.example.productrepair;

import com.example.productrepair.controller.AuthController;
import com.example.productrepair.controller.ApiExceptionHandler;
import com.example.productrepair.controller.RepairController;
import com.example.productrepair.controller.TechnicianApplicationController;
import com.example.productrepair.entity.*;
import com.example.productrepair.repository.*;
import com.example.productrepair.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OnboardingAndRepairSecurityTests {
    private UserRepository users;
    private TechnicianApplicationRepository applications;
    private TechnicianApplicationEventRepository applicationEvents;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        applications = mock(TechnicianApplicationRepository.class);
        applicationEvents = mock(TechnicianApplicationEventRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("");
        authController = new AuthController(users, applications, applicationEvents,
                passwordEncoder, jwtService, "target/test-technician-documents");
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(applications.save(any(TechnicianApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publicRegistrationAlwaysCreatesHashedCustomerAccount() {
        when(users.findByEmail("customer@example.com")).thenReturn(Optional.empty());
        User registration = new User("Customer", " CUSTOMER@example.com ", "secure-pass-1", Role.CUSTOMER);
        registration.setPhone("5551234567");

        var response = authController.register(registration);

        assertEquals(Role.CUSTOMER, response.role());
        assertEquals("customer@example.com", response.email());
        verify(users).save(argThat(user -> user.getRole() == Role.CUSTOMER
                && user.getPhone().equals("5551234567")
                && passwordEncoder.matches("secure-pass-1", user.getPassword())
                && !user.getPassword().equals("secure-pass-1")));
    }

    @Test
    void publicRegistrationRejectsDuplicateEmailAndAdministratorRole() {
        User registration = new User("Customer", "customer@example.com", "secure-pass-1", Role.CUSTOMER);
        registration.setPhone("5551234567");
        when(users.findByEmail("customer@example.com")).thenReturn(Optional.of(registration));
        assertThrows(IllegalArgumentException.class, () -> authController.register(registration));

        when(users.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        User adminAttempt = new User("Admin", "admin@example.com", "secure-pass-1", Role.ADMIN);
        adminAttempt.setPhone("5551234567");
        assertThrows(IllegalArgumentException.class, () -> authController.register(adminAttempt));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void pendingTechnicianCannotLogInAndReceivesApprovalMessage() {
        User technician = new User("Technician", "tech@example.com",
                passwordEncoder.encode("secure-pass-1"), Role.TECHNICIAN);
        technician.setId(12L);
        TechnicianApplication application = new TechnicianApplication();
        application.setUser(technician);
        when(users.findByEmail("tech@example.com")).thenReturn(Optional.of(technician));
        when(applications.findByUserId(12L)).thenReturn(Optional.of(application));
        User credentials = new User();
        credentials.setEmail("tech@example.com");
        credentials.setPassword("secure-pass-1");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authController.login(credentials));

        assertEquals(403, exception.getStatusCode().value());
        assertEquals("Your technician application is pending admin approval.", exception.getReason());
    }

    @Test
    void approvedTechnicianCanLogInWithTheOriginalPassword() {
        User technician = new User("Technician", "tech@example.com",
                passwordEncoder.encode("secure-pass-1"), Role.TECHNICIAN);
        technician.setId(12L);
        TechnicianApplication application = new TechnicianApplication();
        application.setUser(technician);
        application.setStatus(TechnicianApplicationStatus.APPROVED);
        when(users.findByEmail("tech@example.com")).thenReturn(Optional.of(technician));
        when(applications.findByUserId(12L)).thenReturn(Optional.of(application));
        User credentials = new User();
        credentials.setEmail(" TECH@example.com ");
        credentials.setPassword("secure-pass-1");
        var response = authController.login(credentials);

        assertEquals(Role.TECHNICIAN, response.user().role());
        assertEquals(TechnicianApplicationStatus.APPROVED, response.user().applicationStatus());
        assertEquals("tech@example.com", jwtService.parseToken(response.token()).email());
        assertEquals(Role.TECHNICIAN, jwtService.parseToken(response.token()).role());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void jwtSessionsAreDistinctAndLoggingOutOneTokenDoesNotRevokeAnother() {
        User technician = new User("Technician", "tech@example.com", "hash", Role.TECHNICIAN);
        User admin = new User("Admin", "admin@example.com", "hash", Role.ADMIN);
        String technicianToken = jwtService.generateToken(technician);
        String adminToken = jwtService.generateToken(admin);

        assertNotEquals(technicianToken, adminToken);
        assertEquals(Role.TECHNICIAN, jwtService.parseToken(technicianToken).role());
        assertEquals(Role.ADMIN, jwtService.parseToken(adminToken).role());

        jwtService.revokeToken(technicianToken);

        assertThrows(IllegalArgumentException.class, () -> jwtService.parseToken(technicianToken));
        assertEquals(Role.ADMIN, jwtService.parseToken(adminToken).role());
    }

    @Test
    void technicianCanCheckApplicationStatusOnlyWithValidCredentials() {
        User technician = new User("Technician", "tech@example.com",
                passwordEncoder.encode("secure-pass-1"), Role.TECHNICIAN);
        technician.setId(12L);
        TechnicianApplication application = new TechnicianApplication();
        application.setUser(technician);
        application.setStatus(TechnicianApplicationStatus.REJECTED);
        application.setRejectionReason("Please provide a valid certificate");
        when(users.findByEmail("tech@example.com")).thenReturn(Optional.of(technician));
        when(applications.findByUserId(12L)).thenReturn(Optional.of(application));
        User credentials = new User();
        credentials.setEmail("tech@example.com");
        credentials.setPassword("secure-pass-1");

        var status = authController.applicationStatus(credentials);

        assertEquals(TechnicianApplicationStatus.REJECTED, status.status());
        assertEquals("Please provide a valid certificate", status.rejectionReason());
        assertThrows(BadCredentialsException.class, () -> {
            User incorrectCredentials = new User();
            incorrectCredentials.setEmail("tech@example.com");
            incorrectCredentials.setPassword("incorrect-password");
            authController.applicationStatus(incorrectCredentials);
        });
    }

    @Test
    void loginRejectsInvalidPassword() {
        User customer = new User("Customer", "customer@example.com",
                passwordEncoder.encode("correct-password"), Role.CUSTOMER);
        when(users.findByEmail("customer@example.com")).thenReturn(Optional.of(customer));
        User credentials = new User();
        credentials.setEmail("customer@example.com");
        credentials.setPassword("incorrect-password");
        assertThrows(BadCredentialsException.class, () -> authController.login(credentials));
    }

    @Test
    void invalidLoginReturnsUnauthorizedStatusAndGenericMessage() {
        var response = new ApiExceptionHandler()
                .handleBadCredentials(new BadCredentialsException("Invalid email or password"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Invalid email or password", response.getBody().get("message"));
    }

    @Test
    void technicianApplicationIsPersistedAsPendingWithAnAuditEntry() throws Exception {
        when(users.findByEmail("tech@example.com")).thenReturn(Optional.empty());

        ResponseEntity<java.util.Map<String, String>> response = authController.apply(
                "Technician", "tech@example.com", "5551234567", "secure-pass-1",
                "1 Main Street", "Central City", "Laptop repairs", 4,
                "Repair certificate", null);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("Your technician application has been submitted and is awaiting verification.",
                response.getBody().get("message"));
        verify(applications).save(argThat(application ->
                application.getStatus() == TechnicianApplicationStatus.PENDING
                        && application.getUser().getRole() == Role.TECHNICIAN
                        && application.getUser().getEmail().equals("tech@example.com")
                        && passwordEncoder.matches("secure-pass-1", application.getUser().getPassword())
                        && application.getYearsExperience() == 4));
        verify(applicationEvents).save(argThat(event -> event.getStatus() == TechnicianApplicationStatus.PENDING
                && event.getChangedBy().getRole() == Role.TECHNICIAN));
    }

    @Test
    void administratorApprovalAndRejectionPersistReasonAndAuditEvents() {
        TechnicianApplicationRepository applicationRepository = mock(TechnicianApplicationRepository.class);
        TechnicianApplicationEventRepository eventRepository = mock(TechnicianApplicationEventRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        User admin = new User("Admin", "admin@example.com", "hash", Role.ADMIN);
        User applicant = new User("Technician", "tech@example.com", "hash", Role.TECHNICIAN);
        TechnicianApplication approvedApplication = new TechnicianApplication();
        approvedApplication.setUser(applicant);
        TechnicianApplication rejectedApplication = new TechnicianApplication();
        rejectedApplication.setUser(new User("Other", "other@example.com", "hash", Role.TECHNICIAN));

        when(applicationRepository.findById(1L)).thenReturn(Optional.of(approvedApplication));
        when(applicationRepository.findById(2L)).thenReturn(Optional.of(rejectedApplication));
        when(applicationRepository.save(any(TechnicianApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        TechnicianApplicationController controller = new TechnicianApplicationController(
                applicationRepository, eventRepository, userRepository, "target/test-technician-documents");
        var adminAuth = authentication("admin@example.com", "ROLE_ADMIN");

        var approved = controller.approve(1L, adminAuth);
        var rejected = controller.reject(2L,
                new TechnicianApplicationController.RejectionRequest("Please provide a valid certificate"),
                adminAuth);

        assertEquals(TechnicianApplicationStatus.APPROVED, approved.status());
        assertEquals(TechnicianApplicationStatus.REJECTED, rejected.status());
        assertEquals("Please provide a valid certificate", rejected.rejectionReason());
        verify(applicationRepository, times(2)).save(any(TechnicianApplication.class));
        verify(userRepository, never()).save(any(User.class));
        verify(eventRepository, times(2)).save(any(TechnicianApplicationEvent.class));
    }

    @Test
    void onlyAdministratorCanAssignAndOnlyAssignedApprovedTechnicianGetsContact() {
        RepairRepository repairs = mock(RepairRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        UserRepository repairUsers = mock(UserRepository.class);
        TechnicianApplicationRepository repairApplications = mock(TechnicianApplicationRepository.class);
        RepairMessageRepository messages = mock(RepairMessageRepository.class);
        RepairStatusUpdateRepository updates = mock(RepairStatusUpdateRepository.class);
        RepairController controller = new RepairController(repairs, products, repairUsers,
                repairApplications, messages, updates);

        User admin = new User("Admin", "admin@example.com", "hash", Role.ADMIN);
        User technician = new User("Technician", "tech@example.com", "hash", Role.TECHNICIAN);
        technician.setId(2L);
        User unrelatedTechnician = new User("Other", "other@example.com", "hash", Role.TECHNICIAN);
        unrelatedTechnician.setId(3L);
        User customer = new User("Customer", "customer@example.com", "hash", Role.CUSTOMER);
        customer.setPhone("5551234567");
        customer.setId(4L);
        Repair repair = new Repair();
        repair.setId(8L);
        repair.setCustomer(customer);
        repair.setIssue("Screen repair");
        repair.setDescription("Cracked display");
        repair.setServiceAddress("1 Main Street");
        repair.setPreferredContactMethod("PHONE");
        TechnicianApplication application = new TechnicianApplication();
        application.setUser(technician);
        application.setStatus(TechnicianApplicationStatus.APPROVED);

        when(repairUsers.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(repairUsers.findByEmail("tech@example.com")).thenReturn(Optional.of(technician));
        when(repairUsers.findByEmail("other@example.com")).thenReturn(Optional.of(unrelatedTechnician));
        when(repairUsers.findById(2L)).thenReturn(Optional.of(technician));
        when(repairApplications.findByUserId(2L)).thenReturn(Optional.of(application));
        when(repairs.findById(8L)).thenReturn(Optional.of(repair));
        when(repairs.save(any(Repair.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var adminAuth = authentication("admin@example.com", "ROLE_ADMIN");
        var technicianAuth = authentication("tech@example.com", "ROLE_TECHNICIAN");
        var unrelatedAuth = authentication("other@example.com", "ROLE_TECHNICIAN");

        controller.assignTechnician(8L, 2L, adminAuth);
        assertSame(technician, repair.getTechnician());
        assertEquals(RepairStatus.ASSIGNED, repair.getStatus());
        assertEquals("5551234567", controller.getCustomerContact(8L, technicianAuth).phone());
        assertThrows(IllegalArgumentException.class, () -> controller.assignTechnician(8L, 2L, technicianAuth));
        assertThrows(IllegalArgumentException.class, () -> controller.getCustomerContact(8L, unrelatedAuth));
        assertThrows(IllegalArgumentException.class, () -> controller.getMessages(8L, unrelatedAuth));
    }

    private UsernamePasswordAuthenticationToken authentication(String email, String authority) {
        return new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority(authority)));
    }
}
