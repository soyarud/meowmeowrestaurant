package service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import domain.MenuItem;
import domain.Order;

/**
 * DatabaseManager - Handles database operations for orders
 */
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

    // CREATE a new order and return its ID (with empty items)
    public int createOrder(String customerName) {
        String sql = "INSERT INTO orders (customer_name, order_date, items, total_price, item_count) " +
                     "VALUES (?, NOW(), '[]', 0.0, 0) RETURNING id";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, customerName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int orderId = rs.getInt(1);
                System.out.println("[DB] Order created with ID: " + orderId + " for customer: " + customerName);
                return orderId;
            }

        } catch (SQLException e) {
            System.out.println("Order creation failed: " + e.getMessage());
        }
        return -1;
    }

    // ADD item to order (updates the order with new item and recalculates totals)
    public void addItemToOrder(int orderId, int menuItemId, int quantity, String itemName, double itemPrice) {
        try (Connection conn = connect()) {
            // Get current order data
            String selectSql = "SELECT items, total_price, item_count FROM orders WHERE id = ?";
            PreparedStatement selectPstmt = conn.prepareStatement(selectSql);
            selectPstmt.setInt(1, orderId);
            ResultSet rs = selectPstmt.executeQuery();

            if (rs.next()) {
                String currentItemsJson = rs.getString("items");
                double currentTotal = rs.getDouble("total_price");
                int currentCount = rs.getInt("item_count");

                // Parse existing items
                List<String> itemsList = parseItemsJson(currentItemsJson);

                // Add new item
                String newItem = "{\"id\":" + menuItemId + ",\"name\":\"" + escapeJson(itemName) + 
                                "\",\"quantity\":" + quantity + ",\"price\":" + 
                                String.format(java.util.Locale.US, "%.2f", itemPrice) + "}";
                itemsList.add(newItem);

                // Rebuild JSON array
                StringBuilder itemsJsonBuilder = new StringBuilder("[");
                for (int i = 0; i < itemsList.size(); i++) {
                    if (i > 0) itemsJsonBuilder.append(",");
                    itemsJsonBuilder.append(itemsList.get(i));
                }
                itemsJsonBuilder.append("]");

                // Update order
                String updateSql = "UPDATE orders SET items = ?, total_price = ?, item_count = ? WHERE id = ?";
                PreparedStatement updatePstmt = conn.prepareStatement(updateSql);
                updatePstmt.setString(1, itemsJsonBuilder.toString());
                updatePstmt.setDouble(2, currentTotal + (itemPrice * quantity));
                updatePstmt.setInt(3, currentCount + quantity);
                updatePstmt.setInt(4, orderId);
                updatePstmt.executeUpdate();

                System.out.println("[DB] Added item #" + menuItemId + " (qty: " + quantity + ") to order #" + orderId);
            }
            selectPstmt.close();

        } catch (SQLException e) {
            System.out.println("Adding item to order failed: " + e.getMessage());
        }
    }

    // GET all orders as JSON
    public String getAllOrdersAsJson() {
        StringBuilder json = new StringBuilder("[");
        String sql = "SELECT id, customer_name, order_date, items, total_price, item_count FROM orders ORDER BY id DESC";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean firstOrder = true;
            
            while (rs.next()) {
                if (!firstOrder) json.append(",");
                
                json.append("{\"id\":").append(rs.getInt("id"))
                    .append(",\"customer\":\"").append(escapeJson(rs.getString("customer_name")))
                    .append("\",\"orderDate\":\"").append(rs.getTimestamp("order_date"))
                    .append("\",\"items\":").append(rs.getString("items"))
                    .append(",\"price\":").append(String.format(java.util.Locale.US, "%.2f", rs.getDouble("total_price")))
                    .append(",\"itemCount\":").append(rs.getInt("item_count"))
                    .append("}");
                
                firstOrder = false;
            }
            
            json.append("]");

        } catch (SQLException e) {
            System.out.println("Retrieving orders failed: " + e.getMessage());
            return "[]";
        }

        return json.toString();
    }

    // MENU item CRUD operations
    public int createMenuItem(String name, String description, double price, String category) {
        String sql = "INSERT INTO menu_items (name, description, price, category) VALUES (?, ?, ?, ?) RETURNING id";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setDouble(3, price);
            pstmt.setString(4, category);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.out.println("createMenuItem failed: " + e.getMessage());
        }
        return -1;
    }

    public boolean updateMenuItem(int id, String name, String description, double price, String category) {
        String sql = "UPDATE menu_items SET name = ?, description = ?, price = ?, category = ? WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, description);
            pstmt.setDouble(3, price);
            pstmt.setString(4, category);
            pstmt.setInt(5, id);
            int affected = pstmt.executeUpdate();
            return affected > 0;
        } catch (SQLException e) {
            System.out.println("updateMenuItem failed: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteMenuItem(int id) {
        String sql = "DELETE FROM menu_items WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            int affected = pstmt.executeUpdate();
            System.out.println("[DB] Deleted menu_item #" + id + ", affected=" + affected);
            if (affected > 0) {
                // Keep order sequence aligned as well (defensive)
                syncOrderSequence();
            }
            return affected > 0;
        } catch (SQLException e) {
            System.out.println("deleteMenuItem failed: " + e.getMessage());
            return false;
        }
    }

    // DELETE an order by id
    public boolean deleteOrder(int orderId) {
        String sql = "DELETE FROM orders WHERE id = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, orderId);
            int affected = pstmt.executeUpdate();
            System.out.println("[DB] Deleted order #" + orderId + ", affected=" + affected);
            if (affected > 0) {
                // After deleting, ensure sequence stays aligned with current max(id)
                syncOrderSequence();
            }
            return affected > 0;
        } catch (SQLException e) {
            System.out.println("Deleting order failed: " + e.getMessage());
            return false;
        }
    }

    // LOAD orders from DB and construct domain.Order objects
    public List<Order> loadOrders() {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT id, customer_name, items, total_price FROM orders ORDER BY id";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String customer = rs.getString("customer_name");
                String itemsJson = rs.getString("items");
                double total = rs.getDouble("total_price");

                Order order = new Order(id, customer == null ? "" : customer);

                List<String> itemStrings = parseItemsJson(itemsJson);
                for (String itemStr : itemStrings) {
                    int itemId = extractInt(itemStr, "id");
                    String name = extractString(itemStr, "name");
                    double price = extractDouble(itemStr, "price");
                    int qty = extractInt(itemStr, "quantity");
                    if (qty <= 0) qty = 1;

                    MenuItem mi = new MenuItem(itemId, name == null ? "" : name, "", price, "Other");
                    for (int i = 0; i < qty; i++) {
                        order.addItem(mi);
                    }
                }

                order.setTotalPrice(total);
                orders.add(order);
            }

        } catch (SQLException e) {
            System.out.println("Loading orders failed: " + e.getMessage());
        }

        return orders;
    }

    // Ensure PostgreSQL sequence for orders.id is set to max(id) to avoid lower nextval
    public void syncOrderSequence() {
        try (Connection conn = connect()) {
            if (conn == null) {
                System.out.println("[DB] Cannot sync sequence: no DB connection");
                return;
            }

            // Get sequence name for the serial column
            String seqName = null;
            try (PreparedStatement pseq = conn.prepareStatement("SELECT pg_get_serial_sequence('orders','id') AS seq")) {
                try (ResultSet rs = pseq.executeQuery()) {
                    if (rs.next()) seqName = rs.getString("seq");
                }
            }

            if (seqName == null || seqName.isEmpty()) {
                System.out.println("[DB] Could not determine orders.id sequence name");
                return;
            }

            int maxId = 0;
            try (PreparedStatement pmax = conn.prepareStatement("SELECT COALESCE(MAX(id),0) AS maxid FROM orders")) {
                try (ResultSet rs = pmax.executeQuery()) {
                    if (rs.next()) maxId = rs.getInt("maxid");
                }
            }

            String setvalSql = String.format("SELECT setval('%s', %d, true)", seqName.replace("'", "''"), maxId);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(setvalSql);
                System.out.println("[DB] Order sequence " + seqName + " set to max(id)=" + maxId);

                // Read back last_value and is_called for easier debugging
                String checkSql = "SELECT last_value, is_called FROM " + seqName;
                try (Statement stmt2 = conn.createStatement(); ResultSet rs2 = stmt2.executeQuery(checkSql)) {
                    if (rs2.next()) {
                        long lastVal = rs2.getLong("last_value");
                        boolean isCalled = rs2.getBoolean("is_called");
                        System.out.println("[DB] Sequence state: last_value=" + lastVal + ", is_called=" + isCalled);
                    }
                } catch (SQLException e) {
                    System.out.println("[DB] Could not read sequence state: " + e.getMessage());
                }
            }

        } catch (SQLException e) {
            System.out.println("Syncing order sequence failed: " + e.getMessage());
        }
    }

    // Parse items JSON string into list
    private List<String> parseItemsJson(String itemsJson) {
        List<String> items = new ArrayList<>();
        if (itemsJson == null || itemsJson.equals("[]")) {
            return items;
        }
        
        // Simple JSON array parsing
        String content = itemsJson.substring(1, itemsJson.length() - 1);
        if (content.isEmpty()) return items;
        
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : content.toCharArray()) {
            if (c == '{') depth++;
            if (c == '}') depth--;
            
            if (c == ',' && depth == 0) {
                items.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            items.add(current.toString().trim());
        }
        
        return items;
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    // Helpers to extract simple values from item JSON snippets like {"id":1,"name":"X","quantity":2}
    private int extractInt(String json, String key) {
        try {
            String pattern = "\"" + key + "\":";
            int idx = json.indexOf(pattern);
            if (idx == -1) return 0;
            idx += pattern.length();
            StringBuilder sb = new StringBuilder();
            while (idx < json.length()) {
                char c = json.charAt(idx);
                if (c == ',' || c == '}' || Character.isWhitespace(c)) break;
                sb.append(c);
                idx++;
            }
            String s = sb.toString().trim();
            if (s.isEmpty()) return 0;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private double extractDouble(String json, String key) {
        try {
            String pattern = "\"" + key + "\":";
            int idx = json.indexOf(pattern);
            if (idx == -1) return 0.0;
            idx += pattern.length();
            StringBuilder sb = new StringBuilder();
            while (idx < json.length()) {
                char c = json.charAt(idx);
                if (c == ',' || c == '}' || Character.isWhitespace(c)) break;
                sb.append(c);
                idx++;
            }
            String s = sb.toString().trim();
            if (s.isEmpty()) return 0.0;
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String extractString(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int idx = json.indexOf(pattern);
            if (idx == -1) return "";
            idx += pattern.length();
            int end = json.indexOf('"', idx);
            if (end == -1) return "";
            return json.substring(idx, end);
        } catch (Exception e) {
            return "";
        }
    }

    // RECREATE tables with merged schema
    public void recreateTables() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            // Drop old tables if they exist
            stmt.execute("DROP TABLE IF EXISTS order_items");
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("DROP TABLE IF EXISTS menu_items");

            // Create menu_items table
            stmt.execute("CREATE TABLE menu_items (" +
                    "id SERIAL PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL, " +
                    "description TEXT, " +
                    "price DECIMAL(10,2) NOT NULL, " +
                    "category VARCHAR(50) NOT NULL" +
                    ")");

            // Create merged orders table
            stmt.execute("CREATE TABLE orders (" +
                    "id SERIAL PRIMARY KEY, " +
                    "customer_name VARCHAR(100) NOT NULL, " +
                    "order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "items TEXT DEFAULT '[]', " +
                    "total_price DECIMAL(10,2) DEFAULT 0.0, " +
                    "item_count INT DEFAULT 0" +
                    ")");

            System.out.println("[DB] Tables recreated successfully with merged schema!");

        } catch (SQLException e) {
            System.out.println("Table recreation failed: " + e.getMessage());
        }
    }

}
