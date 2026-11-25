## System under test

It doesn't need to be production-ready or robust, or use complex approaches and solutions.

It should be simple as possible.

The system is a very simple shop:
1. Catalog
- Users can view the catalog.
- Users can sort products by name and price.
- Products with quantity == 0 must not be displayed.
- Users can open a product card.
- Users can add products to the cart from the catalog.
- Users can add products to the cart from a product card page.
2. Cart
- Each selected product, its selected quantity, and total price must be displayed.
- Products can be removed from the cart.
- Selected quantities can be changed.
- The purchase button must be enabled only for registered users.
- Unregistered or unauthenticated users must be able to sign up/log in without losing their cart contents.
3. User
- Users can register from any app page (no auth, cryptography, etc. — username and password only; as simple as possible).
- Users can log in from any app page.
4. Validation
- Users cannot add more products than are available.
- Users cannot add zero or negative quantities.
- If the available quantity changes after a product is added to the cart, purchasing must return an error and update the cart quantity to the new available quantity (including 0).
- Purchasing a product with quantity == 0 must not return an error.
