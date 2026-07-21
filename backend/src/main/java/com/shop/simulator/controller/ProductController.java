package com.shop.simulator.controller;

import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.RestockOrder;
import com.shop.simulator.dto.RestockRequest;
import com.shop.simulator.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@CrossOrigin(origins = "*")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        return new ResponseEntity<>(productService.createProduct(product), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @RequestBody Product productDetails) {
        return ResponseEntity.ok(productService.updateProduct(id, productDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/restock")
    public ResponseEntity<RestockOrder> triggerRestock(@RequestBody RestockRequest request) {
        RestockOrder order = productService.triggerRestock(request.productId(), request.quantity());
        return new ResponseEntity<>(order, HttpStatus.CREATED);
    }

    @PostMapping("/restock/{id}/deliver")
    public ResponseEntity<RestockOrder> deliverRestock(@PathVariable Long id) {
        return ResponseEntity.ok(productService.deliverRestock(id));
    }

    @GetMapping("/restock/orders")
    public List<RestockOrder> getAllRestockOrders() {
        return productService.getAllRestockOrders();
    }
}
