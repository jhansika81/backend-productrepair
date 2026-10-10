package com.example.productrepair.controller;

import com.example.productrepair.entity.Product;
import com.example.productrepair.entity.User;
import com.example.productrepair.repository.ProductRepository;
import com.example.productrepair.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/warranty")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class WarrantyController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public WarrantyController(ProductRepository productRepository,
                              UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/user/{userId}")
    public List<Product> getWarrantyDetails(@PathVariable Long userId, Authentication authentication) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getEmail().equals(authentication.getName())) {
            throw new RuntimeException("You may only view your own warranty details");
        }

        List<Product> products = productRepository.findByUser(user);

        LocalDate today = LocalDate.now();

        for (Product product : products) {

            if (product.getPurchaseDate() != null &&
                product.getWarrantyPeriod() != null) {

                LocalDate warrantyEndDate = product.getPurchaseDate()
                        .plusMonths(product.getWarrantyPeriod());

                if (today.isAfter(warrantyEndDate)) {
                    product.setWarrantyStatus("EXPIRED");
                } else {
                    product.setWarrantyStatus("ACTIVE");
                }

                productRepository.save(product);
            }
        }

        return products;
    }
}