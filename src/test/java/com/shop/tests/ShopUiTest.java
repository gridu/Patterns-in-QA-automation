package com.shop.tests;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end UI tests covering catalog, product details, cart, authentication, and checkout flows.
 */
public class ShopUiTest {

    private WebDriver driver;

    private static final String SUT_URL = "http://localhost:8000";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/shop_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "postgres_password";

    @BeforeEach
    public void setUp() throws Exception {
        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement st = conn.createStatement();
        st.executeUpdate("DELETE FROM cart_items;");
        st.executeUpdate("DELETE FROM products;");
        st.executeUpdate("DELETE FROM users;");
        st.close();
        conn.close();

        // Initialize driver with helper
        driver = TestHelper.makeDriver();
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    // =========================================================================
    // CATALOG UI TESTS
    // =========================================================================

    @Test
    public void testCatalogDisplaysInStockProductsAndHidesZeroStock() throws Exception {
        // Prepare products: 2 in-stock, 1 zero-stock
        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Gaming Mouse', 'RGB Sensor', 49.99, 10);");
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Mechanical Keyboard', 'Blue switches', 89.99, 5);");
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Sold Out Monitor', '144Hz IPS', 299.99, 0);");
        stmt.close();
        conn.close();

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        List<WebElement> cards = driver.findElements(By.cssSelector("div.product-grid > div.product-card"));
        assertEquals(2, cards.size(), "Catalog should only display products with stock > 0");

        // Verify in-stock items are visible
        String pageSource = driver.getPageSource();
        try {
            assertTrue(pageSource.contains("Gaming Mouse"));
            assertTrue(pageSource.contains("Mechanical Keyboard"));
            assertFalse(pageSource.contains("Sold Out Monitor"), "Zero-quantity product must strictly NOT be displayed");
        } catch (AssertionError e) {
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Path destination = Paths.get("./screenshots/testCatalogDisplaysInStockProductsAndHidesZeroStock.png");
            Files.createDirectories(destination.getParent());
            Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        }
    }

    @Test
    public void testCatalogSortingByNameAndPrice() throws Exception {
        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Alpha Item', 'First', 100.00, 5);");
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Beta Item', 'Second', 10.00, 5);");
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Gamma Item', 'Third', 50.00, 5);");
        stmt.close();
        conn.close();

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        WebElement sortDropdown = driver.findElement(By.id("catalog-sort-select"));
        Select select = new Select(sortDropdown);

        // 1. Sort by Price: Low to High
        select.selectByValue("price-asc");
        TestHelper.pause(800);
        List<WebElement> titlesPriceAsc = driver.findElements(By.className("product-card-title"));
        assertEquals("Beta Item", titlesPriceAsc.get(0).getText().trim());
        assertEquals("Gamma Item", titlesPriceAsc.get(1).getText().trim());
        assertEquals("Alpha Item", titlesPriceAsc.get(2).getText().trim());

        // 2. Sort by Price: High to Low
        select.selectByValue("price-desc");
        TestHelper.pause(800);
        List<WebElement> titlesPriceDesc = driver.findElements(By.className("product-card-title"));
        assertEquals("Alpha Item", titlesPriceDesc.get(0).getText().trim());
        assertEquals("Beta Item", titlesPriceDesc.get(2).getText().trim());

        // 3. Sort by Name: Z to A
        select.selectByValue("name-desc");
        TestHelper.pause(800);
        List<WebElement> titlesNameDesc = driver.findElements(By.className("product-card-title"));
        assertEquals("Gamma Item", titlesNameDesc.get(0).getText().trim());
        assertEquals("Alpha Item", titlesNameDesc.get(2).getText().trim());
    }

    @Test
    public void testProductDetailViewAndNavigation() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Noise Cancelling Headphones", "Incredible bass and active NC", 249.99, 7, null, null, true
        );

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        WebElement titleEl = driver.findElement(By.cssSelector("[data-testid='product-name-" + prodId + "']"));
        titleEl.click();
        TestHelper.pause(800);

        // Verify product detail view is visible and catalog is hidden
        WebElement detailView = driver.findElement(By.id("product-detail-view"));
        assertFalse(detailView.getAttribute("class").contains("hidden"));
        WebElement catalogView = driver.findElement(By.id("catalog-view"));
        assertTrue(catalogView.getAttribute("class").contains("hidden"));

        // Verify details
        WebElement detailName = driver.findElement(By.id("product-detail-name"));
        assertEquals("Noise Cancelling Headphones", detailName.getText());

