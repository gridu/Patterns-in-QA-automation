package com.shop.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end shopping journeys combining API, UI, and database verification.
 */
public class ShopEndToEndTest {

    private WebDriver driver;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String APP_URL = "http://localhost:8000";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/shop_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "postgres_password";

    @BeforeEach
    public void setup() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM cart_items;");
            st.executeUpdate("DELETE FROM products;");
            st.executeUpdate("DELETE FROM users;");
        }
        driver = TestHelper.makeDriver();
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    public void testComplexOmnichannelShoppingJourneyFromGuestToAuthPurchase() throws Exception {
        // STEP 1: Direct SQL Data Preparation
        int monitorId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "UltraWide 34 Monitor", "Curved 144Hz", 500.00, 5, null, null, true
        );
        int mouseId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Ergonomic Vertical Mouse", "Wireless mouse", 60.00, 10, null, null, true
        );

        // STEP 2: UI Browsing as Guest
        driver.get(APP_URL);
        TestHelper.pause(1000);

        // Check catalog displays 2 products
        assertEquals(2, driver.findElements(By.className("product-card")).size());

        // Add 1 mouse directly from catalog card
        driver.findElement(By.cssSelector("[data-testid='add-to-cart-btn-" + mouseId + "']")).click();
        TestHelper.pause(800);
        assertEquals("1", driver.findElement(By.id("cart-count-badge")).getText().trim());

        // Open details for monitor
        driver.findElement(By.cssSelector("[data-testid='product-name-" + monitorId + "']")).click();
        TestHelper.pause(500);

        // Select 2 monitors
        WebElement qtyInput = driver.findElement(By.id("product-detail-quantity-input"));
        qtyInput.clear();
        qtyInput.sendKeys("2");
        driver.findElement(By.id("product-detail-add-btn")).click();
        TestHelper.pause(800);
        assertEquals("3", driver.findElement(By.id("cart-count-badge")).getText().trim());

        // STEP 3: API Cross-Verification (Read session cookie from browser and query API)
        var cookie = driver.manage().getCookieNamed("session_id");
        assertNotNull(cookie, "Session cookie should have been created by guest shopping");
        String guestSessionId = cookie.getValue();

        HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest apiCartReq = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/cart"))
                .header("X-Session-ID", guestSessionId)
                .GET().build();
        HttpResponse<String> apiCartResp = httpClient.send(apiCartReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, apiCartResp.statusCode());
        JsonNode cartJson = mapper.readTree(apiCartResp.body());
        assertEquals(2, cartJson.get("items").size());
        assertEquals(1060.00, cartJson.get("total_price").asDouble(), 0.001);

        // STEP 4: UI Registration & Cart Continuity
        driver.findElement(By.id("nav-register-btn")).click();
        TestHelper.pause(500);
        driver.findElement(By.id("auth-username-input")).sendKeys("omnichannel_user");
        driver.findElement(By.id("auth-password-input")).sendKeys("strongPass123");
        driver.findElement(By.id("auth-submit-btn")).click();
        TestHelper.pause(4500); // Allow notification timer to expire

        // STEP 5: UI Cart Checkout
        driver.findElement(By.id("nav-cart-btn")).click();
        TestHelper.pause(800);

        WebElement totalEl = driver.findElement(By.id("cart-total-price"));
        assertEquals("$1060.00", totalEl.getText().trim());

        WebElement purchaseBtn = driver.findElement(By.id("purchase-btn"));
        assertTrue(purchaseBtn.isEnabled());
        purchaseBtn.click();
        TestHelper.pause(1000);

        // Verify confirmation
        WebElement banner = driver.findElement(By.id("notification-banner"));
        assertFalse(banner.getAttribute("class").contains("hidden"));
        assertTrue(banner.getText().contains("Order placed successfully!"));
        assertTrue(banner.getText().contains("Total: $1060.00"));

        // STEP 6: Direct SQL Verification of Stock and Order Cleanup
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Check monitor stock: was 5, bought 2 -> remaining 3
            try (ResultSet rs = stmt.executeQuery("SELECT quantity FROM products WHERE id = " + monitorId)) {
                assertTrue(rs.next());
                assertEquals(3, rs.getInt("quantity"));
            }

            // Check mouse stock: was 10, bought 1 -> remaining 9
            try (ResultSet rs = stmt.executeQuery("SELECT quantity FROM products WHERE id = " + mouseId)) {
                assertTrue(rs.next());
                assertEquals(9, rs.getInt("quantity"));
            }

            // Check user cart in database is now empty
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM cart_items WHERE session_id LIKE 'user_%'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test
    public void testConcurrentApiAndUiCartManipulationConflictCheck() throws Exception {
        // STEP 1: Direct SQL insert
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Limited Edition Controller", "Special edition", 75.00, 3, null, null, true
        );

        // STEP 2: API user creation
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpResponse<String> regResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"concurrent_shopper\",\"password\":\"passCon1\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, regResp.statusCode());
        int userId = mapper.readTree(regResp.body()).get("id").asInt();

        // STEP 3: UI Login as that user
        driver.get(APP_URL);
        TestHelper.pause(1000);
        driver.findElement(By.id("nav-login-btn")).click();
        TestHelper.pause(500);
        driver.findElement(By.id("auth-username-input")).sendKeys("concurrent_shopper");
        driver.findElement(By.id("auth-password-input")).sendKeys("passCon1");
        driver.findElement(By.id("auth-submit-btn")).click();
        TestHelper.pause(4500); // Allow notification timer to clear

        // In UI, add 2 units to cart via detail
        driver.findElement(By.cssSelector("[data-testid='product-name-" + prodId + "']")).click();
        TestHelper.pause(500);
        WebElement qtyInput = driver.findElement(By.id("product-detail-quantity-input"));
        qtyInput.clear();
        qtyInput.sendKeys("2");
        driver.findElement(By.id("product-detail-add-btn")).click();
        TestHelper.pause(800);
        assertEquals("2", driver.findElement(By.id("cart-count-badge")).getText().trim());

        // STEP 4: Out-of-band API call tries to add 2 more for same user (should fail: 2 + 2 > 3)
        HttpResponse<String> conflictResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-User-ID", String.valueOf(userId))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"product_id\":" + prodId + ",\"quantity\":2}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(400, conflictResp.statusCode());
        assertTrue(conflictResp.body().contains("Available stock: 3"));

        // STEP 5: Out-of-band API call adds 1 unit (2 + 1 = 3, matches available stock)
        HttpResponse<String> successAddResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-User-ID", String.valueOf(userId))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"product_id\":" + prodId + ",\"quantity\":1}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, successAddResp.statusCode());

        // STEP 6: In UI, refresh page so client state syncs with server, open cart and verify 3 units are in cart (Total: $225.00)
        driver.navigate().refresh();
        TestHelper.pause(1000);

        driver.findElement(By.id("nav-cart-btn")).click();
        TestHelper.pause(800);

        WebElement totalEl = driver.findElement(By.id("cart-total-price"));
        assertEquals("$225.00", totalEl.getText().trim());

        // Complete purchase in UI
        driver.findElement(By.id("purchase-btn")).click();
        TestHelper.pause(1000);

        // Verify remaining stock is 0 in DB
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT quantity FROM products WHERE id = " + prodId)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt("quantity"), "All 3 units should have been bought");
        }
    }
}
