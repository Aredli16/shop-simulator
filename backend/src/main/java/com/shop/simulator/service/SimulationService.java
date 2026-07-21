package com.shop.simulator.service;

import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.Transaction;
import com.shop.simulator.domain.RestockOrder;
import com.shop.simulator.dto.SimulationStatus;
import com.shop.simulator.repository.CustomerRepository;
import com.shop.simulator.repository.ProductRepository;
import com.shop.simulator.repository.RestockOrderRepository;
import com.shop.simulator.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final RestockOrderRepository restockOrderRepository;

    private boolean active = false;
    private double initialCapital = 1000.00;

    public SimulationService(ProductRepository productRepository,
                             CustomerRepository customerRepository,
                             TransactionRepository transactionRepository,
                             RestockOrderRepository restockOrderRepository) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.restockOrderRepository = restockOrderRepository;
    }

    public boolean isActive() {
        return active;
    }

    public void startSimulation() {
        this.active = true;
        log.info("Simulation STARTED");
    }

    public void stopSimulation() {
        this.active = false;
        log.info("Simulation STOPPED/PAUSED");
    }

    public void resetSimulation() {
        this.active = false;
        log.info("Simulation RESET requested. Clearing database tables...");
        
        // Clear all data
        customerRepository.deleteAll();
        transactionRepository.deleteAll();
        restockOrderRepository.deleteAll();
        productRepository.deleteAll();

        // Seed default products
        seedDefaultProducts();
        log.info("Simulation RESET complete. Default products seeded.");
    }

    public void seedDefaultProducts() {
        log.info("Seeding default product catalog");
        productRepository.save(new Product(null, "Soda", "Boisson", 0.80, 2.50, 30, 10));
        productRepository.save(new Product(null, "Chips", "Snack", 0.60, 1.80, 25, 8));
        productRepository.save(new Product(null, "Livre", "Culture", 5.00, 12.00, 15, 5));
        productRepository.save(new Product(null, "Console", "High-Tech", 150.00, 299.99, 5, 2));
    }

    public SimulationStatus getStatus() {
        List<Transaction> transactions = transactionRepository.findAll();
        List<RestockOrder> restockOrders = restockOrderRepository.findAll();

        double totalRevenue = transactions.stream()
                .mapToDouble(Transaction::getTotal)
                .sum();
        totalRevenue = Math.round(totalRevenue * 100.0) / 100.0;

        double totalRestockCost = restockOrders.stream()
                .mapToDouble(RestockOrder::getTotalCost)
                .sum();
        totalRestockCost = Math.round(totalRestockCost * 100.0) / 100.0;

        double currentCapital = Math.round((initialCapital + totalRevenue - totalRestockCost) * 100.0) / 100.0;

        log.debug("Simulation status computed: active={}, revenue={}, capital={}, transactions={}, restocks={}", 
                active, totalRevenue, currentCapital, transactions.size(), restockOrders.size());

        return new SimulationStatus(active, totalRevenue, currentCapital, transactions.size(), restockOrders.size());
    }

    public void tick() {
        log.info("Simulation manual TICK executed");
    }
}
