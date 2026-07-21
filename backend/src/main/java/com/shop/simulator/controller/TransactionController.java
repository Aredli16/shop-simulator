package com.shop.simulator.controller;

import com.shop.simulator.domain.Transaction;
import com.shop.simulator.dto.CheckoutRequest;
import com.shop.simulator.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<Transaction> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    @PostMapping("/checkout")
    public ResponseEntity<Transaction> checkout(@RequestBody CheckoutRequest request) {
        Transaction transaction = transactionService.checkout(request.customerId());
        return new ResponseEntity<>(transaction, HttpStatus.CREATED);
    }
}
