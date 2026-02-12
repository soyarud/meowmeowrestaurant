package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import domain.MenuItem;
import domain.MenuItemFactory;
import domain.Order;
import exceptions.InvalidOrderException;
import repository.MenuItemRepository;
import repository.OrderRepository;
import controller.OrderController;
import service.DatabaseManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.List;

/**
 * RestServer - HTTP Server with REST API endpoints and static file serving
 */
public class RestServer {
    private static OrderRepository orderRepository;
    private static MenuItemRepository menuRepository;
    private static DatabaseManager databaseManager;
    private static final int PORT = 8080;
    private static final String ADMIN_KEY = "meowadmin";
    
    // Database credentials
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/restaurant_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "soyarud";

    public static void main(String[] args) throws IOException {
        // Initialize repositories and controller
        orderRepository = new OrderRepository();
        menuRepository = new MenuItemRepository();
        new OrderController(orderRepository, menuRepository);
        databaseManager = new DatabaseManager();

        // Load existing orders from database into in-memory repository
        try {
            List<Order> dbOrders = databaseManager.loadOrders();
            int maxId = 0;
            for (Order o : dbOrders) {
                orderRepository.save(o);
                if (o.getOrderId() > maxId) maxId = o.getOrderId();
            }
            if (maxId > 0) {
                orderRepository.setNextId(maxId + 1);
            }
            System.out.println("[Startup] Loaded " + dbOrders.size() + " orders from DB into repository");
        } catch (Exception e) {
            System.err.println("[Startup] Failed to load orders from DB: " + e.getMessage());
        }

        // Ensure DB sequence for orders.id is in sync with max(id)
        try {
            databaseManager.syncOrderSequence();
        } catch (Exception e) {
            System.err.println("[Startup] Failed to sync order sequence: " + e.getMessage());
        }

        // Optional: Recreate tables with merged schema (uncomment to reset database)
        // databaseManager.recreateTables();

        // Populate menu repository with default items
        menuRepository.save(MenuItemFactory.create(1, "Margherita Pizza", "Classic pizza with tomato and mozzarella", 12.99, "Main"));
        menuRepository.save(MenuItemFactory.create(2, "Carbonara Pasta", "Spaghetti with eggs, cheese, and pancetta", 14.50, "Main"));
        menuRepository.save(MenuItemFactory.create(3, "Caesar Salad", "Romaine lettuce with croutons and Caesar dressing", 8.75, "Appetizer"));
        menuRepository.save(MenuItemFactory.create(4, "Tiramisu", "Italian coffee-flavored dessert", 6.99, "Dessert"));
        menuRepository.save(MenuItemFactory.create(5, "Coca Cola", "Refreshing soft drink", 2.50, "Drink"));
        menuRepository.save(MenuItemFactory.create(6, "House Red Wine", "Full-bodied red wine", 7.50, "Drink"));
        menuRepository.save(MenuItemFactory.create(7, "Grilled Salmon", "Salmon from Balkhash", 18.99, "Main"));
        menuRepository.save(MenuItemFactory.create(8, "Caesar Olive", "Kolbasa, egg, cucumber, mayonaise", 6.50, "Appetizer"));
        menuRepository.save(MenuItemFactory.create(9, "Cheesecake", "Cheese and cake", 6.50, "Dessert"));
        menuRepository.save(MenuItemFactory.create(10, "Red Wine", "100 years wine", 1000.00, "Drink"));
        menuRepository.save(MenuItemFactory.create(11, "Botol of Water", "Water", 1.00, "Drink"));

        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API endpoints
        server.createContext("/api/menu", new MenuApiHandler());
        server.createContext("/api/orders", new OrderApiHandler());
        server.createContext("/api/admin/sync-sequence", new SyncSequenceHandler());

        // Static file serving
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("meow meow restaurant");
        System.out.println("Server running on port: " + PORT);
        System.out.println("Open in browser: http://localhost:" + PORT);
        System.out.println("Database: restaurant_db on localhost:5432");
        System.out.println("API Endpoints:");
        System.out.println("GET  /api/menu         - Get all menu items");
        System.out.println("POST /api/orders       - Create new order");
        System.out.println("GET  /api/orders       - Get all orders");
    }

    /**
     * Handler for menu API endpoints - queries PostgreSQL database
     */
    static class MenuApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCORSHeaders(exchange);

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            System.out.println("[MenuAPI] " + method + " " + path);

