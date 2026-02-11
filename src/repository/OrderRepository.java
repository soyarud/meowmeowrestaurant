package repository;

import domain.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple in-memory OrderRepository used by the server.
 */
public class OrderRepository {
    private final List<Order> orders = new ArrayList<>();
    private int nextId = 1;

    public OrderRepository() {
    }

    public synchronized int getNextOrderId() {
        return nextId++;
    }

    public synchronized void save(Order order) {
        // Replace existing order with same id or add
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).getOrderId() == order.getOrderId()) {
                orders.set(i, order);
                return;
            }
        }
        orders.add(order);
    }

    public synchronized List<Order> getAllOrders() {
        return Collections.unmodifiableList(new ArrayList<>(orders));
    }

    public synchronized Order findById(int id) {
        for (Order o : orders) {
            if (o.getOrderId() == id) return o;
        }
        return null;
    }

    // Allow initializing nextId after loading existing orders from the database
    public synchronized void setNextId(int nextId) {
        this.nextId = nextId;
    }
}
