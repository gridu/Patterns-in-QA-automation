package com.shop.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

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
 * API Test Suite covering catalog, cart, authentication, and checkout endpoints.
 */
public class ShopApiTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String APP_URL = "http://localhost:8000";
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/shop_db";
    private static final String DB_USER = "postgres";
    private static final String DB_PASS = "postgres_password";

    @BeforeEach
    public void setupBeforeEachTest() throws Exception {
        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement st = conn.createStatement();
        st.executeUpdate("DELETE FROM cart_items;");
        st.executeUpdate("DELETE FROM products;");
        st.executeUpdate("DELETE FROM users;");
        st.close();
        conn.close();
    }

    // =========================================================================
    // PRODUCTS API TESTS
    // =========================================================================

    @Test
    public void testGetProductsEmptyList() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/products"))
                .GET()
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        JsonNode json = mapper.readTree(resp.body());
        assertTrue(json.isArray());
        assertEquals(0, json.size());
    }

    @Test
    public void testGetProductsExcludesZeroQuantityRule() throws Exception {
        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('In Stock Item', 'Valid', 29.99, 10);");
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Out of Stock Item', 'Depleted', 9.99, 0);");
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Negative Item', 'Faulty', 5.00, -2);");
        stmt.close();
        conn.close();

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/products"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        JsonNode json = mapper.readTree(resp.body());
        assertEquals(1, json.size(), "Only products with quantity > 0 must be returned by catalog API");
        assertEquals("In Stock Item", json.get(0).get("name").asText());
        assertEquals(29.99, json.get(0).get("price").asDouble(), 0.001);
    }

    @Test
    public void testGetProductsSortingByNameAndPrice() throws Exception {
        // Insert products using direct JDBC
        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement stmt = conn.createStatement();
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Zebra Book', 'A', 50.00, 5);");
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Apple Phone', 'B', 999.00, 5);");
        stmt.executeUpdate("INSERT INTO products (name, description, price, quantity) VALUES ('Banana Fruit', 'C', 2.50, 5);");
        stmt.close();
        conn.close();

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // 1. Sort by name asc
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/products?sort_by=name&order=asc"))
                .GET().build();
        HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp1.statusCode());
        JsonNode json1 = mapper.readTree(resp1.body());
        assertEquals("Apple Phone", json1.get(0).get("name").asText());
        assertEquals("Banana Fruit", json1.get(1).get("name").asText());
        assertEquals("Zebra Book", json1.get(2).get("name").asText());

        // 2. Sort by name desc
        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/products?sort_by=name&order=desc"))
                .GET().build();
        HttpResponse<String> resp2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp2.statusCode());
        JsonNode json2 = mapper.readTree(resp2.body());
        assertEquals("Zebra Book", json2.get(0).get("name").asText());
        assertEquals("Apple Phone", json2.get(2).get("name").asText());

        // 3. Sort by price asc
        HttpRequest req3 = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/products?sort_by=price&order=asc"))
                .GET().build();
        HttpResponse<String> resp3 = client.send(req3, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp3.statusCode());
        JsonNode json3 = mapper.readTree(resp3.body());
        assertEquals(2.50, json3.get(0).get("price").asDouble(), 0.001);
        assertEquals(999.00, json3.get(2).get("price").asDouble(), 0.001);

        // 4. Sort by price desc
        HttpRequest req4 = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/products?sort_by=price&order=desc"))
                .GET().build();
        HttpResponse<String> resp4 = client.send(req4, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp4.statusCode());
        JsonNode json4 = mapper.readTree(resp4.body());
        assertEquals(999.00, json4.get(0).get("price").asDouble(), 0.001);
        assertEquals(2.50, json4.get(2).get("price").asDouble(), 0.001);
    }

    @Test
    public void testGetProductByIdSuccessAndNotFound() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Headphones", "Noise Cancelling", 199.95, 8, null, null, true
        );

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // Positive
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/products/" + prodId))
                .GET().build();
        HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp1.statusCode());
        JsonNode json1 = mapper.readTree(resp1.body());
        assertEquals("Headphones", json1.get("name").asText());
        assertEquals("Noise Cancelling", json1.get("description").asText());
        assertEquals(199.95, json1.get("price").asDouble(), 0.001);
        assertEquals(8, json1.get("quantity").asInt());

        // Negative (404)
        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/products/999999"))
                .GET().build();
        HttpResponse<String> resp2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp2.statusCode());
        assertTrue(resp2.body().contains("Product not found"));
    }

    // =========================================================================
    // AUTHENTICATION API TESTS
    // =========================================================================

    @Test
    public void testAuthRegisterSuccessAndDuplicateUsername() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // 1. Success registration
        String payload1 = "{\"username\":\"alice_test\",\"password\":\"password123\"}";
        HttpRequest req1 = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload1))
                .build();

        HttpResponse<String> resp1 = client.send(req1, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, resp1.statusCode());
        JsonNode json1 = mapper.readTree(resp1.body());
        assertEquals("alice_test", json1.get("username").asText());
        assertTrue(json1.get("id").asInt() > 0);

        // Verify cookie is set
        String cookieVal = TestHelper.extractCookieValue(resp1, "user_id");
        assertNotNull(cookieVal);
        assertEquals(String.valueOf(json1.get("id").asInt()), cookieVal);

        // 2. Duplicate registration fails with 400
        HttpRequest req2 = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload1))
                .build();

        HttpResponse<String> resp2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, resp2.statusCode());
        assertTrue(resp2.body().contains("Username already exists"));
    }

    @Test
    public void testAuthLoginSuccessAndInvalidCredentials() throws Exception {
        // Pre-create user in database
        TestHelper.doDatabaseAction(
                "INSERT_USER", null, null, 0, 0, "bob_login", "secretBob1", true
        );

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // 1. Valid login
        String validPayload = "{\"username\":\"bob_login\",\"password\":\"secretBob1\"}";
        HttpRequest reqValid = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(validPayload))
                .build();

        HttpResponse<String> respValid = client.send(reqValid, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, respValid.statusCode());
        JsonNode jsonValid = mapper.readTree(respValid.body());
        assertEquals("bob_login", jsonValid.get("username").asText());

        // 2. Invalid password
        String wrongPassPayload = "{\"username\":\"bob_login\",\"password\":\"WRONG\"}";
        HttpRequest reqWrong = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(wrongPassPayload))
                .build();
        HttpResponse<String> respWrong = client.send(reqWrong, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, respWrong.statusCode());
        assertTrue(respWrong.body().contains("Invalid username or password"));

        // 3. Non-existent user
        String nonUserPayload = "{\"username\":\"no_such_user\",\"password\":\"pass\"}";
        HttpRequest reqNon = HttpRequest.newBuilder()
                .uri(URI.create(APP_URL + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(nonUserPayload))
                .build();
        HttpResponse<String> respNon = client.send(reqNon, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, respNon.statusCode());
    }

    @Test
    public void testAuthLogoutAndMeEndpoint() throws Exception {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // Register user to obtain credentials and session
        String payload = "{\"username\":\"charlie\",\"password\":\"charliePass\"}";
        HttpResponse<String> regResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, regResp.statusCode());
        JsonNode regUser = mapper.readTree(regResp.body());
        int userId = regUser.get("id").asInt();

        // GET /api/auth/me with Cookie
        HttpResponse<String> meResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/auth/me"))
                        .header("Cookie", "user_id=" + userId)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, meResp.statusCode());
        JsonNode meUser = mapper.readTree(meResp.body());
        assertEquals("charlie", meUser.get("username").asText());

        // POST /api/auth/logout
        HttpResponse<String> logoutResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/auth/logout"))
                        .header("Cookie", "user_id=" + userId)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, logoutResp.statusCode());
        assertTrue(logoutResp.body().contains("Logged out successfully"));

        // GET /api/auth/me without cookie (guest)
        HttpResponse<String> guestMe = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/auth/me"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, guestMe.statusCode());
        assertEquals("null", guestMe.body().trim());
    }

    // =========================================================================
    // CART API TESTS
    // =========================================================================

    @Test
    public void testCartAddItemsCalculateSubtotalAndTotal() throws Exception {
        int prod1 = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Keyboard", "Mechanical", 100.00, 10, null, null, true
        );
        int prod2 = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Mouse", "Optical", 50.00, 10, null, null, true
        );

        String guestSession = "guest_api_session_1";
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // 1. Add 2 units of prod1
        String addPayload1 = "{\"product_id\":" + prod1 + ",\"quantity\":2}";
        HttpResponse<String> addResp1 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-ID", guestSession)
                        .POST(HttpRequest.BodyPublishers.ofString(addPayload1))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, addResp1.statusCode());
        JsonNode cart1 = mapper.readTree(addResp1.body());
        assertEquals(1, cart1.get("items").size());
        assertEquals(200.00, cart1.get("total_price").asDouble(), 0.001);

        // 2. Add 1 unit of prod2
        String addPayload2 = "{\"product_id\":" + prod2 + ",\"quantity\":1}";
        HttpResponse<String> addResp2 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-ID", guestSession)
                        .POST(HttpRequest.BodyPublishers.ofString(addPayload2))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, addResp2.statusCode());
        JsonNode cart2 = mapper.readTree(addResp2.body());
        assertEquals(2, cart2.get("items").size());
        assertEquals(250.00, cart2.get("total_price").asDouble(), 0.001);

        // 3. Add 1 more unit of prod1 (cumulative quantity test)
        String addPayload3 = "{\"product_id\":" + prod1 + ",\"quantity\":1}";
        HttpResponse<String> addResp3 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-ID", guestSession)
                        .POST(HttpRequest.BodyPublishers.ofString(addPayload3))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, addResp3.statusCode());
        JsonNode cart3 = mapper.readTree(addResp3.body());
        assertEquals(2, cart3.get("items").size());
        assertEquals(350.00, cart3.get("total_price").asDouble(), 0.001);
    }

    @Test
    public void testCartAddQuantityExceedsAvailableStockValidation() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Limited GPU", "Rare item", 500.00, 3, null, null, true
        );

        String guestSession = "guest_api_session_overflow";
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // 1. Attempt to add more than total stock immediately (4 > 3)
        String overflowPayload = "{\"product_id\":" + prodId + ",\"quantity\":4}";
        HttpResponse<String> resp1 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-ID", guestSession)
                        .POST(HttpRequest.BodyPublishers.ofString(overflowPayload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(400, resp1.statusCode());
        assertTrue(resp1.body().contains("Available stock: 3"));

        // 2. Add valid quantity (2)
        String validPayload = "{\"product_id\":" + prodId + ",\"quantity\":2}";
        HttpResponse<String> resp2 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-ID", guestSession)
                        .POST(HttpRequest.BodyPublishers.ofString(validPayload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, resp2.statusCode());

        // 3. Attempt to add 2 more (already 2 in cart, total would be 4 > 3)
        HttpResponse<String> resp3 = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-ID", guestSession)
                        .POST(HttpRequest.BodyPublishers.ofString(validPayload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(400, resp3.statusCode());
        assertTrue(resp3.body().contains("already in cart: 2"));
    }

    @Test
    public void testCartDeleteItemAndRecalculateTotal() throws Exception {
        int prod1 = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Monitor", "4K", 300.00, 5, null, null, true
        );
        int prod2 = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Desk Pad", "Cloth", 20.00, 10, null, null, true
        );

        String session = "guest_api_delete_session";
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // Add both
        client.send(HttpRequest.newBuilder().uri(URI.create(APP_URL + "/api/cart/items"))
                .header("Content-Type", "application/json").header("X-Session-ID", session)
                .POST(HttpRequest.BodyPublishers.ofString("{\"product_id\":" + prod1 + ",\"quantity\":1}")).build(), HttpResponse.BodyHandlers.ofString());
        client.send(HttpRequest.newBuilder().uri(URI.create(APP_URL + "/api/cart/items"))
                .header("Content-Type", "application/json").header("X-Session-ID", session)
                .POST(HttpRequest.BodyPublishers.ofString("{\"product_id\":" + prod2 + ",\"quantity\":2}")).build(), HttpResponse.BodyHandlers.ofString());

        // Delete prod1
        HttpResponse<String> delResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items/" + prod1))
                        .header("X-Session-ID", session)
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, delResp.statusCode());
        assertTrue(delResp.body().contains("Item removed from cart"));

        // Verify cart has only prod2 and total is 40.00
        HttpResponse<String> cartResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart"))
                        .header("X-Session-ID", session)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        JsonNode cart = mapper.readTree(cartResp.body());
        assertEquals(1, cart.get("items").size());
        assertEquals(40.00, cart.get("total_price").asDouble(), 0.001);

        // Delete non-existent item (404)
        HttpResponse<String> delNon = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items/88888"))
                        .header("X-Session-ID", session)
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(404, delNon.statusCode());
    }

    @Test
    public void testGuestCartMergesToAuthenticatedUserCartOnRegister() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Merged Mug", "Ceramic", 15.00, 10, null, null, true
        );

        String guestSession = "guest_to_merge_123";
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // 1. Guest adds product to cart
        client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-ID", guestSession)
                        .POST(HttpRequest.BodyPublishers.ofString("{\"product_id\":" + prodId + ",\"quantity\":3}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // 2. Guest registers with the same guest session header
        String regPayload = "{\"username\":\"merged_user\",\"password\":\"pass123\"}";
        HttpResponse<String> regResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .header("X-Session-ID", guestSession)
                        .POST(HttpRequest.BodyPublishers.ofString(regPayload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, regResp.statusCode());
        int userId = mapper.readTree(regResp.body()).get("id").asInt();

        // 3. User cart contains the items from guest session
        HttpResponse<String> userCartResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart"))
                        .header("X-User-ID", String.valueOf(userId))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, userCartResp.statusCode());
        JsonNode userCart = mapper.readTree(userCartResp.body());
        assertEquals(1, userCart.get("items").size());
        assertEquals(3, userCart.get("items").get(0).get("quantity").asInt());
        assertEquals(45.00, userCart.get("total_price").asDouble(), 0.001);
    }

    @Test
    public void testCheckoutAuthenticationAndStockDecrement() throws Exception {
        int prodId = TestHelper.doDatabaseAction(
                "INSERT_PRODUCT", "Checkout Item", "Stock decreases", 100.00, 5, null, null, true
        );

        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

        // 1. Unauthorized checkout fails (401)
        HttpResponse<String> anonCheckout = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/checkout"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(401, anonCheckout.statusCode());

        // 2. Register user
        String regPayload = "{\"username\":\"buyer_api\",\"password\":\"passBuyer\"}";
        HttpResponse<String> regResp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(regPayload))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        int userId = mapper.readTree(regResp.body()).get("id").asInt();

        // 3. Empty cart checkout fails (400)
        HttpResponse<String> emptyCheckout = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/checkout"))
                        .header("X-User-ID", String.valueOf(userId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(400, emptyCheckout.statusCode());
        assertTrue(emptyCheckout.body().contains("Cart is empty"));

        // 4. Add 2 items to authenticated user cart
        client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/items"))
                        .header("Content-Type", "application/json")
                        .header("X-User-ID", String.valueOf(userId))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"product_id\":" + prodId + ",\"quantity\":2}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        // 5. Successful checkout
        HttpResponse<String> successCheckout = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(APP_URL + "/api/cart/checkout"))
                        .header("X-User-ID", String.valueOf(userId))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, successCheckout.statusCode());
        JsonNode checkoutData = mapper.readTree(successCheckout.body());
        assertEquals("Order placed successfully", checkoutData.get("detail").asText());
        assertEquals(200.00, checkoutData.get("order_total").asDouble(), 0.001);

        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT quantity FROM products WHERE id = " + prodId);
        assertTrue(rs.next());
        int remainingStock = rs.getInt("quantity");
        assertEquals(3, remainingStock, "Stock should have decremented from 5 to 3");
        rs.close();

        // Verify cart is now empty in DB
        ResultSet cartRs = st.executeQuery("SELECT count(*) FROM cart_items WHERE session_id = 'user_" + userId + "'");
        assertTrue(cartRs.next());
        assertEquals(0, cartRs.getInt(1), "Cart items should have been deleted upon checkout");
        cartRs.close();
        st.close();
        conn.close();
    }
}
