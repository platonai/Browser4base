# PowerDOM — Extended CSS Selectors & Numerical Features

PulsarRPA uses [jsoup](https://jsoup.org/) to extract data from HTML documents. jsoup parses HTML into the same DOM as modern browsers. See [selector-syntax](https://jsoup.org/cookbook/extracting-data/selector-syntax) for all supported CSS selectors.

Modern web page source code changes frequently, but the visual appearance of a page tends to remain stable in order to deliver a consistent user experience. This makes inspecting elements by their visual and numerical characteristics especially effective. To solve complex real-world problems, PulsarRPA extends CSS with visual and numerical feature support.

---

## 1. Preparing a Document

Load a page and parse it into a jsoup document:

```kotlin
// Load a page — fetch from the internet if expired or first-time access
val page = session.load(url, "-expires 1d")

// Parse the page content into a jsoup document
val document = session.parse(page)
```

---

## 2. Basic CSS Selectors

Common standard jsoup / CSS selector usage:

```kotlin
// Simple selection
val title = document.selectFirst(".title").text()
val price = document.selectFirst(".price").text()

// Anchor tags with an href attribute
val links = document.select("a[href]")

// Images whose src ends with .png
val pngs = document.select("img[src$=.png]")

// Div with class "masthead"
val masthead = document.select("div.masthead").first()

// Direct child a of h3
val resultLinks = document.select("h3.r > a")

// Sibling td preceded by th containing text "Best Sellers Rank"
val bsr = document.select("th:contains(Best Sellers Rank) ~ td")
```

---

## 3. Browser4base Extensions

On top of standard CSS / jsoup, PulsarRPA provides three core extensions:

1. **Numerical features** — computed for every DOM node (position, dimensions, child counts, etc.)
2. **`:expr()` pseudo-selector** — enables math expressions inside CSS queries
3. **Utility methods** — a collection of convenience methods that simplify and enhance DOM manipulation

---

## 4. Numerical Features

PulsarRPA computes the following numerical features for every DOM node. These can be used directly inside `:expr()`:

| Feature   | Description                               |
|-----------|-------------------------------------------|
| `top`     | Top coordinate of the element (pixels)    |
| `left`    | Left coordinate of the element (pixels)   |
| `width`   | Width of the element (pixels)             |
| `height`  | Height of the element (pixels)            |
| `char`    | Number of characters inside the node      |
| `txt_nd`  | Number of descendant text nodes           |
| `img`     | Number of descendant images               |
| `a`       | Number of descendant anchors              |
| `sibling` | Number of sibling nodes                   |
| `child`   | Number of children                        |
| `dep`     | Node depth                                |
| `seq`     | Node sequence in the document             |
| `txt_dns` | Text node density                         |

> PulsarRPA Professional Edition will introduce additional topology-related numerical features to support machine learning and AI.

---

## 5. The `:expr()` Pseudo-Selector

The `:expr(expression)` pseudo-selector fills a gap in standard CSS by allowing math operations directly inside CSS queries.

### Syntax

```css
selector:expr(condition)
```

### Examples

```css
/* Select links in the left sidebar with width 500 */
a:expr(left < 100 && width == 500)

/* Select images with area greater than 1600 */
img:expr(width * height > 1600)

/* Select divs that contain exactly one image and have width & height between 400 and 500 */
div:expr(img == 1 && width > 400 && width < 500 && height > 400 && height < 500)
```

### Kotlin Usage

```kotlin
// Select all 400×400 images in the document
val imgs400x400 = document.select("img:expr(width == 400 && height == 400)")

// Select divs with width & height between 400–500 containing exactly one image
val expr = "img == 1 && width > 400 && width < 500 && height > 400 && height < 500"
val elements = doc.select("div:expr($expr)")
```

---

## 6. Operator Reference

### Arithmetic Operators

| Operator | Description              | Example |
|----------|--------------------------|---------|
| `-`      | Prefix negation          | `-2`    |
| `+`      | Prefix positive          | `+2`    |
| `-`      | Infix subtraction        | `5-2`   |
| `+`      | Infix addition           | `5+2`   |
| `*`      | Multiplication           | `a*b`   |
| `/`      | Division                 | `a/b`   |
| `^`      | Exponentiation           | `a^b`   |
| `%`      | Modulo (remainder)       | `a%b`   |

### Boolean / Comparison Operators

| Operator    | Description      |
|-------------|------------------|
| `=`、`==`   | Equal to         |
| `!=`、`<>`  | Not equal to     |
| `!`         | Prefix logical NOT |
| `>`         | Greater than     |
| `>=`        | Greater or equal |
| `<`         | Less than        |
| `<=`        | Less or equal    |
| `&&`        | Logical AND      |
| `\|\|`      | Logical OR       |

---

## 7. Using in X-SQL

In [X-SQL](https://github.com/platonai/pulsar-sql), CSS selectors can select elements and their attributes. The following snippet is from the real-world case [x-asin.sql](https://github.com/platonai/pulsar-sql/blob/master/src/main/resources/sql/x-asin.sql), showing how to combine various CSS selectors to solve complex e-commerce data extraction problems:

```sql
dom_first_attr(dom, '#landingImage, #imgTagWrapperId img, #imageBlock img:expr(width>400)', 'data-old-hires') as `imgsrc`,
dom_first_attr(dom, '#landingImage, #imgTagWrapperId img, #imageBlock img:expr(width>400)', 'data-a-dynamic-image') as `dynamicimgsrcs`,
dom_first_slim_html(dom, '#landingImage, #imgTagWrapperId img, #imageBlock img:expr(width>400)') as `img`,
dom_first_text(dom, '#price tr td:contains(List Price) ~ td') as `listprice`,
dom_first_text(dom, '#price tr td:matches(^Price) ~ td, #price_inside_buybox') as `price`,
dom_first_text(dom, '#price #priceblock_dealprice, #price tr td:contains(Deal of the Day) ~ td') as `withdeal`,
dom_first_text(dom, '#price #dealprice_savings .priceBlockSavingsString, #price tr td:contains(You Save) ~ td') as `yousave`,
```

---
