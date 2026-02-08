import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

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
    private static OrderController orderController;
    private static OrderRepository orderRepository;
    private static final int PORT = 8080;
    
    // Database credentials
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/restaurant_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "soyarud";

    public static void main(String[] args) throws IOException {
        // Initialize order repository and controller
        orderRepository = new OrderRepository();
        MenuItemRepository menuRepository = new MenuItemRepository();
        orderController = new OrderController(orderRepository, menuRepository);

        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API endpoints
        server.createContext("/api/menu", new MenuApiHandler());
        server.createContext("/api/orders", new OrderApiHandler());

        // Static file serving
        server.createContext("/", new StaticFileHandler());

        server.setExecutor(null);
        server.start();

        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║  🍝 Bella Italia Restaurant - REST Server Started 🍝  ║");
        System.out.println("╠════════════════════════════════════════════════════════╣");
        System.out.println("║  Server running on port: " + PORT);
        System.out.println("║  Open in browser: http://localhost:" + PORT);
        System.out.println("║  Database: restaurant_db on localhost:5432             ║");
        System.out.println("║  API Endpoints:                                       ║");
        System.out.println("║    GET  /api/menu         - Get all menu items        ║");
        System.out.println("║    POST /api/orders       - Create new order          ║");
        System.out.println("║    GET  /api/orders       - Get all orders            ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
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
                e.printStackTrace();
                return "[{\"error\":\"Database connection failed: " + escapeJson(e.getMessage()) + "\"}]";
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
                } else if ("OPTIONS".equals(method)) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    sendErrorResponse(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private void handleGetOrders(HttpExchange exchange, String path) throws IOException {
            List<Order> orders = orderController.getAllOrders();
            String json = convertOrdersToJson(orders);
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

                // Create order with database menu items
                Order order = createOrderFromDatabase(customerName, items);
                String json = convertOrderToJson(order);
                System.out.println("[OrderAPI] SUCCESS: Order created - " + json);
                sendJsonResponse(exchange, json, 201);
            } catch (Exception e) {
                System.err.println("[OrderAPI] ERROR creating order: " + e.getMessage());
                e.printStackTrace();
                sendErrorResponse(exchange, 400, "Error creating order: " + e.getMessage());
            }
        }
        
        private Order createOrderFromDatabase(String customerName, List<OrderController.OrderItemRequest> items) throws SQLException, InvalidOrderException {
            int orderId = orderRepository.getNextOrderId();
            Order order = new Order(orderId, customerName);
            double totalPrice = 0;
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                for (OrderController.OrderItemRequest itemReq : items) {
                    String query = "SELECT id, name, description, price, category FROM menu_items WHERE id = ?";
                    PreparedStatement stmt = conn.prepareStatement(query);
                    stmt.setInt(1, itemReq.menuItemId);
                    ResultSet rs = stmt.executeQuery();
                    
                    if (!rs.next()) {
                        throw new InvalidOrderException("Menu item with ID " + itemReq.menuItemId + " not found");
                    }
                    
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    String description = rs.getString("description");
                    double price = rs.getDouble("price");
                    String category = rs.getString("category");
                    
                    MenuItem item = new MenuItem(id, name, description, price, category);
                    
                    // Add item to order based on quantity
                    for (int i = 0; i < itemReq.quantity; i++) {
                        order.addItem(item);
                        totalPrice += price;
                    }
                    
                    rs.close();
                    stmt.close();
                    
                    System.out.println("[OrderAPI] Added to order: " + name + " x" + itemReq.quantity);
                }
            }
            
            order.setTotalPrice(totalPrice);
            orderRepository.save(order);
            System.out.println("[OrderAPI] Order created: #" + orderId + " for " + customerName + " - Total: $" + totalPrice);
            return order;
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJsonResponse(HttpExchange exchange, String json, int statusCode) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        OutputStream os = exchange.getResponseBody();
        os.write(response);
        os.close();
    }

    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
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

    private static String convertOrdersToJson(List<Order> orders) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < orders.size(); i++) {
            json.append(convertOrderToJson(orders.get(i)));
            if (i < orders.size() - 1) json.append(",");
        }
        json.append("]");
        return json.toString();
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

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        System.out.println("[Debug] Extracting '" + key + "': pattern='" + pattern + "', start=" + start);
        if (start == -1) {
            System.out.println("[Debug] Pattern not found, returning empty string");
            return "";
        }
        
        start += pattern.length();
        int end = json.indexOf("\"", start);
        System.out.println("[Debug] After pattern, start=" + start + ", end=" + end);
        if (end == -1) {
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
