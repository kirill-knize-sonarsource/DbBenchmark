package org.sonarsource.bench.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class JdbcUtils {
    static Connection open(String url) throws SQLException {
        return DriverManager.getConnection(url);
    }
}