        WebElement detailDesc = driver.findElement(By.id("product-detail-description"));
        assertTrue(detailDesc.getText().contains("Incredible bass"));

        WebElement detailPrice = driver.findElement(By.id("product-detail-price"));
        assertEquals("$249.99", detailPrice.getText());

        WebElement detailStock = driver.findElement(By.id("product-detail-quantity"));
        assertTrue(detailStock.getText().contains("7"));

        // Click Back to Catalog
        WebElement backBtn = driver.findElement(By.id("back-to-catalog-btn"));
        backBtn.click();
        TestHelper.pause(500);

        // Verify views toggled back
        assertTrue(detailView.getAttribute("class").contains("hidden"));
        assertFalse(catalogView.getAttribute("class").contains("hidden"));
    }

    @Test
    public void testProductDetailQuantityValidation() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Limited Watch", "Only 3 left in stock", 150.00, 3, null, null, true
        );

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        driver.findElement(By.cssSelector("[data-testid='product-name-" + prodId + "']")).click();
        TestHelper.pause(500);

        WebElement qtyInput = driver.findElement(By.id("product-detail-quantity-input"));
        WebElement errorMsg = driver.findElement(By.id("quantity-error-msg"));
        WebElement addBtn = driver.findElement(By.id("product-detail-add-btn"));

        // Initially valid (default 1)
        assertTrue(errorMsg.getAttribute("class").contains("hidden"));
        assertTrue(addBtn.isEnabled());

        // 1. Enter quantity greater than stock (4 > 3)
        qtyInput.clear();
        qtyInput.sendKeys("4");
        TestHelper.pause(400);
        assertFalse(errorMsg.getAttribute("class").contains("hidden"));
        assertTrue(errorMsg.getText().contains("Quantity cannot exceed available stock of 3"));
        assertFalse(addBtn.isEnabled(), "Add to Cart button must be disabled when quantity exceeds stock");

        // 2. Enter invalid quantity (0)
        qtyInput.clear();
        qtyInput.sendKeys("0");
        TestHelper.pause(400);
        assertFalse(errorMsg.getAttribute("class").contains("hidden"));
        assertTrue(errorMsg.getText().contains("valid quantity of at least 1"));
        assertFalse(addBtn.isEnabled());

        // 3. Enter valid quantity (2)
        qtyInput.clear();
        qtyInput.sendKeys("2");
        TestHelper.pause(400);
        assertTrue(errorMsg.getAttribute("class").contains("hidden"));
        assertTrue(addBtn.isEnabled());
    }

    // =========================================================================
    // CART OPERATIONS UI TESTS
    // =========================================================================

    @Test
    public void testAddToCartFromCatalogUpdatesBadge() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Coffee Mug", "Ceramic mug", 12.50, 10, null, null, true
        );

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        WebElement badge = driver.findElement(By.id("cart-count-badge"));
        assertEquals("0", badge.getText().trim());

        driver.findElement(By.cssSelector("[data-testid='add-to-cart-btn-" + prodId + "']")).click();
        TestHelper.pause(1000);

        // Verify badge updated to 1
        assertEquals("1", badge.getText().trim());

        // Verify notification banner
        WebElement banner = driver.findElement(By.id("notification-banner"));
        assertFalse(banner.getAttribute("class").contains("hidden"));
        assertTrue(banner.getText().contains("Product added to cart!"));
    }

    @Test
    public void testAddToCartFromProductDetailView() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Desk Lamp", "LED warm light", 35.00, 10, null, null, true
        );

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        // Open details
        driver.findElement(By.cssSelector("[data-testid='product-name-" + prodId + "']")).click();
        TestHelper.pause(500);

        // Set quantity to 3
        WebElement qtyInput = driver.findElement(By.id("product-detail-quantity-input"));
        qtyInput.clear();
        qtyInput.sendKeys("3");

        // Click Add to Cart from Detail
        driver.findElement(By.id("product-detail-add-btn")).click();
        TestHelper.pause(1000);

        // Verify view switched back to catalog and badge is 3
        WebElement badge = driver.findElement(By.id("cart-count-badge"));
        assertEquals("3", badge.getText().trim());
    }

    @Test
    public void testCartViewCalculationsAndItemDeletion() throws Exception {
        int prod1 = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Wireless Keyboard", "Bluetooth", 80.00, 5, null, null, true
        );
        int prod2 = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Mouse Pad", "Extended", 20.00, 10, null, null, true
        );

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        // Add prod1 (1 unit = $80.00)
        driver.findElement(By.cssSelector("[data-testid='add-to-cart-btn-" + prod1 + "']")).click();
        TestHelper.pause(800);

        // Add prod2 via detail (2 units = $40.00)
        driver.findElement(By.cssSelector("[data-testid='product-name-" + prod2 + "']")).click();
        TestHelper.pause(500);
        WebElement qty = driver.findElement(By.id("product-detail-quantity-input"));
        qty.clear();
        qty.sendKeys("2");
        driver.findElement(By.id("product-detail-add-btn")).click();
        TestHelper.pause(800);

        // Open Cart view
        driver.findElement(By.id("nav-cart-btn")).click();
        TestHelper.pause(800);

        // Verify total price is $120.00
        WebElement totalEl = driver.findElement(By.id("cart-total-price"));
        assertEquals("$120.00", totalEl.getText().trim());

        // Verify subtotals
        WebElement sub1 = driver.findElement(By.cssSelector("[data-testid='cart-item-subtotal-" + prod1 + "']"));
        assertEquals("$80.00", sub1.getText().trim());
        WebElement sub2 = driver.findElement(By.cssSelector("[data-testid='cart-item-subtotal-" + prod2 + "']"));
        assertEquals("$40.00", sub2.getText().trim());

        // Delete prod1
        driver.findElement(By.cssSelector("[data-testid='delete-cart-item-btn-" + prod1 + "']")).click();
        TestHelper.pause(800);

        // Verify prod1 row is gone, prod2 remains, total is now $40.00
        List<WebElement> remainingRows = driver.findElements(By.cssSelector("[data-testid='cart-item-" + prod1 + "']"));
        assertEquals(0, remainingRows.size());
        assertEquals("$40.00", driver.findElement(By.id("cart-total-price")).getText().trim());
    }

    @Test
    public void testCartCheckoutAccessControlForGuestAndAuthUser() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Book", "Novel", 15.00, 10, null, null, true
        );

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        // Add item
        driver.findElement(By.cssSelector("[data-testid='add-to-cart-btn-" + prodId + "']")).click();
        TestHelper.pause(800);

        // Open Cart as Guest
        driver.findElement(By.id("nav-cart-btn")).click();
        TestHelper.pause(500);

        // Purchase button must be disabled for guest
        WebElement purchaseBtn = driver.findElement(By.id("purchase-btn"));
        assertFalse(purchaseBtn.isEnabled(), "Purchase button must be disabled for unauthenticated user");

        WebElement authWarning = driver.findElement(By.id("cart-auth-warning"));
        assertFalse(authWarning.getAttribute("class").contains("hidden"));
        assertTrue(authWarning.getText().contains("Please Login or Register"));
    }

    // =========================================================================
    // AUTHENTICATION UI TESTS
    // =========================================================================

    @Test
    public void testAuthRegistrationAndLoginViaModal() throws Exception {
        driver.get(SUT_URL);
        TestHelper.pause(1000);

        // 1. Open Register Modal
        driver.findElement(By.id("nav-register-btn")).click();
        TestHelper.pause(500);

        WebElement authModal = driver.findElement(By.id("auth-modal"));
        assertFalse(authModal.getAttribute("class").contains("hidden"));

        // Register new user
        driver.findElement(By.id("auth-username-input")).sendKeys("ui_user_1");
        driver.findElement(By.id("auth-password-input")).sendKeys("uiPass123");
        driver.findElement(By.id("auth-submit-btn")).click();
        TestHelper.pause(1000);

        // Verify logged in
        WebElement userActions = driver.findElement(By.id("user-actions"));
        assertFalse(userActions.getAttribute("class").contains("hidden"));
        assertEquals("ui_user_1", driver.findElement(By.id("current-username")).getText());

        // 2. Logout
        driver.findElement(By.id("nav-logout-btn")).click();
        TestHelper.pause(1000);

        // Verify logged out
        WebElement guestActions = driver.findElement(By.id("guest-actions"));
        assertFalse(guestActions.getAttribute("class").contains("hidden"));
        assertTrue(userActions.getAttribute("class").contains("hidden"));

        // 3. Login with wrong password
        driver.findElement(By.id("nav-login-btn")).click();
        TestHelper.pause(500);
        driver.findElement(By.id("auth-username-input")).sendKeys("ui_user_1");
        driver.findElement(By.id("auth-password-input")).sendKeys("WRONG_PASS");
        driver.findElement(By.id("auth-submit-btn")).click();
        TestHelper.pause(1000);

        WebElement errorBox = driver.findElement(By.id("auth-error-msg"));
        assertFalse(errorBox.getAttribute("class").contains("hidden"));
        assertTrue(errorBox.getText().contains("Invalid username or password"));

        // 4. Login with correct password
        driver.findElement(By.id("auth-password-input")).clear();
        driver.findElement(By.id("auth-password-input")).sendKeys("uiPass123");
        driver.findElement(By.id("auth-submit-btn")).click();
        TestHelper.pause(1000);

        assertTrue(authModal.getAttribute("class").contains("hidden"));
        assertFalse(userActions.getAttribute("class").contains("hidden"));
        assertEquals("ui_user_1", driver.findElement(By.id("current-username")).getText());
    }

    @Test
    public void testGuestCartContinuityOnLogin() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Continuity USB Cable", "Fast charging", 10.00, 10, null, null, true
        );
        TestHelper.doDatabaseAction(
                "INSERT_USER", null, null, 0, 0, "existing_shopper", "shopperPass", true
        );

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        // 1. Guest adds product to cart
        driver.findElement(By.cssSelector("[data-testid='add-to-cart-btn-" + prodId + "']")).click();
        TestHelper.pause(800);
        assertEquals("1", driver.findElement(By.id("cart-count-badge")).getText().trim());

        // 2. Guest logs in
        driver.findElement(By.id("nav-login-btn")).click();
        TestHelper.pause(500);
        driver.findElement(By.id("auth-username-input")).sendKeys("existing_shopper");
        driver.findElement(By.id("auth-password-input")).sendKeys("shopperPass");
        driver.findElement(By.id("auth-submit-btn")).click();
        TestHelper.pause(1200);

        // 3. Open cart and verify item is preserved
        driver.findElement(By.id("nav-cart-btn")).click();
        TestHelper.pause(800);

        WebElement subtotal = driver.findElement(By.cssSelector("[data-testid='cart-item-subtotal-" + prodId + "']"));
        assertEquals("$10.00", subtotal.getText().trim());

        // Verify purchase button is now enabled because user is authenticated
        WebElement purchaseBtn = driver.findElement(By.id("purchase-btn"));
        assertTrue(purchaseBtn.isEnabled(), "Purchase button should be enabled after user logs in with items in cart");
    }

    @Test
    public void testAuthenticatedUserCheckoutFlow() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Smartphone", "Latest model", 800.00, 4, null, null, true
        );

        driver.get(SUT_URL);
        TestHelper.pause(1000);

        // Register user directly from UI
        driver.findElement(By.id("nav-register-btn")).click();
        TestHelper.pause(500);
        driver.findElement(By.id("auth-username-input")).sendKeys("buyer_ui");
        driver.findElement(By.id("auth-password-input")).sendKeys("buyerPass123");
        driver.findElement(By.id("auth-submit-btn")).click();
        TestHelper.pause(4500); // Wait for the welcome notification timer (4s) to clear

        // Add 2 units of smartphone via product detail
        driver.findElement(By.cssSelector("[data-testid='product-name-" + prodId + "']")).click();
        TestHelper.pause(500);
        WebElement qty = driver.findElement(By.id("product-detail-quantity-input"));
        qty.clear();
        qty.sendKeys("2");
        driver.findElement(By.id("product-detail-add-btn")).click();
        TestHelper.pause(800);

        // Open cart
        driver.findElement(By.id("nav-cart-btn")).click();
        TestHelper.pause(800);

        WebElement purchaseBtn = driver.findElement(By.id("purchase-btn"));
        assertTrue(purchaseBtn.isEnabled());

        // Click Purchase
        purchaseBtn.click();
        TestHelper.pause(1000);

        // Verify notification banner
        WebElement banner = driver.findElement(By.id("notification-banner"));
        assertFalse(banner.getAttribute("class").contains("hidden"));
        assertTrue(banner.getText().contains("Order placed successfully!"));
        assertTrue(banner.getText().contains("Total: $1600.00"));

        // Verify cart is empty
        driver.findElement(By.id("nav-cart-btn")).click();
        TestHelper.pause(500);
        WebElement emptyMsg = driver.findElement(By.id("empty-cart-message"));
        assertFalse(emptyMsg.getAttribute("class").contains("hidden"));
        assertTrue(emptyMsg.getText().contains("Your cart is empty"));

        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT quantity FROM products WHERE id = " + prodId);
        assertTrue(rs.next());
        int remainingStock = rs.getInt("quantity");
        assertEquals(2, remainingStock, "Stock should have decremented from 4 to 2");
        rs.close();
        st.close();
        conn.close();
    }
}
