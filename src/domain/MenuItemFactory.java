package domain;

/**
 * Simple factory to create MenuItem instances based on category
 */
public class MenuItemFactory {
    public static MenuItem create(int id, String name, String description, double price, String category) {
        if (category == null) category = "Other";
        switch (category.trim().toLowerCase()) {
            case "appetizer":
                return new Appetizer(id, name, description, price);
            case "main":
            case "maincourse":
                return new MainCourse(id, name, description, price);
            case "dessert":
                return new Dessert(id, name, description, price);
            case "drink":
                // default alcoholic = false for factory-created drinks
                return new Drink(id, name, description, price, false);
            default:
                return new MenuItem(id, name, description, price, category);
        }
    }
}
