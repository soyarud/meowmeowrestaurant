package domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Order class represents a customer's order in the restaurant
 */
public class Order {
    // Attributes
    private int orderId;
    private List<MenuItem> items; // List of menu items in the order
    private String status;        // Order status: "NEW", "IN PROGRESS", "COMPLETED"
    private double totalPrice;    // Total price of the order
    private String customerName;  // Customer name

    // Constructor
    public Order(int orderId) {
        this.orderId = orderId;
        this.items = new ArrayList<>();
        this.status = "NEW";
        this.totalPrice = 0.0;
        this.customerName = "";
    }

    /**
     * Constructor with customer name
     */
    public Order(int orderId, String customerName) {
        this.orderId = orderId;
        this.items = new ArrayList<>();
        this.status = "NEW";
        this.totalPrice = 0.0;
        this.customerName = customerName;
    }

    // Method to add an item to the order
    public void addItem(MenuItem item) {
        items.add(item);
        totalPrice += item.getPrice();
        System.out.println("Added " + item.getName() + " to order #" + orderId);
    }

    // Method to remove an item from the order
    public void removeItem(MenuItem item) {
        if (items.remove(item)) {
            totalPrice -= item.getPrice();
            System.out.println("Removed " + item.getName() + " from order #" + orderId);
        }
    }

    // Method to change order status
    public void setStatus(String status) {
        this.status = status;
        System.out.println("Order #" + orderId + " status changed to: " + status);
    }

    // Getter methods
    public int getOrderId() {
        return orderId;
    }

    public List<MenuItem> getItems() {
        return items;
    }

    public void setItems(List<MenuItem> items) {
        this.items = items;
    }

    public String getStatus() {
        return status;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    // Method to display order details
    public void displayOrder() {
        System.out.println("\n=== Order #" + orderId + " ===");
        System.out.println("Status: " + status);
        System.out.println("Items:");

        // Display all items in the order
        for (MenuItem item : items) {
            System.out.println("  - " + item.getName() + " $" + item.getPrice());
        }

        System.out.println("Total: $" + totalPrice);
        System.out.println("======================");
    }

    @Override
    public String toString() {
        return "Order #" + orderId + " | Status: " + status +
                " | Total: $" + String.format("%.2f", totalPrice) +
                " | Items: " + items.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return orderId == order.orderId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }
}
