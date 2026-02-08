public class Drink extends MenuItem {
    private boolean alcoholic;

    public Drink(int id, String name, String description, double price, boolean alcoholic) {
        super(id, name, description, price, "Drink");
        this.alcoholic = alcoholic;
    }

    public boolean isAlcoholic() {
        return alcoholic;
    }
}