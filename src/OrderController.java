import java.util.List;

/**
 * OrderController - coordinates between repositories and higher-level logic.
 */
public class OrderController {
    private final OrderRepository orderRepository;
    private final MenuItemRepository menuRepository;

    public OrderController(OrderRepository orderRepository, MenuItemRepository menuRepository) {
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
    }

    public List<Order> getAllOrders() {
        return orderRepository.getAllOrders();
    }

    public void saveOrder(Order order) {
        orderRepository.save(order);
    }

    public MenuItemRepository getMenuRepository() {
        return menuRepository;
    }

    // Simple inner type used by RestServer.parseOrderItems
    public static class OrderItemRequest {
        public final int menuItemId;
        public final int quantity;

        public OrderItemRequest(int menuItemId, int quantity) {
            this.menuItemId = menuItemId;
            this.quantity = quantity;
        }
    }
}
