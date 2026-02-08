import java.sql.*;

public class DatabaseManager {
    private static final String URL = "jdbc:postgresql://localhost:5432/restaurant_db";
    private static final String USER = "postgres";
    private static final String PASSWORD = "soyarud";

    private Connection connect() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            System.out.println("Connection failed: " + e.getMessage());
            return null;
        }
    }

    // INSERT menu item
    public void insertMenuItem(String name, String description, double price, String category) {
        String sql = "INSERT INTO menu_items (name, description, price, category) VALUES (?, ?, ?, ?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setDouble(3, price);
            pstmt.setString(4, category);
            pstmt.executeUpdate();
            System.out.println("Inserted menu item: " + name);
        } catch (SQLException e) {
            System.out.println("Insert failed: " + e.getMessage());
        }
    }

    // Display all menu items in a formatted table
    public void readMenuItems() {
        String sql = "SELECT * FROM menu_items";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            System.out.println("\n========== MENU ==========");
            System.out.printf("%-3s | %-20s | %-8s | %-10s%n",
                    "ID", "Name", "Price", "Category");
            System.out.println("-----------------------------------------------");

            while (rs.next()) {
                System.out.printf("%-3d | %-20s | $%-7.2f | %-10s%n",
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getString("category"));
            }

        } catch (SQLException e) {
            System.out.println("Read menu failed: " + e.getMessage());
        }
    }


    // UPDATE price of menu item by name
    public void updateMenuItemPrice(String name, double newPrice) {
        String sql = "UPDATE menu_items SET price = ? WHERE name = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, newPrice);
            pstmt.setString(2, name);
            int rows = pstmt.executeUpdate();
            System.out.println("Updated " + rows + " item(s). New price for " + name + ": $" + newPrice);
        } catch (SQLException e) {
            System.out.println("Update failed: " + e.getMessage());
        }
    }

    // DELETE menu item by name
    public void deleteMenuItem(String name) {
        String sql = "DELETE FROM menu_items WHERE name = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            int rows = pstmt.executeUpdate();
            System.out.println("Deleted " + rows + " item(s): " + name);
        } catch (SQLException e) {
            System.out.println("Delete failed: " + e.getMessage());
        }
    }
    // Recreate all tables and relationships from scratch
    public void recreateTables() {

        String dropOrderItems = "DROP TABLE IF EXISTS order_items";
        String dropOrders = "DROP TABLE IF EXISTS orders";
        String dropMenuItems = "DROP TABLE IF EXISTS menu_items";

        String createMenuItems =
                "CREATE TABLE menu_items (" +
                        "id SERIAL PRIMARY KEY, " +
                        "name VARCHAR(100) NOT NULL, " +
                        "description TEXT, " +
                        "price DECIMAL(10,2) NOT NULL, " +
                        "category VARCHAR(50) NOT NULL" +
                        ")";

        String createOrders =
                "CREATE TABLE orders (" +
                        "id SERIAL PRIMARY KEY, " +
                        "order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";

        String createOrderItems =
                "CREATE TABLE order_items (" +
                        "order_id INT NOT NULL, " +
                        "menu_item_id INT NOT NULL, " +
                        "quantity INT NOT NULL, " +
                        "PRIMARY KEY (order_id, menu_item_id), " +
                        "FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE" +
                        ")";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            // Drop tables in correct order because of foreign keys
            stmt.execute(dropOrderItems);
            stmt.execute(dropOrders);
            stmt.execute(dropMenuItems);

            // Create tables
            stmt.execute(createMenuItems);
            stmt.execute(createOrders);
            stmt.execute(createOrderItems);

            System.out.println("All tables recreated successfully.");

        } catch (SQLException e) {
            System.out.println("Table recreation failed: " + e.getMessage());
        }
    }
    // Create a new order
    public int createOrder() {
        String sql = "INSERT INTO orders DEFAULT VALUES RETURNING id";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                int orderId = rs.getInt(1);
                System.out.println("Order created with ID: " + orderId);
                return orderId;
            }

        } catch (SQLException e) {
            System.out.println("Order creation failed: " + e.getMessage());
        }
        return -1;
    }
    // Add menu item to order
    public void addItemToOrder(int orderId, int menuItemId, int quantity) {
        String sql =
                "INSERT INTO order_items (order_id, menu_item_id, quantity) " +
                        "VALUES (?, ?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, orderId);
            pstmt.setInt(2, menuItemId);
            pstmt.setInt(3, quantity);

            pstmt.executeUpdate();
            System.out.println("Item added to order.");

        } catch (SQLException e) {
            System.out.println("Adding item failed: " + e.getMessage());
        }
    }
    // Read order details with menu items
    public void readOrderDetails(int orderId) {
        String sql =
                "SELECT o.id, m.name, oi.quantity, m.price " +
                        "FROM orders o " +
                        "JOIN order_items oi ON o.id = oi.order_id " +
                        "JOIN menu_items m ON oi.menu_item_id = m.id " +
                        "WHERE o.id = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, orderId);
            ResultSet rs = pstmt.executeQuery();

            System.out.println("\nOrder ID: " + orderId);
            while (rs.next()) {
                System.out.printf(
                        "%s | Quantity: %d | Price: $%.2f%n",
                        rs.getString("name"),
                        rs.getInt("quantity"),
                        rs.getDouble("price")
                );
            }

        } catch (SQLException e) {
            System.out.println("Read order failed: " + e.getMessage());
        }
    }

}