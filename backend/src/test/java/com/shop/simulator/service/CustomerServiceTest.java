package com.shop.simulator.service;

import com.shop.simulator.domain.Customer;
import com.shop.simulator.domain.Product;
import com.shop.simulator.exception.ResourceNotFoundException;
import com.shop.simulator.repository.CustomerRepository;
import com.shop.simulator.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CustomerService customerService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer(1L, "Alice PNJ", 50.0);
    }

    @Test
    void getAllCustomers_shouldReturnList() {
        when(customerRepository.findAll()).thenReturn(List.of(customer));
        List<Customer> customers = customerService.getAllCustomers();
        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).getName()).isEqualTo("Alice PNJ");
    }

    @Test
    void getCustomerById_whenExists_shouldReturnCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        Customer found = customerService.getCustomerById(1L);
        assertThat(found.getName()).isEqualTo("Alice PNJ");
    }

    @Test
    void getCustomerById_whenDoesNotExist_shouldThrowException() {
        when(customerRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customerService.getCustomerById(2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer with ID 2 not found");
    }

    @Test
    void createCustomer_shouldSaveAndReturn() {
        when(customerRepository.save(customer)).thenReturn(customer);
        Customer saved = customerService.createCustomer(customer);
        assertThat(saved).isNotNull();
        verify(customerRepository, times(1)).save(customer);
    }

    @Test
    void updateCustomer_shouldModifyAndSave() {
        Customer details = new Customer(null, "Updated Bob", 75.0);
        details.setCart(List.of(new Product()));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer updated = customerService.updateCustomer(1L, details);

        assertThat(updated.getName()).isEqualTo("Updated Bob");
        assertThat(updated.getBudget()).isEqualTo(75.0);
        assertThat(updated.getCart()).hasSize(1);
    }

    @Test
    void deleteCustomer_shouldInvokeRepositoryDelete() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        doNothing().when(customerRepository).delete(customer);

        customerService.deleteCustomer(1L);

        verify(customerRepository, times(1)).delete(customer);
    }

    @Test
    void generateCustomer_whenProductCatalogIsEmpty_shouldCreateWithEmptyCart() {
        when(productRepository.findAll()).thenReturn(new ArrayList<>());
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer generated = customerService.generateCustomer();

        assertThat(generated).isNotNull();
        assertThat(generated.getName()).isNotEmpty();
        assertThat(generated.getBudget()).isBetween(15.0, 120.0);
        assertThat(generated.getCart()).isEmpty();
    }

    @Test
    void generateCustomer_whenProductCatalogHasItems_shouldPopulateCart() {
        Product p1 = new Product(1L, "Soda", "Drink", 0.5, 1.5, 10, 5);
        Product p2 = new Product(2L, "Chips", "Snack", 0.4, 1.2, 20, 5);
        
        when(productRepository.findAll()).thenReturn(List.of(p1, p2));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer generated = customerService.generateCustomer();

        assertThat(generated).isNotNull();
        assertThat(generated.getCart()).isNotEmpty();
        assertThat(generated.getCart().get(0)).isIn(p1, p2);
    }
}
