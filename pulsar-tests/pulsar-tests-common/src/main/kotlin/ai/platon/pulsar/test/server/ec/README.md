# Mock Ecommerce Site Specification (Dynamic Rendering)

## Prerequisite

Read `AGENTS.md` in the project root to guide your actions.

## Purpose
Implement a fully dynamic mock ecommerce website served under the `/ec` path via `EcommerceController.kt` (bootstrapped by `MockSiteApplication.kt`).
All pages (home, category/list, product) must be rendered server-side from a **single JSON data file** loaded once at startup.

## High-Level Goals
- 1 home page listing 20 category links.
- 20 category (list) pages, each showing 5–12 products (total products ≥ 100).
- Product detail pages for every listed product.
- Deterministic, reproducible data generation (seeded) to keep IDs stable across runs.
- Clean semantic HTML with unique IDs and reusable classes to aid automated testing / scraping.
- Proper 400 / 404 handling.

## Routes
| Route | Description | Notes |
|-------|-------------|-------|
| GET `/ec/` | Home page with all categories | 20 links: `/ec/b?node={categoryId}` |
| GET `/ec/b?node={categoryId}` | Category list page | `node` required; 400 if missing, 404 if unknown |
| GET `/ec/dp/{productId}` | Product detail page | 404 if product missing or inconsistent category |
| GET `/ec/static/*` | Optional static assets (images/css) | Can serve from classpath |
| (any other `/ec/*`) | Not found | 404 |

## Data Source
Single JSON file loaded from classpath at:
```
/static/generated/mock-amazon/data/products.json
```
(filesystem: `src/main/resources/static/generated/mock-amazon/data/products.json`)
Load once at application start; keep immutable in memory.

### JSON Structure (Schema)
```
{
  "meta": {
    "version": 1,
    "generatedAt": "2025-01-01T00:00:00Z",
    "seed": 12345
  },
  "categories": [
    { "id": "1292115012", "name": "Electronics", "slug": "electronics" },
    { "id": "1292115013", "name": "Home", "slug": "home" }
    // ... total 20
  ],
  "products": [
    {
      "id": "B08PP5MSVB",
      "name": "Wireless Noise-Cancelling Headphones",
      "categoryId": "1292115012",
      "price": 199.99,
      "currency": "USD",
      "image": "/ec/static/img/B08PP5MSVB.jpg",
      "rating": 4.4,
      "ratingCount": 312,
      "badges": ["Bestseller"],
      "features": ["Bluetooth 5.2", "30h battery"],
      "description": "High fidelity wireless headphones.",
      "specs": {"weight": "240g", "color": "Black"},
      "inventory": {"inStock": true, "qty": 42},
      "createdAt": "2025-01-01T00:00:00Z",
      "updatedAt": "2025-01-01T00:00:00Z"
    }
    // ... more
  ]
}
```

### Data Rules
- Exactly 20 distinct categories.
- Each product belongs to exactly one `categoryId` present in categories.
- ≥ 5 and ≤ 12 products per category (for variety) → easy pagination later.
- Product IDs unique (Amazon-like IDs ok, e.g. `B0...`).
- Prices: positive, formatted with 2 decimals when rendered.
- Deterministic generation: if you implement a generator, seed the RNG (store seed in `meta.seed`).

## Page Templates
Three templates are loaded from the classpath at startup by `HtmlRenderer.kt`:
- Home: `ec-home.html` — category navigation with `<!--CATEGORY_LINKS-->` placeholder.
- Category list: `ec-category.html` — product grid with `<!--PRODUCT_LIST-->` placeholder.
- Product detail: `ec-product.html` — full product rendering with `{{PLACEHOLDER}}` markers.

All templates are under:
```
/static/generated/mock-amazon/ec-home.html
/static/generated/mock-amazon/ec-category.html
/static/generated/mock-amazon/ec-product.html
```

> **CRITICAL REQUIREMENT: DO NOT ALTER THE TEMPLATE LAYOUT, EXISTING JAVASCRIPT, OR CSS—ONLY INJECT DYNAMIC PRODUCT DATA INTO PLACEHOLDERS. EXISTING ID AND CLASS CONVENTIONS MUST BE PRESERVED TO KEEP TESTS STABLE.**

