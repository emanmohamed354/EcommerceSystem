package ecommerce.ecommercesystem;
import java.util.*;
import java.util.stream.*;


class ExpiryHelper {
    public static Date getExpiryDate(String expiry) {
        if (expiry == null || expiry.isEmpty()) {
            return null; // no expiry
        }
        
        long now = System.currentTimeMillis();
        
        boolean isPast = expiry.startsWith("-");
        if (isPast) {
            expiry = expiry.substring(1); 
        }
        
        char unit = expiry.charAt(expiry.length() - 1);
        String numberPart = expiry.substring(0, expiry.length() - 1);
        int value;
        
        try {
            value = Integer.parseInt(numberPart);
        } catch (NumberFormatException e) {
            System.out.println("Invalid expiry format: " + expiry);
            return null;
        }
        
        long timeDiff = 0;
        if (unit == 'd') {
            timeDiff = value * 24L * 60 * 60 * 1000;
        } else if (unit == 'h') {
            timeDiff = value * 60L * 60 * 1000;
        } else if (unit == 'm') {
            timeDiff = value * 30L * 24 * 60 * 60 * 1000;
        } else {
            System.out.println("Unknown unit: " + unit + ". Using days as default.");
            timeDiff = value * 24L * 60 * 60 * 1000;
        }
        
        if (isPast) {
            now -= timeDiff; 
        } else {
            now += timeDiff; 
        }
        
        return new Date(now);
    }
    
    public static boolean isExpired(Date expiryDate) {
        if (expiryDate == null) return false;
        return new Date().after(expiryDate);
    }
}

abstract class Product {
    protected String name;
    protected double price;
    protected int quantity;
    protected Date expiryDate;
    
    public Product(String name, double price, int quantity) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.expiryDate = null; 
    }
    
    public Product(String name, double price, int quantity, String expiryTime) {
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.expiryDate = ExpiryHelper.getExpiryDate(expiryTime);
    }
    
    public boolean isAvailable(int requestedQty) {
        if (quantity < requestedQty) return false;
        if (ExpiryHelper.isExpired(expiryDate)) return false;
        return true;
    }
    
    public boolean isExpired() {
        return ExpiryHelper.isExpired(expiryDate);
    }
    
    public void reduceQuantity(int qty) {
        this.quantity -= qty;
    }
    
    public abstract boolean requiresShipping();
    
    public String getName() { return name; }
    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
}

interface Shippable {
    String getName();
    double getWeight();
}

class ShippableProduct extends Product implements Shippable {
    private double weight;
    
    public ShippableProduct(String name, double price, int quantity, double weight) {
        super(name, price, quantity);
        this.weight = weight;
    }
    
    public ShippableProduct(String name, double price, int quantity, double weight, String expiryTime) {
        super(name, price, quantity, expiryTime);
        this.weight = weight;
    }
    
    @Override
    public boolean requiresShipping() {
        return true;
    }
    
    @Override
    public double getWeight() {
        return weight;
    }
}

class DigitalProduct extends Product {
    
    public DigitalProduct(String name, double price, int quantity) {
        super(name, price, quantity);
    }
    
    public DigitalProduct(String name, double price, int quantity, String expiryTime) {
        super(name, price, quantity, expiryTime);
    }
    
    @Override
    public boolean requiresShipping() {
        return false;
    }
}

class Cart {
    private Map<Product, Integer> items = new HashMap<>();
    
    public void add(Product product, int quantity) throws Exception {
        if (product.isExpired()) {
            throw new Exception("Cannot add expired product " + product.getName());
        }
        
        if (product.getQuantity() < quantity) {
            throw new Exception("Not enough stock for " + product.getName() + 
                " Available " + product.getQuantity() + ",but the Requested " + quantity);
        }
        
        if (items.containsKey(product)) {
            items.put(product, items.get(product) + quantity);
        } else {
            items.put(product, quantity);
        }
        
        System.out.println("Added " + quantity + " from " + product.getName() + " to cart");
    }
    
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    public Map<Product, Integer> getItems() {
        return items;
    }
    
    public double getTotal() {
        double total = 0;
        for (Map.Entry<Product, Integer> item : items.entrySet()) {
            total += item.getKey().getPrice() * item.getValue();
        }
        return total;
    }
    
    public void clear() {
        items.clear();
    }
}

class ShippingService {
    private static final double SHIPPING_COST = 30.0;
    
    public static void shipItems(List<Shippable> items) {
        if (items.isEmpty()) return;
        
        System.out.println("\n** Shipment notice **");
        
        
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Double> weights = new HashMap<>();
        
        for (Shippable item : items) {
            String name = item.getName();
            counts.put(name, counts.getOrDefault(name, 0) + 1);
            weights.putIfAbsent(name, item.getWeight());
        }
        
        double totalWeight = 0;
        for (String productName : counts.keySet()) {
            int count = counts.get(productName);
            double weight = weights.get(productName) * count;
            totalWeight += weight;
            
            System.out.printf("%dx %-13s %.0fg\n", count, productName, weight * 1000);
        }
        
        System.out.printf("Total package weight %.1fkg\n", totalWeight);
    }
    
