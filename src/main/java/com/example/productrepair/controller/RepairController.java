
package com.example.productrepair.controller;

import com.example.productrepair.entity.Product;
import com.example.productrepair.entity.Repair;
import com.example.productrepair.entity.RepairStatus;
import com.example.productrepair.entity.User;
import com.example.productrepair.repository.ProductRepository;
import com.example.productrepair.repository.RepairRepository;
import com.example.productrepair.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/repairs")
@CrossOrigin(origins = "http://localhost:5173")
public class RepairController {

    private final RepairRepository repairRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public RepairController(
            RepairRepository repairRepository,
            ProductRepository productRepository,
            UserRepository userRepository) {
        this.repairRepository = repairRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/request/{userId}/{productId}")
    public Repair createRepairRequest(
            @PathVariable Long userId,
            @PathVariable Long productId,
            @RequestBody Repair repair) {

        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        repair.setCustomer(customer);
        repair.setProduct(product);
        repair.setRequestDate(LocalDate.now());
        repair.setStatus(RepairStatus.REQUESTED);

        return repairRepository.save(repair);
    }

    @GetMapping("/customer/{userId}")
    public List<Repair> getCustomerRepairs(@PathVariable Long userId) {

        User customer = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        return repairRepository.findByCustomer(customer);
    }

    @GetMapping("/technician/{technicianId}")
    public List<Repair> getTechnicianRepairs(
            @PathVariable Long technicianId) {

        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new RuntimeException("Technician not found"));

        if (technician.getRole() !=
                com.example.productrepair.entity.Role.TECHNICIAN) {
            throw new RuntimeException("User is not a technician");
        }

        return repairRepository.findByTechnician(technician);
    }

    @GetMapping("/admin/all")
    public List<Repair> getAllRepairs() {
        return repairRepository.findAll();
    }

    @PutMapping("/{id}/assign/{technicianId}")
    public Repair assignTechnician(
            @PathVariable Long id,
            @PathVariable Long technicianId) {

        Repair repair = repairRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Repair request not found"));

        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new RuntimeException("Technician not found"));

        if (technician.getRole() !=
                com.example.productrepair.entity.Role.TECHNICIAN) {
            throw new RuntimeException("Selected user is not a technician");
        }

        repair.setTechnician(technician);
        repair.setStatus(RepairStatus.ASSIGNED);

        return repairRepository.save(repair);
    }

    @GetMapping("/{id}")
    public Repair getRepair(@PathVariable Long id) {

        return repairRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Repair request not found"));
    }

    @PutMapping("/{id}/status")
    public Repair updateStatus(
            @PathVariable Long id,
            @RequestParam RepairStatus status) {

        Repair repair = repairRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Repair request not found"));

        repair.setStatus(status);

        return repairRepository.save(repair);
    }

    @DeleteMapping("/{id}")
    public String deleteRepair(@PathVariable Long id) {

        if (!repairRepository.existsById(id)) {
            return "Repair request not found";
        }

        repairRepository.deleteById(id);

        return "Repair request deleted successfully";
    }
}
