# CS157A Personal Database Application – Phase C
## 1. Project Overview
This project implements a small warehouse / membership purchasing system using:

- MySQL for relational data storage
- Java and JDBC for a console-based client application
- PreparedStatements for DB operations
- A simple transactional workflow for purchase insertion

Features:
- View, add, update, and delete customers
- View purchases joined with customers
- Create a new purchase in a single transaction (insert into `Purchases` and `TransactionLineItem`, with a trigger updating stock)
- View current stock levels per warehouse and item
---

## 2. Environment & Dependencies
- Operating System: Windows 11
- Database: MySQL 9.4.0
- Client Tool: MySQL Workbench
- Java: JDK 25.0.1
- JDBC Driver: mysql-connector-j-9.5.0
---

## 4 Database Setup
1. Open MySQL Workbench and connect to your local MySQL server
2. Open the script:
   - `File` -> `Open SQL Script`
   - Choose `create_and_populate.sql`
3. Execute the entire script (lightning bolt icon)
4. The script will drop and recreate the database, all tables (with sample data), triggers, views, and procedures

## 5. Configuring App.properties
- `app.properties` holds the database connection config
  - Insert your database username and password

## 6. Building and Running the Application (Intellij)
- Be sure to add the mySQL connector as a library:
  - Navigate to `File` -> `Project Structure` `Libraries`
  - Add the `mysql-connector` file as a library
  - Right click `Main.java` and select `Run`
    - If not using IntelliJ, you will have to manually compile through the command line
---

## 8. How the Application Was Built
### 1. Designed ERD for:
   - `Warehouse`, `Employee`, `Customer`, `Purchases`, `WarehouseItem`, `TransactionLineItem`, `Manages`
   - Normalized to avoid redundancy and for referential integrity
### 2. SQL Implementation
   - Wrote create_and_populate.sql to:
     1) Create database and tables based on Phase B
     2) Insert data from Phase B
     3) Create `CustomerPurchaseSummary` view, `AddNewCustomer` procedure, and `update_stock_after_purchase` trigger
### 3. App Config
   - Separated DB credentials from Java code using a properties file
   - Configured `db.url`, `db.user`, and `db.password`
### 4. Java Console Shell
   - Created Main.java with:
     1. `main()` method establishing a Connection using JDBC
     2. A loop that displays menu items and input with Java `Scanner`
     3. Helper methods for database operations

### Code Walkthrough
#### Main Method Setup:
```java
      public static void main(String[] args) {
      Properties props = new Properties();
      try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
      props.load(fis);
      } catch (IOException e) {
      System.err.println("Could not read " + PROPERTIES_FILE + ": " + e.getMessage());
      return;
      }

      String url = props.getProperty("db.url");
      String user = props.getProperty("db.user");
      String pass = props.getProperty("db.password");
```
- Loads database credentials from app.properties \

#### Database Connection & Menu Loop
```java
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
                // ... other cases
            }
        }
    }
}
```
- Establishes a database connection and enters an interactive loop where users can db operations from a menu

### 5. JDBC Operations
- Used prepared statements for SELECT, INSERT, UPDATE, and DELETE operations

#### List Customers (SELECT):

```java
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
```
- Uses a PreparedStatement to query all customers and display them in formatted table
  <br>
#### Add Customer (INSERT)
```java
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
        } 
        else {
            System.out.println("Error inserting customer: " + e.getMessage());
        }
    }
}
```
- Collects user input, validates it using helper methods, and inserts a new customer record
- Catches constraint violations (duplicate IDs, etc)  

#### Update Customer Phone (UPDATE)
```java
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
```
- Updates a customer's phone number and says if customer was found

#### Delete Customer (DELETE)
```
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
```
- Deletes a customer and handles FK constraint violations as well as other errors.

