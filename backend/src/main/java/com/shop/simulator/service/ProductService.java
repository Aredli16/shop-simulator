package com.shop.simulator.service;

import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.RestockOrder;
import com.shop.simulator.exception.ResourceNotFoundException;
import com.shop.simulator.repository.ProductRepository;
import com.shop.simulator.repository.RestockOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final RestockOrderRepository restockOrderRepository;

    public ProductService(ProductRepository productRepository, RestockOrderRepository restockOrderRepository) {
        this.productRepository = productRepository;
        this.restockOrderRepository = restockOrderRepository;
    }

    public List<Product> getAllProducts() {
        log.debug("Fetching all products");
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        log.debug("Fetching product with ID: {}", id);
        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product with ID {} not found", id);
                    return new ResourceNotFoundException("Product with ID " + id + " not found.");
                });
    }

    public Product createProduct(Product product) {
        Product saved = productRepository.save(product);
        log.info("Product created: ID={}, name={}, sellingPrice={}", saved.getId(), saved.getName(), saved.getSellingPrice());
        return saved;
    }

    public Product updateProduct(Long id, Product productDetails) {
        Product product = getProductById(id);
        product.setName(productDetails.getName());
        product.setCategory(productDetails.getCategory());
        product.setPurchasePrice(productDetails.getPurchasePrice());
        product.setSellingPrice(productDetails.getSellingPrice());
        product.setStockQuantity(productDetails.getStockQuantity());
        product.setRestockThreshold(productDetails.getRestockThreshold());
        Product updated = productRepository.save(product);
        log.info("Product updated: ID={}, name={}, stockQuantity={}", id, updated.getName(), updated.getStockQuantity());
        return updated;
    }

    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        productRepository.delete(product);
        log.info("Product deleted: ID={}, name={}", id, product.getName());
    }

    public RestockOrder triggerRestock(Long productId, int quantity) {
        Product product = getProductById(productId);
        log.info("Triggering restock for product: ID={}, name={}, quantity={}", productId, product.getName(), quantity);
        
        // Create the restock order
        RestockOrder order = new RestockOrder();
        order.setDate(LocalDateTime.now());
        order.setProduct(product);
        order.setQuantityOrdered(quantity);
        order.setTotalCost(product.getPurchasePrice() * quantity);
        order.setStatus("En cours");
        
        RestockOrder savedOrder = restockOrderRepository.save(order);
        log.info("Restock order created: ID={}, totalCost={}", savedOrder.getId(), savedOrder.getTotalCost());
        return savedOrder;
    }

    public RestockOrder deliverRestock(Long restockOrderId) {
        RestockOrder order = restockOrderRepository.findById(restockOrderId)
                .orElseThrow(() -> {
                    log.warn("RestockOrder with ID {} not found", restockOrderId);
                    return new ResourceNotFoundException("RestockOrder with ID " + restockOrderId + " not found.");
                });
        
        if ("En cours".equals(order.getStatus())) {
            Product product = order.getProduct();
            int newStock = product.getStockQuantity() + order.getQuantityOrdered();
            product.setStockQuantity(newStock);
            productRepository.save(product);
            
            order.setStatus("Livré");
            order = restockOrderRepository.save(order);
            log.info("Restock order delivered: ID={}, product={}, newStockQuantity={}", restockOrderId, product.getName(), newStock);
        } else {
            log.warn("Restock order ID={} is already delivered (status={})", restockOrderId, order.getStatus());
        }
        
        return order;
    }

    public List<RestockOrder> getAllRestockOrders() {
        return restockOrderRepository.findAll();
    }

    public void checkAndTriggerAutoRestock(Product product) {
        log.debug("Checking auto-restock threshold for product: {}, stock={}, threshold={}", 
                product.getName(), product.getStockQuantity(), product.getRestockThreshold());
        if (product.getStockQuantity() <= product.getRestockThreshold()) {
            // Check if there is already a restock order pending for this product
            List<RestockOrder> pendingOrders = restockOrderRepository.findByStatus("En cours");
            boolean alreadyPending = pendingOrders.stream()
                    .anyMatch(order -> order.getProduct().getId().equals(product.getId()));
            
            if (!alreadyPending) {
                log.info("Auto-restock triggered: product '{}' has stock {} which is <= threshold {}", 
                        product.getName(), product.getStockQuantity(), product.getRestockThreshold());
                // Trigger an auto-restock of 50 units (default replenishment batch size)
                triggerRestock(product.getId(), 50);
            } else {
                log.debug("Product '{}' needs restock but an order is already pending", product.getName());
            }
        }
    }
}
