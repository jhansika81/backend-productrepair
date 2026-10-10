package com.example.productrepair.controller;

import com.example.productrepair.entity.Product;
import com.example.productrepair.entity.Role;
import com.example.productrepair.entity.User;
import com.example.productrepair.repository.ProductRepository;
import com.example.productrepair.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/products")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class ProductController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductController(ProductRepository productRepository,
                             UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/register/{userId}")
    public Product registerProduct(
            @PathVariable Long userId,
            @RequestBody Product product,
            Authentication authentication) {

        if (product == null) {
            throw new RuntimeException("Product details are required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole() != Role.CUSTOMER || !user.getEmail().equals(authentication.getName())) {
            throw new RuntimeException("Only customers can register products");
        }

        product.setId(null);
        product.setUser(user);

        if (product.getName() == null || product.getName().isBlank()) {
            throw new RuntimeException("Product name is required");
        }

        if (product.getWarrantyStatus() == null || product.getWarrantyStatus().isBlank()) {
            product.setWarrantyStatus("ACTIVE");
        }

        return productRepository.save(product);
    }

    @GetMapping("/all")
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/user/{userId}")
    public List<Product> getUserProducts(@PathVariable Long userId, Authentication authentication) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getEmail().equals(authentication.getName())) {
            throw new RuntimeException("You may only view your own products");
        }
        return productRepository.findByUser(user);
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id, Authentication authentication) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (!product.getUser().getEmail().equals(authentication.getName())) {
            throw new RuntimeException("You may only view your own products");
        }
        return product;
    }

    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id, Authentication authentication) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        if (!product.getUser().getEmail().equals(authentication.getName())) {
            throw new RuntimeException("You may only delete your own products");
        }

        productRepository.delete(product);

        return "Product deleted successfully";
    }
}