#### List Purchases (SELECT + JOIN)
```java
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
```
- Uses a JOIN operation to display purchases and customer names
### 6. Transactional Workflow
- Performed multistep operation (checks, insert into Purchases, insert into TransactionLineItem)
- If any step failed, calls rollback() and shows an error
- On success, calls commit() and prints the new transaction ID
```java
private static void createPurchaseTransaction(Connection conn, Scanner scanner) {
    int membershipId = readInt(scanner, "Enter customer membership ID: ");
    int itemId = readInt(scanner, "Enter item ID to purchase: ");
    int quantity = readInt(scanner, "Enter quantity (>0): ");

    if (quantity <= 0) {
        System.out.println("Quantity must be greater than 0.");
        return;
    }

    try {
        conn.setAutoCommit(false); // Begin transaction

        // Step 1: Verify customer exists
        if (!exists(conn, "SELECT 1 FROM Customer WHERE MembershipID = ?", membershipId)) {
            System.out.println("No customer with that membership ID.");
            conn.rollback();
            conn.setAutoCommit(true);
            return;
        }

        // Step 2: Verify item exists
        if (!exists(conn, "SELECT 1 FROM WarehouseItem WHERE ItemID = ?", itemId)) {
            System.out.println("No item with that ID.");
            conn.rollback();
            conn.setAutoCommit(true);
            return;
        }

        // Step 3: Check sufficient stock
        int totalStock = queryForInt(conn,
                "SELECT COALESCE(SUM(Stock), 0) FROM Manages WHERE ItemID = ?",
                itemId);

        if (totalStock < quantity) {
            System.out.println("Insufficient stock. Available: " + totalStock);
            conn.rollback();
            conn.setAutoCommit(true);
            return;
        }

        // Step 4: Get unit price and calculate total
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

        // Step 5: Insert purchase record
        String insertPurchase =
                "INSERT INTO Purchases (MembershipID, Date, Total) VALUES (?, ?, ?)";
        int newTransactionId;

        try (PreparedStatement ps = conn.prepareStatement(
                insertPurchase, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, membershipId);
            ps.setDate(2, Date.valueOf(LocalDate.now()));
            ps.setBigDecimal(3, totalPrice);
            ps.executeUpdate();

            // Retrieve auto-generated TransactionID
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    newTransactionId = keys.getInt(1);
                } else {
                    throw new SQLException("No generated key returned for Purchases.");
                }
            }
        }

        // Step 6: Insert line item (triggers stock decrement)
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

        // Commit transaction
        conn.commit();
        conn.setAutoCommit(true);
        System.out.println("Purchase created and committed. TransactionID = " + newTransactionId);

    } catch (SQLException e) {
        // Rollback on any error
        try {
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException ex) {
            System.out.println("Error during rollback: " + ex.getMessage());
        }
        System.out.println("Error creating purchase. Transaction rolled back: " + e.getMessage());
    }
}
```

### Helper Methods
#### Input Validation
```java
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
```
- `readInt` keeps prompting the user until a valid int is intered
  - If the user enters non numeric input, it handles it and outputs an error message
- `readNonEmpty` ensures string fields (name, email, phone, etc) are not blank
- `readDate` validates date input in YYYY-MM-DD format using `LocalDate.parse()`

#### Database Query Helpers:
```java
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
                return rs.getBigDecimal(1);
            }
            return null;
        }
    }
}
```
- These methods check if
  - Whether a record exists in the database by executing a query and checking if any rows are returned
  - Executes a query that returns a single integer value (helpful for aggregate functions)
  - executes a query that returns a single BigDecimal value (good for monetary values and prices)
#### Result Set printer
```java
private static void printResultSet(ResultSet rs) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();

    List<String[]> rows = new ArrayList<>();
    int[] colWidths = new int[columnCount];

    // Calculate header widths
    for (int i = 1; i <= columnCount; i++) {
        String label = meta.getColumnLabel(i);
        colWidths[i - 1] = label.length();
    }

    // Read all rows and calculate column widths
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

    // Build format string
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

    // Print header row
    String[] headers = new String[columnCount];
    for (int i = 1; i <= columnCount; i++) {
        headers[i - 1] = meta.getColumnLabel(i);
    }
    System.out.println(String.format(formatStr, (Object[]) headers));

    // Print separator
    StringBuilder sep = new StringBuilder("|");
    for (int w : colWidths) {
        sep.append("-".repeat(w + 2)).append("|");
    }
    System.out.println(sep.toString());

    // Print data rows
    for (String[] row : rows) {
        System.out.println(String.format(formatStr, (Object[]) row));
    }
}
```
- Formats and displays query results in a table
- Dynamically calculates the width of each column based on the header labels and data for alignment

### 7. Testing and Debugging
- Tested each menu option with valid and invalid inputs
- Verified FK constraint behavior,trigger behavior, stored output in MySQL Workbench