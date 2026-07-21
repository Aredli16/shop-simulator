package com.shop.simulator.service;

import com.shop.simulator.domain.Customer;
import com.shop.simulator.domain.Product;
import com.shop.simulator.exception.ResourceNotFoundException;
import com.shop.simulator.repository.CustomerRepository;
import com.shop.simulator.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@Transactional
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final Random random = new Random();

    private static final String[] PNJ_NAMES = {
        "Bob", "Alice", "Charlie", "Daisy", "Ethan", "Fiona", "George", "Hannah",
        "Ian", "Julia", "Kevin", "Laura", "Max", "Nora", "Oscar", "Peggy",
        "Quincy", "Rose", "Steve", "Tina", "Victor", "Wendy", "Zack"
    };

    public CustomerService(CustomerRepository customerRepository, ProductRepository productRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    public List<Customer> getAllCustomers() {
        log.debug("Fetching all PNJ customers");
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        log.debug("Fetching PNJ customer with ID: {}", id);
        return customerRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Customer with ID {} not found", id);
                    return new ResourceNotFoundException("Customer with ID " + id + " not found.");
                });
    }

    public Customer createCustomer(Customer customer) {
        Customer saved = customerRepository.save(customer);
        log.info("PNJ Customer created: ID={}, name={}, budget={}", saved.getId(), saved.getName(), saved.getBudget());
        return saved;
    }

    public Customer updateCustomer(Long id, Customer customerDetails) {
        Customer customer = getCustomerById(id);
        customer.setName(customerDetails.getName());
        customer.setBudget(customerDetails.getBudget());
        customer.setCart(customerDetails.getCart());
        Customer updated = customerRepository.save(customer);
        log.info("PNJ Customer updated: ID={}, name={}, cartSize={}", id, updated.getName(), updated.getCart().size());
        return updated;
    }

    public void deleteCustomer(Long id) {
        Customer customer = getCustomerById(id);
        customerRepository.delete(customer);
        log.info("PNJ Customer deleted (exited store): ID={}, name={}", id, customer.getName());
    }

    public Customer generateCustomer() {
        String name = PNJ_NAMES[random.nextInt(PNJ_NAMES.length)];
        // Budget between 15.0 and 120.0
        double budget = Math.round((15.0 + random.nextDouble() * 105.0) * 100.0) / 100.0;

        Customer customer = new Customer();
        customer.setName(name);
        customer.setBudget(budget);

        List<Product> allProducts = productRepository.findAll();
        if (!allProducts.isEmpty()) {
            // Pick a random number of products for the cart (1 to 4 products)
            int itemCount = 1 + random.nextInt(Math.min(allProducts.size(), 4));
            List<Product> cartProducts = new ArrayList<>();
            
            // Shuffle and pick
            List<Product> copy = new ArrayList<>(allProducts);
            Collections.shuffle(copy);
            
            for (int i = 0; i < itemCount; i++) {
                cartProducts.add(copy.get(i));
            }
            customer.setCart(cartProducts);
        }

        Customer saved = customerRepository.save(customer);
        log.info("Generated new PNJ Customer: ID={}, name={}, budget={}, cartSize={}", 
                saved.getId(), saved.getName(), saved.getBudget(), saved.getCart().size());
        return saved;
    }
}
