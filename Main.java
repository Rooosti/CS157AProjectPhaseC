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

		// extract headers
		String[] headers = new String[columnCount];
		for (int i = 1; i <= columnCount; i++) {
			headers[i - 1] = meta.getColumnLabel(i);
		}

		// compute column widths
		int[] colWidths = new int[columnCount];
		for (int i = 0; i < columnCount; i++) {
			colWidths[i] = headers[i].length();
		}

		rs.beforeFirst();
		while (rs.next()) {
			for (int i = 1; i <= columnCount; i++) {
				String value = rs.getString(i);
				if (value != null) {
					colWidths[i - 1] = Math.max(colWidths[i - 1], value.length());
				}
			}
		}

		// build format string
		StringBuilder formatBuilder = new StringBuilder("| ");
		for (int i = 0; i < columnCount; i++) {
			formatBuilder.append("%-").append(colWidths[i]).append("s");
			if (i < columnCount - 1)
				formatBuilder.append(" | ");
			else
				formatBuilder.append(" |");
		}
		String formatStr = formatBuilder.toString();

		// print header
		System.out.println(String.format(formatStr, (Object[]) headers));

		// print separator
		StringBuilder sep = new StringBuilder("|");
		for (int w : colWidths) {
			sep.append("-".repeat(w + 2)).append("|");
		}
		System.out.println(sep);

		// print rows
		rs.beforeFirst();
		while (rs.next()) {
			Object[] row = new Object[columnCount];
			for (int i = 1; i <= columnCount; i++) {
				row[i - 1] = rs.getString(i);
			}
			System.out.println(String.format(formatStr, row));
		}
	}

}
