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
        return orders.stream()
                .filter(o -> o.getOrderId() == id)
                .findFirst()
                .orElse(null);
    }

    public synchronized boolean deleteById(int id) {
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).getOrderId() == id) {
                orders.remove(i);
                return true;
            }
        }
        return false;
    }

    // Allow initializing nextId after loading existing orders from the database
    public synchronized void setNextId(int nextId) {
        this.nextId = nextId;
    }
}
