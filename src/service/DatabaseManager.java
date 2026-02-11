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