    public static double getShippingCost(List<Shippable> items) {
        return items.isEmpty() ? 0 : SHIPPING_COST;
    }
}

class Customer {
    private String name;
    private double balance;
    
    public Customer(String name, double balance) {
        this.name = name;
        this.balance = balance;
    }
    
    public boolean pay(double amount) {
        if (balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }
    
    public double getBalance() { return balance; }
    public String getName() { return name; }
}

class Checkout {
    public static void processCheckout(Customer customer, Cart cart) {
        System.out.println("\nthis is checkout for " + customer.getName());
        
        if (cart.isEmpty()) {
            System.out.println("Error: Cart is empty!!!!!");
            return;
        }
        
        List<Shippable> toShip = new ArrayList<>();
        
        for (Map.Entry<Product, Integer> entry : cart.getItems().entrySet()) {
            Product product = entry.getKey();
            int qty = entry.getValue();
            
            if (product.isExpired()) {
                System.out.println("Error: " + product.getName() + " is expired!");
                return;
            }
            
            if (product.getQuantity() < qty) {
                System.out.println("Error: Not enough " + product.getName() + " in stock!!!!");
                return;
            }
            
            if (product.requiresShipping()) {
                for (int i = 0; i < qty; i++) {
                    toShip.add((Shippable) product);
                }
            }
        }
        
        double total = cart.getTotal();
        double shipping = ShippingService.getShippingCost(toShip);
        double totals = total + shipping;
        
        if (customer.getBalance() < totals) {
            System.out.println("Error: Insufficient balance, Needed: " + totals + ", Have: " + customer.getBalance());
            return;
        }
        
        ShippingService.shipItems(toShip);
        
        System.out.println("\n ** Checkout receipt ** ");
        for (Map.Entry<Product, Integer> entry : cart.getItems().entrySet()) {
            Product p = entry.getKey();
            int qty = entry.getValue();
            System.out.printf("%dx %-13s %.0f\n", qty, p.getName(), p.getPrice() * qty);
        }
        System.out.printf("%-15s %.0f\n", "Subtotal", total);
        System.out.printf("%-15s %.0f\n", "Shipping", shipping);
        System.out.printf("%-15s %.0f\n", "Amount", totals);
        
        customer.pay(totals);
        
        for (Map.Entry<Product, Integer> entry : cart.getItems().entrySet()) {
            entry.getKey().reduceQuantity(entry.getValue());
        }
        
        cart.clear();
        System.out.println("\n------------------------------");
        System.out.println("\nPayment successful!");
        System.out.println("Remaining balance: " + customer.getBalance());
        System.out.println("\n------------------------------");
    }
}

public class EcommerceSystem {
    public static void main(String[] args) {
        Product cheese = new ShippableProduct("Cheese", 100, 10, 0.2, "7d");
        Product biscuits = new ShippableProduct("Biscuits", 150, 15, 0.7, "30d");
        Product tv = new ShippableProduct("TV", 5000, 3, 15.0); 
        Product scratchCard = new DigitalProduct("Scratch Card", 50, 100, "90d");
        
        Product expiredMilk = new ShippableProduct("Milk", 80, 5, 1.0, "-2d"); 
        System.out.println("\n-------------------------");
        // Test case 1: Normal checkout
        System.out.println("=== Test 1: Normal Checkout ===");
        Customer john = new Customer("John", 500);
        Cart cart1 = new Cart();
        
        try {
            cart1.add(cheese, 2);
            cart1.add(biscuits, 1);
            Checkout.processCheckout(john, cart1);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println("\n-------------------------");
        // Test case 2: Mixed products
        System.out.println("\n\n=== Test 2: Mixed Products ===");
        Customer alice = new Customer("Alice", 300);
        Cart cart2 = new Cart();
        
        try {
            cart2.add(cheese, 1);
            cart2.add(scratchCard, 2);
            Checkout.processCheckout(alice, cart2);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println("\n-------------------------");
        // Test case 3: Empty cart
        System.out.println("\n\n=== Test 3: Empty Cart ===");
        Customer bob = new Customer("Bob", 1000);
        Cart cart3 = new Cart();
        Checkout.processCheckout(bob, cart3);
        System.out.println("\n-------------------------");
        // Test case 4: Not enough money
        System.out.println("\n\n=== Test 4: Insufficient Balance ===");
        Customer charlie = new Customer("Charlie", 50);
        Cart cart4 = new Cart();
        
        try {
            cart4.add(cheese, 2);
            Checkout.processCheckout(charlie, cart4);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        System.out.println("\n-------------------------");

        // Test case 5: Expired product
        System.out.println("\n\n=== Test 5: Expired Product ===");
        Customer dave = new Customer("Dave", 200);
        Cart cart5 = new Cart();
        
        try {
            cart5.add(expiredMilk, 1);
            Checkout.processCheckout(dave, cart5);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}