public class Main {
    public static void main(String[] args) {
        // Print program header
        System.out.println("=== RESTAURANT ORDER MANAGEMENT SYSTEM ===\n");

        // 1. CREATE RESTAURANT OBJECT (constructor demonstration)
        Restaurant italianRestaurant = new Restaurant("Bella Italia");
        System.out.println("Restaurant created: " + italianRestaurant.getName());

        // 2. CREATE MENU ITEMS (creating multiple objects)
        System.out.println("\n--- Creating menu items ---");
        System.out.println("\n--- Creating menu items ---");
        MenuItem pizza = new MainCourse(1, "Margherita Pizza",
                "Classic pizza with tomato sauce and mozzarella", 12.99);
        MenuItem pasta = new MainCourse(2, "Spaghetti Carbonara",
                "Pasta with eggs, cheese, and pancetta", 14.50);
        MenuItem salad = new Appetizer(3, "Caesar Salad",
                "Romaine lettuce with croutons and Caesar dressing", 8.75);
        MenuItem tiramisu = new Dessert(4, "Tiramisu",
                "Italian coffee-flavored dessert", 6.99);
        MenuItem cola = new Drink(5, "Coca-Cola",
                "Refreshing soft drink", 2.50, false);
        MenuItem wine = new Drink(6, "House Red Wine",
                "Full-bodied red wine", 7.50, true);

        // 3. ADD ITEMS TO RESTAURANT MENU
        italianRestaurant.addMenuItem(pizza);
        italianRestaurant.addMenuItem(pasta);
        italianRestaurant.addMenuItem(salad);
        italianRestaurant.addMenuItem(tiramisu);
        italianRestaurant.addMenuItem(cola);
        italianRestaurant.addMenuItem(wine);
        // REPLACED THE OLD CODE, AND NOW IT USES INHERITANCE AND POLYMORPHISM

        // 4. DISPLAY MENU (output to console)
        italianRestaurant.displayMenu();

        // 5. CREATE ORDERS (creating instances of classes)
        System.out.println("\n--- Creating orders ---");
        Order order1 = italianRestaurant.createOrder();
        Order order2 = italianRestaurant.createOrder();

        System.out.println("Order #" + order1.getOrderId() + " created");
        System.out.println("Order #" + order2.getOrderId() + " created");

        // 6. ADD ITEMS TO ORDERS (using methods)
        System.out.println("\n--- Adding items to orders ---");
        order1.addItem(pizza);
        order1.addItem(cola);
        order1.addItem(tiramisu);

        order2.addItem(pasta);
        order2.addItem(salad);
        order2.addItem(cola);

        // 7. CHANGE ORDER STATUSES (using setters)
        order1.setStatus("IN PROGRESS");
        order2.setStatus("IN PROGRESS");

        // 8. DISPLAY ORDERS (output to console requirement)
        System.out.println("\n--- Displaying orders ---");
        order1.displayOrder();
        order2.displayOrder();

        // 9. COMPARING OBJECTS (comparison requirement)
        System.out.println("\n=== COMPARING OBJECTS ===");

        // 9.1 Compare by ID
        System.out.println("1. Comparing order IDs:");
        System.out.println("   Order1 ID == Order2 ID? " +
                (order1.getOrderId() == order2.getOrderId()));

        // 9.2 Compare by total price
        System.out.println("\n2. Comparing order totals:");
        System.out.println("   Order1 total: $" + order1.getTotalPrice());
        System.out.println("   Order2 total: $" + order2.getTotalPrice());
        System.out.println("   Order1 total > Order2 total? " +
                (order1.getTotalPrice() > order2.getTotalPrice()));

        // 9.3 Compare by status
        System.out.println("\n3. Comparing order statuses:");
        System.out.println("   Order1 status: " + order1.getStatus());
        System.out.println("   Order2 status: " + order2.getStatus());
        System.out.println("   Same status? " +
                order1.getStatus().equals(order2.getStatus()));

        // 9.4 Compare object references
        System.out.println("\n4. Are order1 and order2 the same object?");
        System.out.println("   Result: " + (order1 == order2));

        // 10. DEMONSTRATE SETTERS (getter/setter requirement)
        System.out.println("\n=== DEMONSTRATING SETTERS ===");
        System.out.println("Original price of pizza: $" + pizza.getPrice());
        pizza.setPrice(13.49); // Using setter method
        System.out.println("New price of pizza: $" + pizza.getPrice());

        // 11. DEMONSTRATE GETTERS (getter/setter requirement)
        System.out.println("\n=== DEMONSTRATING GETTERS ===");
        System.out.println("Getting pizza name: " + pizza.getName());
        System.out.println("Getting pizza category: " + pizza.getCategory());

        // 12. FIND ORDER BY ID (method demonstration)
        System.out.println("\n=== FINDING ORDER BY ID ===");
        Order foundOrder = italianRestaurant.findOrderById(1);
        if (foundOrder != null) {
            System.out.println("Found order #" + foundOrder.getOrderId());
            System.out.println("Total: $" + foundOrder.getTotalPrice());
        }

        System.out.println("\n=== ASSIGNMENT 2 FEATURES DEMO ===");

        System.out.println("Menu (using toString()):");
        for (MenuItem item : italianRestaurant.getMenu()) {
            System.out.println("  " + item);
        }

        System.out.println("\nDrinks only:");
        italianRestaurant.filterByCategory("Drink")
                .forEach(item -> System.out.println("  " + item));

        System.out.println("\nItems ≤ $10:");
        italianRestaurant.filterByMaxPrice(10.00)
                .forEach(item -> System.out.println("  " + item));

        System.out.println("\nSearch 'pizza':");
        italianRestaurant.searchByName("pizza")
                .forEach(item -> System.out.println("  " + item));

        italianRestaurant.sortMenuByPrice();
        System.out.println("\nMenu sorted by price:");
        italianRestaurant.displayMenu();

        // 13. FINAL SUMMARY
        System.out.println("\n=== FINAL SUMMARY ===");
        System.out.println("Restaurant: " + italianRestaurant.getName());
        System.out.println("Menu items: " + italianRestaurant.getMenu().size());
        System.out.println("Active orders: " + italianRestaurant.getOrders().size());
        System.out.println("\n=== SYSTEM READY ===");


        System.out.println("\n=== ASSIGNMENT 3: DATABASE DEMONSTRATION ===");

        DatabaseManager db = new DatabaseManager();

        // 1. Insert some menu items into DB
        db.insertMenuItem("Margherita Pizza", "Classic pizza with tomato and mozzarella", 12.99, "Main");
        db.insertMenuItem("Coca-Cola", "Refreshing soft drink", 2.50, "Drink");
        db.insertMenuItem("Tiramisu", "Italian coffee-flavored dessert", 6.99, "Dessert");

        // 2. Read and display all items from DB
        db.readMenuItems();

        // 3. Update price
        db.updateMenuItemPrice("Margherita Pizza", 14.99);

        // 4. Read again to see change
        db.readMenuItems();

        // 5. Delete one item
        db.deleteMenuItem("Coca-Cola");

        // 6. Final read
        db.readMenuItems();

        System.out.println("\n=== DATABASE OPERATIONS COMPLETED ===");



        System.out.println("\n=== DATABASE RESET FOR 4TH ASSIGNMENT ===");

        // 1. WIPE THE SLATE CLEAN (This deletes all old/bad data)
        System.out.println("Recreating tables...");
        db.recreateTables();

        // 2. INSERT CLEAN, PROFESSIONAL DATA
        System.out.println("Inserting fresh menu items...");

        // Main Courses
        db.insertMenuItem("Margherita pizza", "most popular pizza with pomidors", 12.99, "Main");
        db.insertMenuItem("Carbonara pasta", "spaghetti yk yk", 14.50, "Main");
        db.insertMenuItem("Grilled salmon", "salmon from Balkhash", 18.99, "Main");

        // Appetizers
        db.insertMenuItem("Oliv'e", "kolbasa, egg, cucumber, mayonez", 6.50, "Appetizer");
        db.insertMenuItem("Caesar salad", "imagine: you died to become a salad", 8.99, "Appetizer");

        // Desserts
        db.insertMenuItem("Tiramisu", "tiramisu lol", 7.50, "Dessert");
        db.insertMenuItem("Cheesecake", "cheese and cake", 6.50, "Dessert");

        // Drinks
        db.insertMenuItem("Koka kola", "it's not a coca-cola", 2.50, "Drink");
        db.insertMenuItem("Red wine", "100 years wine lol", 1000.00, "Drink");
        db.insertMenuItem("Botol of woter", "woter", 1.00, "Drink");

        // 3. VERIFY
        System.out.println("\nCurrent Database Content:");
        db.readMenuItems();

        System.out.println("=== DATA RESET COMPLETE ===");
    }
}