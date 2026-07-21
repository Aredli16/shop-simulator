package com.shop.simulator.service;

import com.shop.simulator.domain.Customer;
import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.Transaction;
import com.shop.simulator.exception.SoldeInsuffisantException;
import com.shop.simulator.exception.StockInsuffisantException;
import com.shop.simulator.repository.CustomerRepository;
import com.shop.simulator.repository.ProductRepository;
import com.shop.simulator.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private TransactionService transactionService;

    private Customer customer;
    private Product soda;

    @BeforeEach
    void setUp() {
        customer = new Customer(1L, "Bob PNJ", 10.00);
        soda = new Product(10L, "Soda", "Drink", 0.50, 2.00, 5, 2);
    }

    @Test
    void getAllTransactions_shouldReturnList() {
        when(transactionRepository.findAll()).thenReturn(Collections.emptyList());
        List<Transaction> transactions = transactionService.getAllTransactions();
        assertThat(transactions).isEmpty();
    }

    @Test
    void checkout_whenCartIsEmpty_shouldThrowException() {
        customer.setCart(new ArrayList<>());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> transactionService.checkout(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cart is empty");
    }

    @Test
    void checkout_whenBudgetIsInsufficient_shouldThrowException() {
        customer.setCart(List.of(soda, soda, soda, soda, soda, soda)); // 6 * 2.00 = 12.00, budget is 10.00
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> transactionService.checkout(1L))
                .isInstanceOf(SoldeInsuffisantException.class)
                .hasMessageContaining("insufficient budget");
    }

    @Test
    void checkout_whenStockIsInsufficient_shouldThrowException() {
        customer.setCart(List.of(soda, soda, soda, soda, soda, soda)); // 6 sodas, only 5 in stock. but customer budget is updated to 20 to pass budget check
        customer.setBudget(20.00);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(soda));

        assertThatThrownBy(() -> transactionService.checkout(1L))
                .isInstanceOf(StockInsuffisantException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    void checkout_whenNominal_shouldDeductStockAndComplete() {
        customer.setCart(List.of(soda, soda)); // 2 * 2.00 = 4.00, budget is 10.00
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(soda));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            tx.setId(100L);
            return tx;
        });

        Transaction transaction = transactionService.checkout(1L);

        assertThat(transaction).isNotNull();
        assertThat(transaction.getId()).isEqualTo(100L);
        assertThat(transaction.getTotal()).isEqualTo(4.00);
        assertThat(transaction.getCustomerName()).isEqualTo("Bob PNJ");
        assertThat(soda.getStockQuantity()).isEqualTo(3); // 5 initial - 2 purchased
        
        verify(productRepository, times(1)).save(soda);
        verify(productService, times(1)).checkAndTriggerAutoRestock(soda);
        verify(customerRepository, times(1)).delete(customer);
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }
}
