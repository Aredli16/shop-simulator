Feature: Auto Restocking Simulation

  Scenario: Auto restock is triggered when stock reaches threshold
    Given the product "Chips" exists with stock 10, threshold 10, and buying price 0.60
    And a customer "Alice" has a budget of 10.00 and has "Chips" in their cart
    When the customer performs checkout
    Then the checkout should succeed
    And the stock of "Chips" should be 9
    And a pending restock order of 50 units of "Chips" should be automatically triggered
