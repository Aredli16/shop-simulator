package com.shop.simulator.bdd;

import com.shop.simulator.ShopSimulatorApplication;
import com.shop.simulator.domain.Customer;
import com.shop.simulator.domain.Product;
import com.shop.simulator.domain.RestockOrder;
import com.shop.simulator.domain.Transaction;
import com.shop.simulator.repository.CustomerRepository;
import com.shop.simulator.repository.ProductRepository;
import com.shop.simulator.repository.RestockOrderRepository;
import com.shop.simulator.repository.TransactionRepository;
import com.shop.simulator.service.TransactionService;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CucumberContextConfiguration
@SpringBootTest(classes = ShopSimulatorApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class StepDefinitions {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RestockOrderRepository restockOrderRepository;

    @Autowired
    private TransactionService transactionService;

    private Customer activeCustomer;
    private Exception checkoutException;
    private Transaction completedTransaction;

    @Given("the product {string} exists with price {double} and stock {int}")
    public void givenProductExistsWithPriceAndStock(String name, double price, int stock) {
        Product p = productRepository.findAll().stream()
                .filter(prod -> prod.getName().equals(name))
                .findFirst()
                .orElse(new Product());
        
        p.setName(name);
        p.setCategory("GherkinTest");
        p.setPurchasePrice(price / 2.0); // Arbitrary purchase price
        p.setSellingPrice(price);
        p.setStockQuantity(stock);
        p.setRestockThreshold(stock / 5); // Default small threshold
        productRepository.save(p);
    }

    @Given("the product {string} exists with stock {int}, threshold {int}, and buying price {double}")
    public void givenProductExistsWithStockThresholdAndBuyingPrice(String name, int stock, int threshold, double buyingPrice) {
        Product p = productRepository.findAll().stream()
                .filter(prod -> prod.getName().equals(name))
                .findFirst()
                .orElse(new Product());

        p.setName(name);
        p.setCategory("GherkinTest");
        p.setPurchasePrice(buyingPrice);
        p.setSellingPrice(buyingPrice * 2.0); // Selling price is double the buying price
        p.setStockQuantity(stock);
        p.setRestockThreshold(threshold);
        productRepository.save(p);
    }

    @And("a customer {string} has a budget of {double} and has {string} in their cart")
    public void customerHasBudgetAndCart(String customerName, double budget, String productName) {
        Product p = productRepository.findAll().stream()
                .filter(prod -> prod.getName().equals(productName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Product " + productName + " not found."));

        // Clean previous customers with same name if any
        customerRepository.findAll().stream()
                .filter(c -> c.getName().equals(customerName))
                .forEach(c -> customerRepository.delete(c));

        Customer c = new Customer();
        c.setName(customerName);
        c.setBudget(budget);
        
        List<Product> cart = new ArrayList<>();
        cart.add(p);
        c.setCart(cart);
        
        activeCustomer = customerRepository.save(c);
        checkoutException = null;
        completedTransaction = null;
    }

    @When("the customer performs checkout")
    public void customerPerformsCheckout() {
        try {
            completedTransaction = transactionService.checkout(activeCustomer.getId());
        } catch (Exception e) {
            checkoutException = e;
        }
    }

    @Then("the checkout should succeed")
    public void checkoutShouldSucceed() {
        assertThat(checkoutException).isNull();
        assertThat(completedTransaction).isNotNull();
    }

    @And("the stock of {string} should be {int}")
    public void stockOfProductShouldBe(String name, int expectedStock) {
        Product p = productRepository.findAll().stream()
                .filter(prod -> prod.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Product " + name + " not found."));
        assertThat(p.getStockQuantity()).isEqualTo(expectedStock);
    }

    @And("a transaction should be recorded with total {double}")
    public void transactionRecordedWithTotal(double expectedTotal) {
        List<Transaction> transactions = transactionRepository.findAll();
        boolean found = transactions.stream()
                .anyMatch(tx -> tx.getCustomerName().equals(activeCustomer.getName()) && Math.abs(tx.getTotal() - expectedTotal) < 0.01);
        assertThat(found).isTrue();
    }

    @And("the customer {string} should no longer be active in the simulator")
    public void customerNoLongerActive(String customerName) {
        boolean found = customerRepository.findAll().stream()
                .anyMatch(c -> c.getName().equals(customerName));
        assertThat(found).isFalse();
    }

    @And("a pending restock order of 50 units of {string} should be automatically triggered")
    public void pendingRestockOrderTriggered(String productName) {
        List<RestockOrder> pendingOrders = restockOrderRepository.findByStatus("En cours");
        boolean found = pendingOrders.stream()
                .anyMatch(order -> order.getProduct().getName().equals(productName) && order.getQuantityOrdered() == 50);
        assertThat(found).isTrue();
    }
}
