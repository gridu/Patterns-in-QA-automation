package com.shop.tests;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Shared test utility helper methods.
 */
public class TestHelper {

    public static int GLOBAL_LAST_PRODUCT_ID = -1;

    public static final String DB_URL = "jdbc:postgresql://localhost:5432/shop_db";
    public static final String DB_USER = "postgres";
    public static final String DB_PASS = "postgres_password";

    public static int doDatabaseAction(
            String actionType,
            String nameOrQuery,
            String desc,
            double price,
            int quantity,
            String username,
            String password,
            boolean autoCommit) throws Exception {

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(autoCommit);
            try (Statement stmt = conn.createStatement()) {
                switch (actionType.toUpperCase()) {
                    case "CLEAN_ALL":
                        stmt.executeUpdate("DELETE FROM cart_items;");
                        stmt.executeUpdate("DELETE FROM products;");
                        stmt.executeUpdate("DELETE FROM users;");
                        return 0;
                    case "INSERT_PRODUCT":
                        String sql = "INSERT INTO products (name, description, price, quantity) VALUES ('"
                                + nameOrQuery.replace("'", "''") + "', '"
                                + desc.replace("'", "''") + "', "
                                + price + ", "
                                + quantity + ") RETURNING id;";
                        try (ResultSet rs = stmt.executeQuery(sql)) {
                            if (rs.next()) {
                                int id = rs.getInt("id");
                                GLOBAL_LAST_PRODUCT_ID = id;
                                return id;
                            }
                        }
                        return -1;
                    case "INSERT_USER":
                        String userSql = "INSERT INTO users (username, password) VALUES ('"
                                + username.replace("'", "''") + "', '"
                                + password.replace("'", "''") + "') RETURNING id;";
                        try (ResultSet rs = stmt.executeQuery(userSql)) {
                            if (rs.next()) {
                                return rs.getInt("id");
                            }
                        }
                        return -1;
                    case "RAW_COUNT":
                        try (ResultSet rs = stmt.executeQuery(nameOrQuery)) {
                            if (rs.next()) {
                                return rs.getInt(1);
                            }
                        }
                        return 0;
                    case "GET_PRODUCT_STOCK":
                        try (ResultSet rs = stmt.executeQuery("SELECT quantity FROM products WHERE id = " + (int) price)) {
                            if (rs.next()) {
                                return rs.getInt("quantity");
                            }
                        }
                        return -1;
                    default:
                        throw new IllegalArgumentException("Unknown DB action: " + actionType);
                }
            }
        }
    }

    public static WebDriver makeDriver() {
        ChromeOptions options = new ChromeOptions();
        String headless = System.getProperty("headless", "true");
        if ("true".equalsIgnoreCase(headless)) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(3));
        return driver;
    }

    public static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static String extractCookieValue(HttpResponse<?> response, String cookieName) {
        var headers = response.headers().allValues("set-cookie");
        for (String header : headers) {
            if (header.contains(cookieName + "=")) {
                String[] parts = header.split(";");
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith(cookieName + "=")) {
                        return trimmed.substring((cookieName + "=").length());
                    }
                }
            }
        }
        return null;
    }
}
