package ecommerce.ecommercesystem;
import java.util.*;
import java.text.SimpleDateFormat;

class ExpiryHelper {
    public static Date getExpiryDate(String expiry) {
        if (expiry == null || expiry.isEmpty()) {
            return null; 
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
        switch (unit) {
            case 'd':
                timeDiff = value * 24L * 60 * 60 * 1000;
                break;
            case 'h':
                timeDiff = value * 60L * 60 * 1000;
                break;
            case 'm': // months
                timeDiff = value * 30L * 24 * 60 * 60 * 1000;
                break;
            default:
                System.out.println("Unknown unit: " + unit + ". Using days.");
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

// Base Product class
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
    public Date getExpiryDate() { return expiryDate; }
}

// Interface for shippable items
interface Shippable {
    String getName();
    double getWeight();
}

// Products that require shipping
class ShippableProduct extends Product implements Shippable {
    private double weight; // in kg
    
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

// Products that don't require shipping (digital products)
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

// Shopping cart implementation
class Cart {
    private Map<Product, Integer> items = new HashMap<>();
    
    public void add(Product product, int quantity) throws Exception {
        if (product.isExpired()) {
            throw new Exception("Cannot add expired product: " + product.getName());
        }
        
        if (product.getQuantity() < quantity) {
            throw new Exception("Not enough stock for " + product.getName() + 
                ". Available: " + product.getQuantity() + ", Requested: " + quantity);
        }
        
        items.put(product, items.getOrDefault(product, 0) + quantity);
        System.out.println("Added " + quantity + "x " + product.getName() + " to cart");
    }
    
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    public Map<Product, Integer> getItems() {
        return items;
    }
    
    public double getSubtotal() {
        double subtotal = 0;
        for (Map.Entry<Product, Integer> entry : items.entrySet()) {
            subtotal += entry.getKey().getPrice() * entry.getValue();
        }
        return subtotal;
    }
    
    public void clear() {
        items.clear();
    }
}

// Shipping service as specified in requirements
class ShippingService {
    private static final double SHIPPING_FEE = 30.0;
    
    public static void ship(List<Shippable> items) {
        if (items.isEmpty()) return;
        
        System.out.println("** Shipment notice **");
        
        // Group items by name and calculate weights
        Map<String, Integer> itemCounts = new HashMap<>();
        Map<String, Double> itemWeights = new HashMap<>();
        
        for (Shippable item : items) {
            String name = item.getName();
            itemCounts.put(name, itemCounts.getOrDefault(name, 0) + 1);
            itemWeights.putIfAbsent(name, item.getWeight());
        }
        
        double totalWeight = 0;
        for (String itemName : itemCounts.keySet()) {
            int count = itemCounts.get(itemName);
            double unitWeight = itemWeights.get(itemName);
            double totalItemWeight = unitWeight * count;
            totalWeight += totalItemWeight;
            
            System.out.printf("%dx %s %.0fg\n", count, itemName, totalItemWeight * 1000);
        }
        
        System.out.printf("Total package weight %.1fkg\n", totalWeight);
    }
    
    public static double getShippingFee() {
        return SHIPPING_FEE;
    }
}

// Customer class
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

// Main checkout function
public class EcommerceSystem {
    
    public static void checkout(Customer customer, Cart cart) {
        if (cart.isEmpty()) {
            System.out.println("Error: Cart is empty");
            return;
        }
        
        // Check for expired products and stock availability
        List<Shippable> shippableItems = new ArrayList<>();
        
        for (Map.Entry<Product, Integer> entry : cart.getItems().entrySet()) {
            Product product = entry.getKey();
            int quantity = entry.getValue();
            
            if (product.isExpired()) {
                System.out.println("Error: " + product.getName() + " is expired");
                return;
            }
            
            if (product.getQuantity() < quantity) {
                System.out.println("Error: " + product.getName() + " is out of stock");
                return;
            }
            
            // Collect shippable items
            if (product.requiresShipping()) {
                for (int i = 0; i < quantity; i++) {
                    shippableItems.add((Shippable) product);
                }
            }
        }
        
        double subtotal = cart.getSubtotal();
        double shippingFees = shippableItems.isEmpty() ? 0 : ShippingService.getShippingFee();
        double totalAmount = subtotal + shippingFees;
        
        if (customer.getBalance() < totalAmount) {
            System.out.println("Error: Customer's balance is insufficient");
            return;
        }
        
        // Process shipping if needed
        if (!shippableItems.isEmpty()) {
            ShippingService.ship(shippableItems);
        }
        
        // Print checkout receipt
        System.out.println("** Checkout receipt **");
        for (Map.Entry<Product, Integer> entry : cart.getItems().entrySet()) {
            Product product = entry.getKey();
            int quantity = entry.getValue();
            double lineTotal = product.getPrice() * quantity;
            System.out.printf("%dx %s %.0f\n", quantity, product.getName(), lineTotal);
        }
        
        System.out.println("----------------------");
        System.out.printf("Subtotal %.0f\n", subtotal);
        System.out.printf("Shipping %.0f\n", shippingFees);
        System.out.printf("Amount %.0f\n", totalAmount);
        
        // Process payment
        customer.pay(totalAmount);
        
        // Update inventory
        for (Map.Entry<Product, Integer> entry : cart.getItems().entrySet()) {
            entry.getKey().reduceQuantity(entry.getValue());
        }
        
        System.out.println("Customer balance after payment: " + customer.getBalance());
        cart.clear();
    }
    
    public static void main(String[] args) {
        // Create products as specified in requirements
        Product cheese = new ShippableProduct("Cheese", 100, 10, 0.2, "15d");
        Product biscuits = new ShippableProduct("Biscuits", 150, 8, 0.7, "30d");
        Product tv = new ShippableProduct("TV", 5000, 5, 15.0);
        Product mobile = new ShippableProduct("Mobile", 3000, 3, 0.5);
        Product scratchCard = new DigitalProduct("Mobile Scratch Card", 50, 100);
        
        // Test case 1: Normal checkout with shipping
        System.out.println("=== Test Case 1: Normal Checkout ===");
        Customer customer1 = new Customer("John", 500);
        Cart cart1 = new Cart();
        
        try {
            cart1.add(cheese, 2);
            cart1.add(biscuits, 1);
            cart1.add(scratchCard, 1);
            checkout(customer1, cart1);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        
        System.out.println("\n=== Test Case 2: Mixed Products ===");
        Customer customer2 = new Customer("Alice", 1000);
        Cart cart2 = new Cart();
        
        try {
            cart2.add(cheese, 2);
            cart2.add(tv, 1);
            cart2.add(scratchCard, 1);
            checkout(customer2, cart2);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        
        System.out.println("\n=== Test Case 3: Empty Cart ===");
        Customer customer3 = new Customer("Bob", 500);
        Cart cart3 = new Cart();
        checkout(customer3, cart3);
        
        System.out.println("\n=== Test Case 4: Insufficient Balance ===");
        Customer customer4 = new Customer("Charlie", 100);
        Cart cart4 = new Cart();
        
        try {
            cart4.add(tv, 1);
            checkout(customer4, cart4);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        
        System.out.println("\n=== Test Case 5: Out of Stock ===");
        Customer customer5 = new Customer("Dave", 2000);
        Cart cart5 = new Cart();
        
        try {
            cart5.add(mobile, 5); // Only 3 available
            checkout(customer5, cart5);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        
        // Test case 6: Expired product
        System.out.println("\n=== Test Case 6: Expired Product ===");
        Product expiredCheese = new ShippableProduct("Expired Cheese", 100, 5, 0.2, "-2d");
        Customer customer6 = new Customer("Eve", 300);
        Cart cart6 = new Cart();
        
        try {
            cart6.add(expiredCheese, 1);
            checkout(customer6, cart6);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}