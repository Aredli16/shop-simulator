Feature: Purchase Simulation

  Scenario: Successful checkout decrements stock and creates a transaction
    Given the product "Soda" exists with price 2.50 and stock 30
    And a customer "Bob" has a budget of 50.00 and has "Soda" in their cart
    When the customer performs checkout
    Then the checkout should succeed
    And the stock of "Soda" should be 29
    And a transaction should be recorded with total 2.50
    And the customer "Bob" should no longer be active in the simulator