            try {
                if ("GET".equals(method)) {
                    handleGetMenu(exchange);
                } else if ("POST".equals(method)) {
                    handleCreateMenuItem(exchange);
                } else if ("PUT".equals(method)) {
                    handleUpdateMenuItem(exchange, path);
                } else if ("DELETE".equals(method)) {
                    handleDeleteMenuItem(exchange, path);
                } else if ("OPTIONS".equals(method)) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    sendErrorResponse(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                System.err.println("[MenuAPI] Error: " + e.getMessage());
                e.printStackTrace();
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private boolean isAdmin(HttpExchange exchange) {
            java.util.List<String> vals = exchange.getRequestHeaders().get("X-Admin-Key");
            if (vals == null || vals.isEmpty()) return false;
            return ADMIN_KEY.equals(vals.get(0));
        }

        private void handleCreateMenuItem(HttpExchange exchange) throws IOException {
            if (!isAdmin(exchange)) {
                sendErrorResponse(exchange, 403, "Admin key required");
                return;
            }

            String body = readRequestBody(exchange);
            String name = extractJsonString(body, "name");
            String description = extractJsonString(body, "description");
            String category = extractJsonString(body, "category");
            double price = extractJsonNumber(body, "price");

            int id = databaseManager.createMenuItem(name, description, price, category);
            if (id == -1) {
                sendErrorResponse(exchange, 500, "Failed to create menu item");
                return;
            }

            String json = String.format("{\"id\":%d,\"name\":\"%s\"}", id, escapeJson(name));
            sendJsonResponse(exchange, json, 201);
        }

        private void handleUpdateMenuItem(HttpExchange exchange, String path) throws IOException {
            if (!isAdmin(exchange)) {
                sendErrorResponse(exchange, 403, "Admin key required");
                return;
            }
            String[] parts = path.split("/");
            if (parts.length < 3) {
                sendErrorResponse(exchange, 400, "Invalid path");
                return;
            }

            String idPart = parts[parts.length - 1];
            try {
                int id = Integer.parseInt(idPart);
                String body = readRequestBody(exchange);
                String name = extractJsonString(body, "name");
                String description = extractJsonString(body, "description");
                String category = extractJsonString(body, "category");
                double price = extractJsonNumber(body, "price");

                boolean ok = databaseManager.updateMenuItem(id, name, description, price, category);
                if (ok) {
                    sendJsonResponse(exchange, "{\"updated\":true}", 200);
                } else {
                    sendErrorResponse(exchange, 404, "Menu item not found");
                }
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid id");
            }
        }

        private void handleDeleteMenuItem(HttpExchange exchange, String path) throws IOException {
            if (!isAdmin(exchange)) {
                sendErrorResponse(exchange, 403, "Admin key required");
                return;
            }
            String[] parts = path.split("/");
            if (parts.length < 3) {
                sendErrorResponse(exchange, 400, "Invalid path");
                return;
            }
            String idPart = parts[parts.length - 1];
            try {
                int id = Integer.parseInt(idPart);
                boolean ok = databaseManager.deleteMenuItem(id);
                if (ok) sendJsonResponse(exchange, String.format("{\"deleted\":%d}", id), 200);
                else sendErrorResponse(exchange, 404, "Menu item not found");
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid id");
            }
        }

        private void handleGetMenu(HttpExchange exchange) throws IOException {
            String json = getMenuFromDatabase();
            System.out.println("[MenuAPI] Returning menu data from database");
            sendJsonResponse(exchange, json, 200);
        }

        private String getMenuFromDatabase() {
            StringBuilder json = new StringBuilder("[");
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT id, name, description, price, category FROM menu_items ORDER BY id";
                PreparedStatement stmt = conn.prepareStatement(query);
                ResultSet rs = stmt.executeQuery();
                
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    String description = rs.getString("description");
                    double price = rs.getDouble("price");
                    String category = rs.getString("category");
                    
                    json.append("{")
                        .append("\"id\":").append(id).append(",")
                        .append("\"name\":\"").append(escapeJson(name)).append("\",")
                        .append("\"description\":\"").append(escapeJson(description)).append("\",")
                        .append("\"price\":").append(String.format(java.util.Locale.US, "%.2f", price)).append(",")
                        .append("\"category\":\"").append(escapeJson(category)).append("\"")
                        .append("}");
                    
                    first = false;
                    System.out.println("[MenuAPI] Loaded: " + id + " | " + name + " | Category: " + category + " | Price: $" + price);
                }
                
                rs.close();
                stmt.close();
            } catch (SQLException e) {
                System.err.println("[MenuAPI] Database error: " + e.getMessage());
                System.err.println("[MenuAPI] Falling back to in-memory menu repository");
                
                // Fallback to in-memory repository
                boolean first = true;
                for (MenuItem item : menuRepository.getAll()) {
                    if (!first) json.append(",");
                    
                    json.append("{")
                        .append("\"id\":").append(item.getId()).append(",")
                        .append("\"name\":\"").append(escapeJson(item.getName())).append("\",")
                        .append("\"description\":\"").append(escapeJson(item.getDescription())).append("\",")
                        .append("\"price\":").append(String.format(java.util.Locale.US, "%.2f", item.getPrice())).append(",")
                        .append("\"category\":\"").append(escapeJson(item.getCategory())).append("\"")
                        .append("}");
                    
                    first = false;
                    System.out.println("[MenuAPI] Loaded (from memory): " + item.getId() + " | " + item.getName() + " | Category: " + item.getCategory() + " | Price: $" + item.getPrice());
                }
            }
            
            json.append("]");
            String result = json.toString();
            System.out.println("[MenuAPI] Total items returned: " + (result.split("\"id\":").length - 1));
            return result;
        }
    }

    /**
     * Handler for order API endpoints
     */
    static class OrderApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCORSHeaders(exchange);

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            try {
                if ("GET".equals(method)) {
                    handleGetOrders(exchange, path);
                } else if ("POST".equals(method)) {
                    handleCreateOrder(exchange);
                } else if ("DELETE".equals(method)) {
                    handleDeleteOrder(exchange, path);
                } else if ("OPTIONS".equals(method)) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    sendErrorResponse(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private void handleDeleteOrder(HttpExchange exchange, String path) throws IOException {
            // Expecting path like /api/orders/{id}
            String[] parts = path.split("/");
            if (parts.length < 3) {
                sendErrorResponse(exchange, 400, "Invalid path");
                return;
            }

            String idPart = parts[parts.length - 1];
            try {
                int id = Integer.parseInt(idPart);

                boolean dbDeleted = databaseManager.deleteOrder(id);
                boolean memDeleted = orderRepository.deleteById(id);

                if (dbDeleted || memDeleted) {
                    String json = String.format("{\"deleted\":%d}", id);
                    sendJsonResponse(exchange, json, 200);
                } else {
                    sendErrorResponse(exchange, 404, "Order not found");
                }
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid order id");
            }
        }

        private void handleGetOrders(HttpExchange exchange, String path) throws IOException {
            String json = databaseManager.getAllOrdersAsJson();
            sendJsonResponse(exchange, json, 200);
        }

        private void handleCreateOrder(HttpExchange exchange) throws IOException {
            String body = readRequestBody(exchange);
            System.out.println("[OrderAPI] Received order request: " + body);
            
            try {
                String customerName = extractJsonString(body, "customerName");
                List<OrderController.OrderItemRequest> items = parseOrderItems(body);
                
                System.out.println("[OrderAPI] Customer: " + customerName + ", Items: " + items.size());
                
                // Validate customer name
                if (customerName == null || customerName.trim().isEmpty()) {
                    System.err.println("[OrderAPI] ERROR: Customer name is empty");
                    sendErrorResponse(exchange, 400, "Customer name is required");
                    return;
                }
                
                if (items == null || items.isEmpty()) {
                    System.err.println("[OrderAPI] ERROR: Items list is empty");
                    sendErrorResponse(exchange, 400, "Order must contain at least one item");
                    return;
                }

                // Create order with menu items from repository
                Order order = createOrderFromDatabase(customerName, items);
                String json = convertOrderToJson(order);
                System.out.println("[OrderAPI] SUCCESS: Order created - " + json);
                sendJsonResponse(exchange, json, 201);
            } catch (InvalidOrderException e) {
                System.err.println("[OrderAPI] ERROR: Invalid order - " + e.getMessage());
                sendErrorResponse(exchange, 400, "Invalid order: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("[OrderAPI] ERROR creating order: " + e.getMessage());
                e.printStackTrace();
                sendErrorResponse(exchange, 400, "Error creating order: " + e.getMessage());
            }
        }
        
        private Order createOrderFromDatabase(String customerName, List<OrderController.OrderItemRequest> items)
        throws InvalidOrderException {
            // First create order in database to obtain canonical ID
            int dbOrderId = databaseManager.createOrder(customerName);
            if (dbOrderId == -1) {
                throw new InvalidOrderException("Failed to create order in database");
            }

            Order order = new Order(dbOrderId, customerName);
            double totalPrice = 0;

            for (OrderController.OrderItemRequest itemReq : items) {
                // Get item from repository
                MenuItem item = menuRepository.findById(itemReq.menuItemId);

                // If not found in repository, create a placeholder item
                if (item == null) {
                    System.out.println("[OrderAPI] Item #" + itemReq.menuItemId + " not in repository, creating placeholder");
                    item = new MenuItem(itemReq.menuItemId, "Item #" + itemReq.menuItemId, "Menu item", 0.0, "Other");
                }

                // Add item to order based on quantity
                for (int i = 0; i < itemReq.quantity; i++) {
                    order.addItem(item);
                    totalPrice += item.getPrice();
                }

                // Save item to database with item details
                databaseManager.addItemToOrder(dbOrderId, itemReq.menuItemId, itemReq.quantity, item.getName(), item.getPrice());

                System.out.println("[OrderAPI] Added to order: " + item.getName() + " x" + itemReq.quantity);
            }

            order.setTotalPrice(totalPrice);
            orderRepository.save(order);
            System.out.println("[OrderAPI] Order created: #" + dbOrderId + " for " + customerName + " - Total: $" + totalPrice);
            return order;
        }
    }

    /**
     * Simple admin endpoint to sync the orders sequence to MAX(id).
     * Use with header `X-Admin-Key: meowadmin`.
     */
    static class SyncSequenceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCORSHeaders(exchange);
            String method = exchange.getRequestMethod();
            if (!"POST".equalsIgnoreCase(method)) {
                try { sendErrorResponse(exchange, 405, "Method not allowed"); } catch (IOException ignored) {}
                return;
            }

            // Admin check
            java.util.List<String> vals = exchange.getRequestHeaders().get("X-Admin-Key");
            if (vals == null || vals.isEmpty() || !ADMIN_KEY.equals(vals.get(0))) {
                try { sendErrorResponse(exchange, 403, "Admin key required"); } catch (IOException ignored) {}
                return;
            }

            try {
                databaseManager.syncOrderSequence();
                sendJsonResponse(exchange, "{\"synced\":true}", 200);
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Sync failed: " + e.getMessage());
            }
        }
    }

    /**
     * Handler for static files (HTML, CSS, JS)
     */
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCORSHeaders(exchange);

            String path = exchange.getRequestURI().getPath();

            if ("/".equals(path)) {
                serveFile(exchange, "index.html", "text/html");
            } else {
                serveFile(exchange, path.substring(1), getMimeType(path));
            }
        }

        private void serveFile(HttpExchange exchange, String filePath, String contentType) throws IOException {
            String projectRoot = System.getProperty("user.dir");
            java.io.File file = new java.io.File(projectRoot, filePath);
            String fullPath = file.getAbsolutePath();

            System.out.println("[StaticFile] Serving: " + filePath + " from " + fullPath);

            try {
                if (!file.exists()) {
                    System.err.println("[StaticFile] File not found: " + fullPath);
                    sendErrorResponse(exchange, 404, "File not found");
                    return;
                }
                
                byte[] fileContent = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, fileContent.length);
                OutputStream os = exchange.getResponseBody();
                os.write(fileContent);
                os.close();
                System.out.println("[StaticFile] Successfully served: " + filePath);
            } catch (IOException e) {
                System.err.println("[StaticFile] Error reading file: " + e.getMessage());
                sendErrorResponse(exchange, 500, "Error reading file: " + e.getMessage());
            }
        }

        private String getMimeType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "text/javascript";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
            if (path.endsWith(".gif")) return "image/gif";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".webp")) return "image/webp";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    // ==================== Helper Methods ====================

    private static void addCORSHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Admin-Key");
    }

    private static void sendJsonResponse(HttpExchange exchange, String json, int statusCode) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message)
    throws IOException {
        String json = String.format("{\"error\":\"%s\"}", escapeJson(message));
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String convertOrderToJson(Order order) {
        StringBuilder items = new StringBuilder("[");
        List<MenuItem> orderItems = order.getItems();
        for (int i = 0; i < orderItems.size(); i++) {
            MenuItem item = orderItems.get(i);
            items.append(String.format(java.util.Locale.US, "{\"id\":%d,\"name\":\"%s\",\"price\":%.2f}",
                item.getId(), escapeJson(item.getName()), item.getPrice()));
            if (i < orderItems.size() - 1) items.append(",");
        }
        items.append("]");

        return String.format(java.util.Locale.US,
            "{\"orderId\":%d,\"customerName\":\"%s\",\"items\":%s,\"totalPrice\":%.2f,\"status\":\"%s\",\"itemCount\":%d}",
            order.getOrderId(),
            escapeJson(order.getCustomerName()),
            items.toString(),
            order.getTotalPrice(),
            order.getStatus(),
            order.getItems().size()
        );
    }

    private static List<OrderController.OrderItemRequest> parseOrderItems(String json) {
        List<OrderController.OrderItemRequest> items = new java.util.ArrayList<>();
        
        // Extract items array
        int itemsStart = json.indexOf("\"items\":");
        if (itemsStart == -1) return items;
        
        int arrayStart = json.indexOf("[", itemsStart);
        int arrayEnd = json.lastIndexOf("]");
        if (arrayStart == -1 || arrayEnd == -1) return items;
        
        String itemsArray = json.substring(arrayStart + 1, arrayEnd);
        
        // Split by }, { to separate items
        String[] itemStrings = itemsArray.split("\\}\\s*,\\s*\\{");
        
        for (String itemStr : itemStrings) {
            itemStr = itemStr.replaceAll("[\\{\\}]", "").trim();
            if (itemStr.isEmpty()) continue;
            
            try {
                int menuItemId = extractJsonIntSafe(itemStr, "menuItemId");
                int quantity = extractJsonIntSafe(itemStr, "quantity");
                items.add(new OrderController.OrderItemRequest(menuItemId, quantity));
                System.out.println("[OrderAPI] Parsed item: menuItemId=" + menuItemId + ", quantity=" + quantity);
            } catch (Exception e) {
                System.err.println("[OrderAPI] Error parsing item: " + itemStr + " - " + e.getMessage());
            }
        }
        return items;
    }

    private static int extractJsonIntSafe(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return 0;
        
        start += pattern.length();
        
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        
        // Find end of number (comma, }, or ])
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        
        if (end <= start) return 0;
        
        try {
            String numStr = json.substring(start, end).trim();
            return Integer.parseInt(numStr);
        } catch (NumberFormatException e) {
            System.err.println("[OrderAPI] Failed to parse number: " + json.substring(start, Math.min(end + 5, json.length())));
            return 0;
        }
    }

    private static double extractJsonNumber(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return 0.0;

        start += pattern.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;

        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') end++; else break;
        }

        if (end <= start) return 0.0;

        try {
            String numStr = json.substring(start, end).trim();
            return Double.parseDouble(numStr);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String extractJsonString(String json, String key) {
        // More robust parsing that handles whitespace variations
        String pattern = "\"" + key + "\"";
        int start = json.indexOf(pattern);
        System.out.println("[Debug] Extracting '" + key + "': pattern='" + pattern + "', start=" + start);
        if (start == -1) {
            System.out.println("[Debug] Pattern not found, returning empty string");
            return "";
        }
        
        // Move past the key name
        start += pattern.length();
        
        // Skip whitespace and colon
        while (start < json.length() && (Character.isWhitespace(json.charAt(start)) || json.charAt(start) == ':')) {
            start++;
        }
        
        // Skip opening quote
        if (start < json.length() && json.charAt(start) == '"') {
            start++;
        } else {
            System.out.println("[Debug] No opening quote found after key");
            return "";
        }
        
        // Find closing quote
        int end = start;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\' && end + 1 < json.length()) {
                end += 2; // Skip escaped characters
            } else {
                end++;
            }
        }
        
        System.out.println("[Debug] After pattern, start=" + start + ", end=" + end);
        if (end == -1 || end >= json.length()) {
            System.out.println("[Debug] Closing quote not found, returning empty string");
            return "";
        }
        
        if (start > end) {
            System.out.println("[Debug] start > end: returning empty string");
            return "";
        }
        
        String result = json.substring(start, end);
        System.out.println("[Debug] Extracted '" + key + "': '" + result + "'");
        return result;
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
