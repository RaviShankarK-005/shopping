# Shopping App

A Java 17 and Spring Boot 3.5 shopping application with a browser-based storefront. It uses Spring Web, Spring Data JPA, Bean Validation, and an in-memory H2 database.

Features include product listings, popular and latest-product dashboards, wishlists, shopping carts, active discounts, idempotent checkout, and timed stock reservations for high-demand products.

## Requirements

- Java 17 or later
- Maven 3.9 or later

## Run locally

```powershell
mvn spring-boot:run
```

Open the storefront at [http://localhost:8080/](http://localhost:8080/). The H2 console is at [http://localhost:8080/h2-console](http://localhost:8080/h2-console).

H2 console connection details:

| Setting | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:shoppingdb` |
| User name | `sa` |
| Password | (blank) |

The database is recreated when the application starts, and sample products and discount codes are loaded into an empty database. Data is not persisted after the application stops. The default shop location is `London, UK`.

The API is restricted to London requests by `LocationInterceptor`. It allows configured developer IPs (`shop.allowed-ips`), a London `CF-IPCity` header, or a London result from the GeoIP lookup. The storefront and H2 console are excluded from this API check. The location check relies on request IP/forwarding headers and external GeoIP services; configure and secure these appropriately before deployment.

## Run tests

```powershell
mvn test
```

The tests cover application startup, product-name search, generated and time-valid discounts, wishlist operations, stock reservation and expiry, checkout and retries, dashboard result limits, request validation, discount calculations, and concurrency/integrity cases such as users racing for the same stock.

## Storefront

The static storefront is served by Spring Boot at `/`. It calls the same `/api` endpoints as the backend; there is no separate frontend server or Node.js setup.

It provides:

- Popular, wishlist, latest-product, and active-discount sections.
- Product-name search from the header, with case-insensitive partial matching.
- Product quantity controls, stock availability labels, and like/unlike actions.
- Cart quantity changes, removal, and a visible high-demand reservation countdown.
- Discount application with a subtotal, saving, and total preview.
- Checkout with a client-generated order ID reused after uncertain network/server failures.
- Order history and a demo form for adding products.

The user ID selector is a local testing aid, defaults to `1`, and is not authentication. Product and discount management endpoints are also not authenticated.

## API

All endpoints are under `/api`. Request and response bodies use JSON. Validation errors and invalid operations generally return `400`; missing products/discounts raised as `NoSuchElementException` return `404`; database uniqueness conflicts return `409`. The exact behavior depends on the endpoint and exception.

### Products

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/items` | Create a product (`201 Created`) |
| `GET` | `/api/items` | List all products |
| `GET` | `/api/items/search?query=watch` | Search product names (case-insensitive substring match) |
| `GET` | `/api/items/{id}` | Get one product |
| `PUT` | `/api/items/{id}` | Update a product |
| `DELETE` | `/api/items/{id}` | Delete a product (`204 No Content`) |
| `GET` | `/api/items/popular?limit=20&offset=0` | List high-demand products |
| `GET` | `/api/items/new?limit=20&offset=0` | List newest products |

Example create/update body:

```json
{
  "name": "Wireless Headphones",
  "price": 79.99,
  "highDemand": true,
  "stock": 50
}
```

`stock` is total stock; `reserved` is the quantity held for high-demand cart entries; `availableStock` is `stock - reserved`. Dashboard endpoints default to a page size of 20 and offset 0. The offset is converted to a page number using integer division by `limit`; use offsets that are multiples of the limit for page-aligned results. `GET /api/items` returns the complete, unpaginated product list.
Search trims surrounding whitespace and returns an empty list for a missing or blank query. Results are ordered by product name.

### Wishlist

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/users/{userId}/wishlist/{itemId}` | Like a product (`201 Created`) |
| `GET` | `/api/users/{userId}/wishlist` | List a user's liked products |
| `DELETE` | `/api/users/{userId}/wishlist/{itemId}` | Unlike a product (`204 No Content`) |

Duplicate likes are rejected. The API uses a numeric `userId`; it does not create or authenticate user accounts.

### Cart

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/users/{userId}/cart/items` | Add a product to the cart (`201 Created`) |
| `GET` | `/api/users/{userId}/cart` | Get cart entries |
| `PUT` | `/api/users/{userId}/cart/items/{itemId}?quantity=2` | Set a cart quantity |
| `DELETE` | `/api/users/{userId}/cart/items/{itemId}` | Remove an entry (`204 No Content`) |

Example add request:

```json
{
  "itemId": 1,
  "quantity": 2
}
```

Adding a product already in the cart increases its quantity. High-demand products reserve stock for 15 minutes, starting when units are first reserved; later quantity changes do not extend the deadline. Removing the entry releases its reservation. An expired reservation is rejected at checkout; adding the product again releases the expired hold and starts a fresh reservation.

Other products are not reserved on add, but the service checks available stock when adding or changing quantities and checks it again at checkout.

An expiry job checks reservations every 30 seconds. It releases expired stock and removes the corresponding cart entry. Because the job runs periodically, an expired entry may remain visible briefly, but checkout checks the timestamp directly.

Stock-changing operations use pessimistic item-row locks. Checkout locks item rows in ascending item ID order, then reads cart entries, reducing stale reads and deadlock risk when concurrent requests update the same products.

### Discounts

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/discounts` | Create a discount (`201 Created`) |
| `GET` | `/api/discounts` | List all discounts |
| `GET` | `/api/discounts/available` | List discounts currently within their validity dates |
| `GET` | `/api/discounts/{code}` | Get a discount by code |

Example create request:

```json
{
  "discountType": "PERCENT",
  "discountValue": 10,
  "itemId": 1,
  "validFrom": "2026-11-12T00:00:00",
  "validTo": "2026-12-31T23:59:59"
}
```

The server generates a 12-character uppercase hexadecimal code and returns it in the discount response. `itemId`, `validFrom`, and `validTo` are optional. Without an `itemId`, the discount applies to all products. Missing validity dates mean there is no bound on that side of the validity period.

Supported `discountType` values:

- `PERCENT`: percentage discount, limited to 100%.
- `FIXED`: amount deducted once from the eligible subtotal and capped at that subtotal.

Discount redemption limits and usage tracking are not implemented.

### Orders and checkout

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/users/{userId}/orders/checkout` | Checkout the user's cart |
| `GET` | `/api/users/{userId}/orders` | List the user's orders, newest first |

Checkout request:

```json
{
  "orderId": "ORDER-USER-1-001",
  "discountCode": "A1B2C3D4E5F6"
}
```

`discountCode` is optional. The client supplies `orderId`, which is both the order's primary key and its idempotency key. Generate a new ID for each new checkout and reuse that exact ID if a timeout or server error leaves the result uncertain.

- A new successful checkout returns `201 Created`.
- A retry with the same ID and user returns the existing order with `200 OK`, without deducting stock again.
- Reusing an ID for a different user is rejected.

Checkout, stock changes, order creation, and cart clearing occur in one transaction. Each order stores its purchased items as a JSON snapshot string rather than a separate order-items table. Totals are rounded to two decimal places using half-up rounding.

## Example flow

1. Create a product with `POST /api/items`.
2. Add it using `POST /api/users/1/cart/items`.
3. Optionally create a discount with `POST /api/discounts`.
4. Submit `POST /api/users/1/orders/checkout` with a unique `orderId` and optional discount code.
5. If the result is uncertain, retry with the same `orderId`.

## Configuration and limitations

- Spring Boot 3.5.7, Java 17, Spring Web, Spring Data JPA, Bean Validation, and H2 are used; Spring Boot DevTools is an optional runtime dependency.
- `src/main/resources/application.properties` sets port `8080`, the in-memory H2 connection, schema recreation on startup, the H2 console, and `shop.location`.
- Product dashboard queries use database indexes on `high_demand, listed_at` and `listed_at`. Dashboard results are not cached.
- Cart entries use `(user_id, item_id)` as a composite key. Checkout uses the client-provided order ID as its primary key.
- Authentication, user management, discount redemption limits, pagination for the full product list, and protected product/discount administration are not implemented.
- `LocalDateTime` is used without a configured London time zone. API timestamps have no explicit zone information.
- Entities are returned directly from API endpoints. The H2 in-memory database is intended for local/demo use, not multi-instance production persistence.
- Stock shortfalls and many invalid cart/checkout operations return `400 Bad Request`; missing items/discounts may return `404 Not Found`, and database uniqueness conflicts return `409 Conflict`.
- The location check uses request headers and external IP lookup services; it is a demo restriction, not a substitute for trusted proxy configuration or robust geographic access control.

## Project structure

```text
src/main/java/com/example/shopping/
  ShoppingApplication.java       Spring Boot entry point and scheduling
  SampleDataConfig.java          Sample products and discounts
  cart/                          Cart API, composite key, persistence, and reservation logic
  common/                        API error handling and London API interceptor configuration
  discount/                      Discount API, model, persistence, and validation
  item/                          Product API, model, persistence, and stock rules
  order/                         Checkout API, idempotency, and order persistence
  reservation/                   Scheduled reservation expiry
  wishlist/                      Wishlist API and persistence
src/main/resources/
  application.properties         Runtime and H2 configuration
  static/index.html              Browser storefront (HTML, CSS, and JavaScript)
src/test/java/com/example/shopping/
  ShoppingApplicationTests.java       Application and service behavior
  ConcurrencyAndIntegrityTests.java    Committed transactions, concurrency, and integrity
```

## JavaDoc

All 33 Java application source files have JavaDoc on their types, with API and key domain/service operations documented. Generate browsable documentation with:

```powershell
mvn javadoc:javadoc
```

The generated site is written to `target/site/apidocs/`.
