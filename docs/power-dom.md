Data Extraction
=

Browser4base uses [jsoup](https://jsoup.org/) to extract data from HTML documents. Jsoup parses HTML into the same DOM as modern browsers. See [selector-syntax](https://jsoup.org/cookbook/extracting-data/selector-syntax) for all supported CSS selectors, and [here](https://www.w3schools.com/cssref/css_selectors.php) for a detailed reference of standard CSS selectors.

The source code of modern web pages changes very frequently, but the "look" of a website's pages doesn't change much, ensuring a consistent user experience. In such cases, examining web page elements from a visual perspective becomes particularly effective. To better view web pages through visual and numerical characteristics, PulsarR extends CSS, enabling you to tackle the most complex real-world problems from the angles of visual and numerical features.

First, prepare a document:

```kotlin
// Load a page; if the page is expired or being loaded for the first time, download it from the internet
val page = session.load(url, "-expires 1d")
// Parse the page content into a Jsoup document
val document = session.parse(page)
```

The simplest data extraction:

```kotlin
// Do something with the document
val title = document.selectFirst('.title').text()
val price = document.selectFirst('.price').text()
```

Slightly more complex examples:

```kotlin
// a with href
val links = document.select("a[href]")

// img with src ending .png
val pngs = document.select("img[src$=.png]")

// div with class=masthead
val masthead = document.select("div.masthead").first()

// direct a after h3
val resultLinks = document.select("h3.r > a")

// finds sibling td element preceded by th who contains text "Best Sellers Rank"
val bsr = document.select("th:contains(Best Sellers Rank) ~ td")
```

PulsarRPA extends CSS and Jsoup to solve complex real-world problems:

1. Computes numerical features for each DOM Node
2. Extends CSS syntax to support mathematical operations in CSS queries
3. Provides a batch of utility methods to simplify and enhance DOM operations

PulsarR computes numerical features for each DOM Node. Currently, PulsarR supports the following numerical features:

```
top,       // the top coordinate in pixel of the element
left,      // the left coordinate in pixel of the element
width,     // the width in pixel of the element
height,    // the height in pixel of the element
char,      // number of chars inside this node
txt_nd,    // number of descend text nodes
img,       // number of descend images
a,         // number of descend anchors
sibling,   // number of sibling nodes
child,     // number of children
dep,       // node depth
seq,       // node sequence in the document
txt_dns    // text node density
```

PulsarRPA extends CSS syntax to support mathematical operations in CSS queries.

To compensate for the shortcomings of existing CSS expressions, PulsarR introduces the **:expr(expression)** pseudo-selector, allowing us to perform mathematical operations in CSS.

For example:

```
/* Select links whose left sidebar width is 500 */
a:expr(left < 100 && width == 500)

/* Select images with an area greater than 1600 */
img:expr(width * height > 1600)

/* Select all div elements in the document that contain one image and have width and height between 400 and 500 */
div:expr(img == 1 && width > 400 && width < 500 && height > 400 && height < 500)
```

Select all 400 x 400 images in the document:

```kotlin
val imgs400x400 = document.select("img:expr(width == 400 && height == 400)")
```

Select all div elements in the document whose width and height are between 400 and 500, and that contain one image:

```kotlin
val expr = "img == 1 && width > 400 && width < 500 && height > 400 && height < 500"
val elements = doc.select("div:expr($expr)")
```

The table below lists the standard operators.

### Arithmetic Operators

| Name | Description                             |
| ---- | --------------------------------------- |
| -    | The prefix minus operator, like in "-2" |
| +    | The prefix plus operator, like in "+2"  |
| -    | The infix minus operator, like in "5-2" |
| +    | The infix plus operator, like in "5+2"  |
| *    | The multiplication operator             |
| /    | The division operator                   |
| ^    | The power-of operator                   |
| %    | The modulo operator (remainder)         |

### Boolean Operators

| Name   | Description                         |
| ------ | ----------------------------------- |
| =, ==  | The equals operator                 |
| !=, <> | The not equals operator             |
| !      | The prefix not operator, like in !a |
| >      | The greater than operator           |
| >=     | The greater equals operator         |
| <      | The less than operator              |
| <=     | The less equals operator            |
| &&     | The and operator                    |
| \|\|   | The or operator                     |

In PulsarR Professional Edition, we will introduce more interesting numerical features to support machine learning and [artificial intelligence](https://zhuanlan.zhihu.com/p/576098111), such as topology-related features.

In the [X-SQL](X-SQL.md) chapter, we will detail how to use CSS selectors in X-SQL to select elements and their attributes. A comprehensive real-world example is [x-asin.sql](https://github.com/platonai/exotic-amazon/tree/main/src/main/resources/sites/amazon/crawl/parse/sql/crawl/x-asin.sql) ([domestic mirror](https://gitee.com/platonai_galaxyeye/exotic-amazon/blob/main/src/main/resources/sites/amazon/crawl/parse/sql/crawl/x-asin.sql)), which uses a variety of CSS selectors to solve the most complex e-commerce web page data extraction problems. Below are some excerpts:

```sql
dom_first_attr(dom, '#landingImage, #imgTagWrapperId img, #imageBlock img:expr(width>400)', 'data-old-hires') as `imgsrc`,
dom_first_attr(dom, '#landingImage, #imgTagWrapperId img, #imageBlock img:expr(width>400)', 'data-a-dynamic-image') as `dynamicimgsrcs`,
dom_first_slim_html(dom, '#landingImage, #imgTagWrapperId img, #imageBlock img:expr(width>400)') as `img`,
dom_first_text(dom, '#price tr td:contains(List Price) ~ td') as `listprice`,
dom_first_text(dom, '#price tr td:matches(^Price) ~ td, #price_inside_buybox') as `price`,
dom_first_text(dom, '#price #priceblock_dealprice, #price tr td:contains(Deal of the Day) ~ td') as `withdeal`,
dom_first_text(dom, '#price #dealprice_savings .priceBlockSavingsString, #price tr td:contains(You Save) ~ td') as `yousave`,
```
