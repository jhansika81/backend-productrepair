package com.example.productrepair.repository;

import com.example.productrepair.entity.Product;
import com.example.productrepair.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByUser(User user);
}