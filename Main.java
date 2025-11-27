import java.sql.*;
import java.util.Scanner;

class Main {
	public static void main(String[] args) throws Exception {
		// setup: define url, username, password, and database name
		String databaseName = "databaseName";
		String username = "username";
		String password = "password";
		String host = "localhost";
		String port = "3306";
		String databaseURL = "jdbc:mysql://" + host + ":" + port + "/" + databaseName; // "jdbc:mysql://<host>:<port>/<databaseName>"

		try (
				Connection myCon = DriverManager.getConnection(databaseURL, username, password);
				Scanner scanner = new Scanner(System.in)) {
			System.out.println("Connected to database" + databaseName);
			System.out.println("Type 'quit' or 'exit' to close.");

			while (true) {
				System.out.print("sql> ");
				if (!scanner.hasNextLine()) {
					break;
				}
				String sql = scanner.nextLine().trim();

				if (sql.equalsIgnoreCase("quit") || sql.equalsIgnoreCase("exit")) {
					System.out.println("Goodbye!");
					break;
				}
				if (sql.isEmpty()) {
					continue;
				}

				String firstWord = sql.split("\\s+")[0].toLowerCase();
				try (Statement stmt = myCon.createStatement()) {
					if (firstWord.equals("select") || firstWord.equals("show")
							|| firstWord.equals("describe")) {
						// Query that returns rows
						try (ResultSet rs = stmt.executeQuery(sql)) {
							printResultSet(rs);
						}
					} else {
						// UPDATE / INSERT / DELETE / DDL, etc.
						int affected = stmt.executeUpdate(sql);
						System.out.println("OK, " + affected + " row(s) affected.");
					}
				}
			}
		} catch (SQLException e) {
			System.err.println("Could not connect to the database:");
			e.printStackTrace();
		}
	}

	private static void printResultSet(ResultSet rs) throws SQLException {
		ResultSetMetaData meta = rs.getMetaData();
		int columnCount = meta.getColumnCount();

		// Print header
		for (int i = 1; i <= columnCount; i++) {
			System.out.print(meta.getColumnLabel(i));
			if (i < columnCount)
				System.out.print(" | ");
		}
		System.out.println();

		// Print separator
		for (int i = 1; i <= columnCount; i++) {
			for (int j = 0; j < meta.getColumnLabel(i).length(); j++) {
				System.out.print("-");
			}
			if (i < columnCount)
				System.out.print("-+-");
		}
		System.out.println();

		int rowCount = 0;
		while (rs.next()) {
			rowCount++;
			for (int i = 1; i <= columnCount; i++) {
				Object val = rs.getObject(i);
				System.out.print(val == null ? "NULL" : val.toString());
				if (i < columnCount)
					System.out.print(" | ");
			}
			System.out.println();
		}

		if (rowCount == 0) {
			System.out.println("(no rows)");
		} else {
			System.out.println(rowCount + " row(s).");
		}
	}
}