## Rendering Requirements
### Common
- UTF-8 output.
- `<title>` reflects page context: `Category: Electronics` or `Product: Wireless Noise-Cancelling Headphones`.
- Include canonical-like structure for consistent scraping.
- Stable, descriptive IDs (unique per page) and reusable classes for selectors.

### Suggested ID / Class Conventions
- Home: `#category-list`, items: `li.category-item[data-category-id]`, link id: `cat-link-{categoryId}`.
- Category Page wrapper: `#category-page[data-category-id]`.
- Product cards: `article.product-card#product-{productId}[data-category-id]`.
- Inside card: `h2.product-title`, price span: `span.product-price[data-product-id]`, rating: `span.product-rating`, badge container: `.product-badges`.
- Product Detail root: `#product-page[data-product-id][data-category-id]`.
- Detail fields: `#product-title`, `#product-price`, `#product-rating`, `#product-rating-count`, `#product-category-link`, features list `#product-features`, specs table `#product-specs`.
- Use `alt` attributes for images: `alt="{name}"`.

### Accessibility / Semantics
- Use `<nav>` for category navigation on home.
- Use `<section>` / `<article>` for product listings.
- Provide `<ul>` for feature lists; `<table>` only for tabular specs.

## Error Handling
| Scenario | Status | Response |
|----------|--------|----------|
| Missing `node` param on `/ec/b` | 400 | Plain text or simple HTML: "Missing category parameter" |
| Unknown category | 404 | "Category not found" |
| Unknown product | 404 | "Product not found" |
| Product exists but not in data (should not happen) | 404 | Same as unknown |
| Any other `/ec/*` | 404 | Standard not found |

Keep error pages lightweight, also with a unique id: `#error-page` and a class `error-code-404` etc.

## Validation / Test Checklist
Automated or manual tests should assert:
1. GET `/ec/` returns 200 and contains 20 links with `cat-link-` IDs.
2. Each category link resolves (200) and only shows products whose cards have `data-category-id` matching the `node` param.
3. Each product card link resolves (200) and product detail page contains matching `#product-page[data-product-id]`.
4. Invalid category (`/ec/b?node=NOPE`) returns 404.
5. Missing node (`/ec/b`) returns 400.
6. Invalid product (`/ec/dp/DOESNOTEXIST`) returns 404.
7. All prices show two decimals (regex: `\$\d+\.\d{2}`).
8. No duplicate IDs in any page (spot check by parsing DOM or regex + set logic).
9. Total product count ≥ 100; distribution respects 5–12 per category.

## Optional Enhancements (Do NOT block MVP)
- Query pagination: `/ec/b?node=1292115012&page=2` (deterministic sort by product ID).
- Simple search: `/ec/search?q=headphones`.
- Badge filtering or price range.
- Regeneration endpoint (dev only) to rebuild JSON with same seed or new seed.

## Logging
- On startup: log categories count, product count, seed.
- On 404/400: concise log line with path + reason.

## Done Definition
- All required routes implemented.
- Data served purely from JSON (no hardcoded product logic except generation step if included).
- Acceptance checklist passes.
- Deterministic repeatable product set.
- Semantic, test-friendly HTML.

## Architecture Overview

The implementation consists of six files in the `ec` package:

| File | Role |
|------|------|
| `CatalogModels.kt` | Data classes: `CatalogMeta`, `Category`, `Product`, `Catalog` |
| `CatalogLoader.kt` | Loads and parses `products.json` at startup, builds lookup indexes |
| `CatalogService.kt` | Service layer exposing category/product queries |
| `HtmlRenderer.kt` | Server-side HTML rendering via template + placeholder injection |
| `EcommerceController.kt` | Spring MVC `@Controller` — route handlers for all `/ec/*` endpoints |
| `EcControllers.kt` | Reserved for additional controllers (currently empty) |

Data is pre-generated and stored in a single JSON file (`products.json`). There is no runtime generation step — the JSON is the source of truth. The seed in `meta.seed` records how the data was originally generated for reproducibility.

## Maintenance Notes
- If schema evolves, bump `meta.version` and handle backward compatibility in `CatalogLoader`.
- All product images reference `/ec/static/img/placeholder.png` — a single placeholder served from `static/ec/static/img/`.
- If adding new ID or class names, update this document's conventions section.

---
This document supersedes the previous minimal instructions and provides a precise, testable contract for the mock ecommerce site implementation.
