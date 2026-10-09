package com.example.productrepair.controller;

import com.example.productrepair.entity.Product;
import com.example.productrepair.entity.User;
import com.example.productrepair.repository.ProductRepository;
import com.example.productrepair.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@CrossOrigin(origins = "http://localhost:5173")
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
            @RequestBody Product product) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        product.setUser(user);

        if (product.getWarrantyStatus() == null ||
            product.getWarrantyStatus().isEmpty()) {
            product.setWarrantyStatus("ACTIVE");
        }

        return productRepository.save(product);
    }

    @GetMapping("/user/{userId}")
    public List<Product> getUserProducts(@PathVariable Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return productRepository.findByUser(user);
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {

        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @DeleteMapping("/{id}")
    public String deleteProduct(@PathVariable Long id) {

        if (!productRepository.existsById(id)) {
            return "Product not found";
        }

        productRepository.deleteById(id);

        return "Product deleted successfully";
    }
}