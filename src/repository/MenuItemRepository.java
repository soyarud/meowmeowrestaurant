package repository;

import domain.MenuItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal MenuItemRepository. Currently uses an in-memory list.
 * The REST API in `RestServer` reads menu items from the database directly,
 * but other code can use this repository if desired.
 */
public class MenuItemRepository {
    private final List<MenuItem> menu = new ArrayList<>();

    public MenuItemRepository() {
        // Optionally seed with some default items if none in DB
    }

    public synchronized void save(MenuItem item) {
        for (int i = 0; i < menu.size(); i++) {
            if (menu.get(i).getId() == item.getId()) {
                menu.set(i, item);
                return;
            }
        }
        menu.add(item);
    }

    public synchronized List<MenuItem> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(menu));
    }

    public synchronized MenuItem findById(int id) {
        return menu.stream()
                .filter(m -> m.getId() == id)
                .findFirst()
                .orElse(null);
    }
}
