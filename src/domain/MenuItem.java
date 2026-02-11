package domain;

import java.util.Objects;

public class MenuItem {
    // Attributes (class fields)
    private int id;
    private String name;
    private String description;
    private double price;
    private String category; // "Appetizer", "Main", "Dessert", "Drink"

    // Constructor
    public MenuItem(int id, String name, String description, double price, String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
    }

    // Getter and setter methods
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    // Method to display item information
    public void displayInfo() {
        System.out.println("[" + id + "] " + name + " - $" + price);
        System.out.println("   " + description);
        System.out.println("   Category: " + category);
    }

    @Override
    public String toString() {
        return String.format("%-25s $%.2f (%s)", name, price, category);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MenuItem menuItem = (MenuItem) o;
        return id == menuItem.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
