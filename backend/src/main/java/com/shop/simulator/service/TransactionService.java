package com.shop.simulator.service;

import com.shop.simulator.domain.Customer;
import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.Transaction;
import com.shop.simulator.exception.ResourceNotFoundException;
import com.shop.simulator.exception.SoldeInsuffisantException;
import com.shop.simulator.exception.StockInsuffisantException;
import com.shop.simulator.repository.CustomerRepository;
import com.shop.simulator.repository.ProductRepository;
import com.shop.simulator.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;

    public TransactionService(TransactionRepository transactionRepository,
                              CustomerRepository customerRepository,
                              ProductRepository productRepository,
                              ProductService productService) {
        this.transactionRepository = transactionRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.productService = productService;
    }

    public List<Transaction> getAllTransactions() {
        log.debug("Fetching all transactions");
        return transactionRepository.findAll();
    }

    public Transaction checkout(Long customerId) {
        log.info("Initiating checkout process for customer ID: {}", customerId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> {
                    log.warn("Checkout failed: Customer ID {} not found", customerId);
                    return new ResourceNotFoundException("Customer with ID " + customerId + " not found.");
                });

        List<Product> cart = customer.getCart();
        if (cart.isEmpty()) {
            log.warn("Checkout failed: Customer '{}' has an empty cart", customer.getName());
            throw new IllegalArgumentException("Customer's cart is empty.");
        }

        // Count quantity per product in the cart
        Map<Long, Long> productCounts = cart.stream()
                .collect(Collectors.groupingBy(Product::getId, Collectors.counting()));

        // Calculate total cost
        double totalCost = 0;
        for (Product item : cart) {
            totalCost += item.getSellingPrice();
        }
        // Round to 2 decimal places
        totalCost = Math.round(totalCost * 100.0) / 100.0;

        log.info("Checking budget for customer '{}': budget={}, totalCost={}", customer.getName(), customer.getBudget(), totalCost);
        // Check budget
        if (customer.getBudget() < totalCost) {
            log.warn("Checkout failed: Customer '{}' has insufficient budget. Required: {}, Available: {}",
                    customer.getName(), totalCost, customer.getBudget());
            throw new SoldeInsuffisantException("Customer " + customer.getName() +
                    " has insufficient budget. Required: " + totalCost + ", Available: " + customer.getBudget());
        }

        // Check stock levels
        for (Map.Entry<Long, Long> entry : productCounts.entrySet()) {
            Long productId = entry.getKey();
            Long requestedQty = entry.getValue();
            
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> {
                        log.error("Checkout failed: Product ID {} in cart does not exist", productId);
                        return new ResourceNotFoundException("Product with ID " + productId + " not found.");
                    });
            
            log.debug("Checking stock for '{}': stock={}, requested={}", product.getName(), product.getStockQuantity(), requestedQty);
            if (product.getStockQuantity() < requestedQty) {
                log.warn("Checkout failed: Product '{}' has insufficient stock. Stock: {}, Requested: {}",
                        product.getName(), product.getStockQuantity(), requestedQty);
                throw new StockInsuffisantException("Insufficient stock for product: " + product.getName() +
                        ". Requested: " + requestedQty + ", Available: " + product.getStockQuantity());
            }
        }

        // Deduct stocks and trigger auto-restocks
        List<Product> purchasedItems = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : productCounts.entrySet()) {
            Long productId = entry.getKey();
            int requestedQty = entry.getValue().intValue();
            
            Product product = productRepository.findById(productId).get();
            int oldStock = product.getStockQuantity();
            int newStock = oldStock - requestedQty;
            product.setStockQuantity(newStock);
            productRepository.save(product);
            log.info("Deducted stock for '{}': {} -> {}", product.getName(), oldStock, newStock);
            
            purchasedItems.add(product);
            
            // Check if we need to auto-restock this product
            productService.checkAndTriggerAutoRestock(product);
        }

        // Create transaction
        Transaction transaction = new Transaction();
        transaction.setDate(LocalDateTime.now());
        transaction.setItems(cart);
        transaction.setTotal(totalCost);
        transaction.setCustomerName(customer.getName());
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved: ID={}, customer='{}', total={}", savedTransaction.getId(), savedTransaction.getCustomerName(), totalCost);

        // Delete customer as they have finished their shopping cycle and left the store
        customerRepository.delete(customer);
        log.info("Customer '{}' deleted from active simulation context after successful checkout", customer.getName());

        return savedTransaction;
    }
}
