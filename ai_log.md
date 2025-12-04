# AI Collaboration Log - CS157A PHASE C
**Project:** Warehouse Console JDBC App with SQL database  
**AI Tool:** Chatgpt(5.1)  
**Info:** This document includes all prompts sent to AI in order to assist with the completion of PHASE C and the code or help the AI sent back. Additonally, the document also includes what we used from the AI's response and generation.

## Prompt #1:
hey, we have a cs157a final project in java + mysql
we need to make a console app with:app.properties for db url/user/password, menu with Scanner (view/insert/update/delete/transaction/exit) ,ONLY PreparedStatement, at least one transaction with commit/rollback. can you show me how to structure Main.java and what’s the best practice way to organize this

## AI Response #1:

Skeleton of code the AI gave as an example:
```
public static void main(String[] args) {
    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
        props.load(fis);
    } catch (IOException e) {
        System.err.println("Could not read " + PROPERTIES_FILE);
        return;
    }

    String url  = props.getProperty("db.url");
    String user = props.getProperty("db.user");
    String pass = props.getProperty("db.password");

    try (Connection conn = DriverManager.getConnection(url, user, pass);
         Scanner scanner = new Scanner(System.in)) {

        System.out.println("Connected to database.");
        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1": listCustomers(conn); break;
                case "2": addCustomer(conn, scanner); break;
                // other options here
                case "0": running = false; break;
                default:  System.out.println("Invalid choice.");
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}
```
What we used from AI #1:
Used the overall structure (loading app.properties, one Connection + Scanner, while loop + switch menu), then filled in our own menu options and SQL for our warehouse schema.

## Prompt #2: 
i keep repeating the same scanner code and jdbc code everywhere  
can you help me make helper methods for:  
- read int with validation  
- read non-empty string  
- read date in yyyy-mm-dd  
and also small jdbc helpers like:  
- check if a row exists  
- query a single int / bigdecimal
all using PreparedStatement?

## AI Response #2:

AI suggested adding input helper methods to avoid repeating the same Scanner logic:

```
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
        System.out.println("Input cannot be empty.");
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
It also suggested small JDBC helper methods using PreparedStatement:
```
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
What we used from AI #2:
We added readInt, readNonEmpty, and readDate to our Main class so all user input is validated in one place.
We also added exists, queryForInt, and queryForBigDecimal and used them mainly inside createPurchaseTransaction to check if customers/items exist, to sum stock from Manages, and to look up UnitPrice from WarehouseItem.

## Prompt #3:
i have select queries for customers, purchases, and warehouse stock  
right now i just print them with println and it looks messy  

can you help me write a single method `printResultSet(ResultSet rs)` that prints a table with column headers and auto-sized columns, and works for any select query?

## AI Response #3:

AI suggested creating a generic printResultSet method that uses ResultSetMetaData and computes column widths:

```
private static void printResultSet(ResultSet rs) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int columnCount = meta.getColumnCount();

    List<String[]> rows = new ArrayList<>();
    int[] colWidths = new int[columnCount];

    // initialize with header labels
    for (int i = 1; i <= columnCount; i++) {
        String label = meta.getColumnLabel(i);
        colWidths[i - 1] = label.length();
    }

    // read all rows and update widths
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
```
What we used from AI #3:

We added this printResultSet method to our Main class (with only tiny formatting tweaks).
We now use it in listCustomers, listPurchases, and viewWarehouseStock so all of our SELECT queries print as a clean table in the console instead of a bunch of separate printlns.

## Prompt #4:
what does mean in database managment "Create at least one workflow that uses both COMMIT and ROLLBACK to ensure atomicity"  
like what exactly do they want us to do in java/jdbc?

## AI Response #4:

AI explained that this requirement is about implementing a real database transaction in JDBC, where multiple SQL statements either all succeed together or all get undone.

It showed the typical JDBC pattern:

```
conn.setAutoCommit(false);  // start transaction

try {
    // run several SQL operations (INSERT/UPDATE/DELETE) here

    conn.commit();          // if everything works
} catch (SQLException e) {
    conn.rollback();        // if anything fails, undo all changes
} finally {
    conn.setAutoCommit(true);  // restore default behavior
}
```
What we used from AI #4:

We used this idea directly to design our createPurchaseTransaction method.
In our code we:
-Call conn.setAutoCommit(false); at the start of the purchase workflow.
-Use our helper methods (exists, queryForInt, queryForBigDecimal) to check the customer, item, and available stock, and to retrieve UnitPrice.
-Insert a new row into Purchases and read back the generated TransactionID.
-Insert a corresponding row into TransactionLineItem.
-Call conn.commit(); if all steps work, or conn.rollback(); and show an error message if any step fails or validation fails (for example, insufficient stock or missing customer).
-Restore autoCommit to true at the end.
This is the transactional workflow we’ll demonstrate in the video to show both COMMIT and ROLLBACK behavior.


