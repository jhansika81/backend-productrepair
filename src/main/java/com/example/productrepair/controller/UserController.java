package com.example.productrepair.controller;

import com.example.productrepair.entity.Role;
import com.example.productrepair.entity.User;
import com.example.productrepair.repository.ProductRepository;
import com.example.productrepair.repository.RepairRepository;
import com.example.productrepair.repository.TechnicianApplicationRepository;
import com.example.productrepair.repository.UserRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class UserController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final RepairRepository repairRepository;
    private final TechnicianApplicationRepository technicianApplicationRepository;

    public UserController(UserRepository userRepository,
                         ProductRepository productRepository,
                         RepairRepository repairRepository,
                         TechnicianApplicationRepository technicianApplicationRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.repairRepository = repairRepository;
        this.technicianApplicationRepository = technicianApplicationRepository;
    }

    @GetMapping("/all")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/customers")
    public List<User> getCustomers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.CUSTOMER)
                .toList();
    }

    @GetMapping("/technicians")
    public List<User> getTechnicians() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.TECHNICIAN)
                .filter(user -> technicianApplicationRepository.findByUserId(user.getId())
                        .map(application -> application.getStatus() == com.example.productrepair.entity.TechnicianApplicationStatus.APPROVED)
                        .orElse(false))
                .toList();
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/stats")
    public Map<String, Long> getUserStats() {
        long customers = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.CUSTOMER)
                .count();

        long technicians = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.TECHNICIAN)
                .count();

        long products = productRepository.count();
        long repairs = repairRepository.count();

        Map<String, Long> stats = new HashMap<>();
        stats.put("customers", customers);
        stats.put("technicians", technicians);
        stats.put("products", products);
        stats.put("repairs", repairs);
        return stats;
    }
}
