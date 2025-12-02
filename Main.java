package src;// src.Main.java
// CS157A Final Project

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class Main {

    private static final String PROPERTIES_FILE = "app.properties";

    public static void main(String[] args) {
        Properties props = new Properties();
        // added error detection
        try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            System.err.println("Could not read " + PROPERTIES_FILE + ": " + e.getMessage());
            System.err.println("Make sure the file exists and is in the working directory.");
            return;
        }
        String url =props.getProperty("db.url");
        String user = props.getProperty("db.user");
        String pass = props.getProperty("db.password");

        if (url == null || user == null) {
            System.err.println("db.url or db.user missing from app.properties.");
            return;
        }

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to database.");
            boolean running = true;

            while (running) {
                printMenu();
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1":
                        listCustomers(conn);
                        break;
                    case "2":
                        addCustomer(conn, scanner);
                        break;
                    case "3":
                        updateCustomerPhone(conn, scanner);
                        break;
                    case "4":
                        deleteCustomer(conn, scanner);
                        break;
                    case "5":
                        listPurchases(conn);
                        break;
                    case "6":
                        createPurchaseTransaction(conn, scanner);
                        break;
                    case "7":
                        viewWarehouseStock(conn);
                        break;
                    case "0":
                        running = false;
                        System.out.println("Goodbye!");
                        break;
                    default:
                        System.out.println("Invalid choice.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error:");
            e.printStackTrace();
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("==== CS157A Warehouse Console ====");
        System.out.println("1) List all customers");
        System.out.println("2) Add a new customer");
        System.out.println("3) Update a customer's phone");
        System.out.println("4) Delete a customer");
        System.out.println("5) List purchases with customer names");
        System.out.println("6) Create a new purchase (transactional)");
        System.out.println("7) View warehouse stock by item");
        System.out.println("0) Exit");
        System.out.print("Enter choice: ");
    }

    // ---------- Menu actions -------------------------------------------------

    private static void listCustomers(Connection conn) {
        String sql = "SELECT MembershipID, Name, DateOfBirth, Email, Phone " +
                "FROM Customer ORDER BY MembershipID";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            printResultSet(rs);
        } catch (SQLException e) {
            System.out.println("Error listing customers: " + e.getMessage());
        }
    }

    private static void addCustomer(Connection conn, Scanner scanner) {
        int membershipId = readInt(scanner, "Enter new membership ID (int): ");
        String name = readNonEmpty(scanner, "Enter customer name: ");
        LocalDate dob = readDate(scanner, "Enter date of birth (YYYY-MM-DD): ");
        String email = readNonEmpty(scanner, "Enter email: ");
        String phone = readNonEmpty(scanner, "Enter phone: ");

        String sql = "INSERT INTO Customer (MembershipID, Name, DateOfBirth, Email, Phone) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, membershipId);
            ps.setString(2, name);
            ps.setDate(3, Date.valueOf(dob));
            ps.setString(4, email);
            ps.setString(5, phone);
            int rows = ps.executeUpdate();
            System.out.println("Inserted " + rows + " customer(s).");
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                System.out.println("Constraint violation (duplicate ID or email).");
            } else {
                System.out.println("Error inserting customer: " + e.getMessage());
            }
        }
    }

    private static void updateCustomerPhone(Connection conn, Scanner scanner) {
        int membershipId = readInt(scanner, "Enter membership ID to update: ");
        String phone = readNonEmpty(scanner, "Enter new phone number: ");

        String sql = "UPDATE Customer SET Phone = ? WHERE MembershipID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setInt(2, membershipId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                System.out.println("No customer found with that membership ID.");
            } else {
                System.out.println("Updated " + rows + " row(s).");
            }
        } catch (SQLException e) {
            System.out.println("Error updating phone: " + e.getMessage());
        }
    }

    private static void deleteCustomer(Connection conn, Scanner scanner) {
        int membershipId = readInt(scanner, "Enter membership ID to delete: ");
        System.out.println("Warning: delete will fail if the customer has existing purchases.");

        String sql = "DELETE FROM Customer WHERE MembershipID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, membershipId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                System.out.println("No customer deleted (check ID or existing purchases).");
            } else {
                System.out.println("Deleted " + rows + " customer(s).");
            }
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                System.out.println("Cannot delete due to foreign key constraint (existing purchases).");
            } else {
                System.out.println("Error deleting customer: " + e.getMessage());
            }
        }
    }

    private static void listPurchases(Connection conn) {
        String sql =
                "SELECT p.TransactionID, c.Name AS CustomerName, p.Date, p.Total " +
                        "FROM Purchases p " +
                        "JOIN Customer c ON p.MembershipID = c.MembershipID " +
                        "ORDER BY p.TransactionID";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            printResultSet(rs);
        } catch (SQLException e) {
            System.out.println("Error listing purchases: " + e.getMessage());
        }
    }

    /**
     * Transactional workflow:
     * - verify customer and item exist
     * - verify enough stock exists in Manages
     * - compute total from WarehouseItem.UnitPrice * quantity
     * - INSERT into Purchases
     * - INSERT into TransactionLineItem
     * - Trigger will decrement stock
     * If any step fails -> rollback.
     */
    private static void createPurchaseTransaction(Connection conn, Scanner scanner) {
        int membershipId = readInt(scanner, "Enter customer membership ID: ");
        int itemId = readInt(scanner, "Enter item ID to purchase: ");
        int quantity = readInt(scanner, "Enter quantity (>0): ");

        if (quantity <= 0) {
            System.out.println("Quantity must be greater than 0.");
            return;
        }

        try {
            conn.setAutoCommit(false); // begin transaction

            if (!exists(conn, "SELECT 1 FROM Customer WHERE MembershipID = ?", membershipId)) {
                System.out.println("No customer with that membership ID.");
                conn.rollback();
                conn.setAutoCommit(true);
                return;
            }

            if (!exists(conn, "SELECT 1 FROM WarehouseItem WHERE ItemID = ?", itemId)) {
                System.out.println("No item with that ID.");
                conn.rollback();
                conn.setAutoCommit(true);
                return;
            }

            int totalStock = queryForInt(conn,
                    "SELECT COALESCE(SUM(Stock), 0) FROM Manages WHERE ItemID = ?",
                    itemId);

            if (totalStock < quantity) {
                System.out.println("Insufficient stock. Available: " + totalStock);
                conn.rollback();
                conn.setAutoCommit(true);
                return;
            }

            BigDecimal unitPrice = queryForBigDecimal(conn,
                    "SELECT UnitPrice FROM WarehouseItem WHERE ItemID = ?",
                    itemId);

            if (unitPrice == null) {
                System.out.println("Could not find unit price for item.");
                conn.rollback();
                conn.setAutoCommit(true);
                return;
            }

            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

            // Insert purchase row
            String insertPurchase =
                    "INSERT INTO Purchases (MembershipID, Date, Total) VALUES (?, ?, ?)";
            int newTransactionId;

            try (PreparedStatement ps = conn.prepareStatement(
                    insertPurchase, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, membershipId);
                ps.setDate(2, Date.valueOf(LocalDate.now()));
                ps.setBigDecimal(3, totalPrice);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        newTransactionId = keys.getInt(1);
                    } else {
                        throw new SQLException("No generated key returned for Purchases.");
                    }
                }
            }

            // Insert line item row (LineNumber = 1 for this simple example)
            String insertLine =
                    "INSERT INTO TransactionLineItem (TransactionID, LineNumber, ItemID, Quantity) " +
                            "VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertLine)) {
                ps.setInt(1, newTransactionId);
                ps.setInt(2, 1);
                ps.setInt(3, itemId);
                ps.setInt(4, quantity);
                ps.executeUpdate();
            }

            conn.commit();
            conn.setAutoCommit(true);
            System.out.println("Purchase created and committed. TransactionID = " + newTransactionId);

        } catch (SQLException e) {
            try {
                conn.rollback();
                conn.setAutoCommit(true);
            } catch (SQLException ex) {
                System.out.println("Error during rollback: " + ex.getMessage());
            }
            System.out.println("Error creating purchase. Transaction rolled back: " + e.getMessage());
        }
    }

    private static void viewWarehouseStock(Connection conn) {
        String sql =
                "SELECT w.WarehouseID, w.Location, wi.ItemID, wi.UnitPrice, m.Stock " +
                        "FROM Manages m " +
                        "JOIN Warehouse w ON m.WarehouseID = w.WarehouseID " +
                        "JOIN WarehouseItem wi ON m.ItemID = wi.ItemID " +
                        "ORDER BY w.WarehouseID, wi.ItemID";

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            printResultSet(rs);
        } catch (SQLException e) {
            System.out.println("Error viewing stock: " + e.getMessage());
        }
    }

    // ---------- Helper methods -----------------------------------------------

    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private static String readNonEmpty(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            if (!line.isEmpty()) {
                return line;
            }
            System.out.println("Input may not be empty.");
        }
    }

    private static LocalDate readDate(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return LocalDate.parse(line);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date. Use YYYY-MM-DD.");
            }
        }
    }

    private static boolean exists(Connection conn, String sql, int value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int queryForInt(Connection conn, String sql, int value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    private static BigDecimal queryForBigDecimal(Connection conn, String sql, int value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal bd = rs.getBigDecimal(1);
                    return bd;
                }
                return null;
            }
        }
    }

    // Simple tabular printing that does NOT require scrollable result sets
    private static void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        List<String[]> rows = new ArrayList<>();
        int[] colWidths = new int[columnCount];

        // header widths
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            colWidths[i - 1] = label.length();
        }

        // read all rows into memory & compute widths
        while (rs.next()) {
            String[] row = new String[columnCount];
            for (int i = 1; i <= columnCount; i++) {
                String val = rs.getString(i);
                if (val == null) val = "NULL";
                row[i - 1] = val;
                if (val.length() > colWidths[i - 1]) {
                    colWidths[i - 1] = val.length();
                }
            }
            rows.add(row);
        }

        if (rows.isEmpty()) {
            System.out.println("(no rows)");
            return;
        }

        // build format string
        StringBuilder fmt = new StringBuilder("| ");
        for (int i = 0; i < columnCount; i++) {
            fmt.append("%-").append(colWidths[i]).append("s");
            if (i < columnCount - 1) {
                fmt.append(" | ");
            } else {
                fmt.append(" |");
            }
        }
        String formatStr = fmt.toString();

        // header row
        String[] headers = new String[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            headers[i - 1] = meta.getColumnLabel(i);
        }
        System.out.println(String.format(formatStr, (Object[]) headers));

        // separator
        StringBuilder sep = new StringBuilder("|");
        for (int w : colWidths) {
            sep.append("-".repeat(w + 2)).append("|");
        }
        System.out.println(sep.toString());

        // data rows
        for (String[] row : rows) {
            System.out.println(String.format(formatStr, (Object[]) row));
        }
    }
}
