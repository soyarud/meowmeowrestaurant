import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * Restaurant class represents a restaurant with menu and orders
 */
public class Restaurant {
    // Attributes
    private String name;           // Restaurant name
    private List<MenuItem> menu;   // List of menu items
    private List<Order> orders;    // List of active orders
    private int orderCounter;      // Counter for order IDs

    // Constructor
    public Restaurant(String name) {
        this.name = name;
        this.menu = new ArrayList<>();
        this.orders = new ArrayList<>();
        this.orderCounter = 1;
    }

    // Method to add a menu item
    public void addMenuItem(MenuItem item) {
        menu.add(item);
    }

    // Method to create a new order
    public Order createOrder() {
        Order newOrder = new Order(orderCounter);
        orders.add(newOrder);
        orderCounter++;
        return newOrder;
    }

    // Method to find an order by ID
    public Order findOrderById(int orderId) {
        for (Order order : orders) {
            if (order.getOrderId() == orderId) {
                return order;
            }
        }
        return null; // Return null if order not found
    }

    // Method to display the restaurant menu
    public void displayMenu() {
        System.out.println("\n=== " + name + " Menu ===");
        for (MenuItem item : menu) {
            item.displayInfo();
            System.out.println(); // Empty line between items
        }
    }

    // Getter methods
    public String getName() {
        return name;
    }

    public List<MenuItem> getMenu() {
        return menu;
    }

    public List<Order> getOrders() {
        return orders;
    }
    public List<MenuItem> searchByName(String keyword) {
        return menu.stream()
                .filter(item -> item.getName().toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<MenuItem> filterByCategory(String category) {
        return menu.stream()
                .filter(item -> item.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    public List<MenuItem> filterByMaxPrice(double maxPrice) {
        return menu.stream()
                .filter(item -> item.getPrice() <= maxPrice)
                .collect(Collectors.toList());
    }

    public void sortMenuByPrice() {
        menu.sort(Comparator.comparingDouble(MenuItem::getPrice));
    }

    public void sortMenuByName() {
        menu.sort(Comparator.comparing(MenuItem::getName));
    }
}