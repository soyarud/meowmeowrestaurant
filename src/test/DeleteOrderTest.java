package test;

import service.DatabaseManager;

/**
 * Simple integration test for deleteOrder()
 * Run with: `java -cp out;lib/* test.DeleteOrderTest` (adjust classpath for your setup)
 */
public class DeleteOrderTest {
    public static void main(String[] args) {
        DatabaseManager db = new DatabaseManager();
        int id = db.createOrder("DeleteTestUser");
        if (id == -1) {
            System.err.println("FAILED: createOrder returned -1");
            System.exit(2);
        }

        boolean deleted = db.deleteOrder(id);
        if (!deleted) {
            System.err.println("FAILED: deleteOrder returned false for id " + id);
            System.exit(1);
        }

        System.out.println("PASS: deleteOrder succeeded for id " + id);
        System.exit(0);
    }
}
