package ai.platon.pulsar.test.server

import ai.platon.pulsar.common.ResourceLoader
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MockSiteController {
    @GetMapping("/")
    fun home(): String {
        return "Welcome! This site is used for internal test."
    }

    @GetMapping("hello")
    fun hello(): String {
        return "Hello, World!"
    }

    @GetMapping("text", produces = ["text/plain"])
    fun text(): String {
        return "Hello, World! This is a plain text."
    }

    @GetMapping("csv", produces = ["text/csv"])
    fun csv(): String {
        return """
1,2,3,4,5,6,7
a,b,c,d,e,f,g
1,2,3,4,5,6,7
a,b,c,d,e,f,g
""".trimIndent()
    }

    @GetMapping("json", produces = ["application/json"])
    fun json(): String {
        return """{"message": "Hello, World! This is a json."}"""
    }

    @GetMapping("robots.txt", produces = ["application/text"])
    fun robots(): String {
        return """
            User-agent: *
            Disallow: /exec/obidos/account-access-login
            Disallow: /exec/obidos/change-style
            Disallow: /exec/obidos/flex-sign-in
            Disallow: /exec/obidos/handle-buy-box
            Disallow: /exec/obidos/tg/cm/member/
            Disallow: /gp/aw/help/id=sss
            Disallow: /gp/cart
            Disallow: /gp/flex
            Disallow: /gp/product/e-mail-friend
            Disallow: /gp/product/product-availability
            Disallow: /gp/product/rate-this-item
            Disallow: /gp/sign-in
            Disallow: /gp/reader
            Disallow: /gp/sitbv3/reader
        """.trimIndent()
    }

    @GetMapping("amazon/home.htm", produces = ["text/html"])
    fun amazonHome(): String {
        return ResourceLoader.readString("pages/amazon/home.htm")
    }

    @GetMapping("amazon/product.htm", produces = ["text/html"])
    fun amazonProduct(): String {
        return ResourceLoader.readString("pages/amazon/B08PP5MSVB.original.htm")
    }

    // =========================================================================
    // Rich HTML test pages for UDF testing
    // =========================================================================

    /**
     * A product listing page with a header, breadcrumb nav, product cards
     * (each containing image, title, price, rating, description, and links),
     * a comparison table, sidebar links, and a footer.
     */
    @GetMapping("udf-test/product-listing.html", produces = ["text/html"])
    fun udfProductListing(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Product Listing - UDF Test Store</title>
    <meta name="description" content="Test product listing page for UDF testing">
</head>
<body>
    <header id="site-header" class="header">
        <div id="logo">
            <a href="/"><img src="/img/logo.png" width="200" height="60" alt="UDF Test Store"></a>
        </div>
        <nav id="breadcrumb" aria-label="Breadcrumb">
            <ul>
                <li><a href="/">Home</a></li>
                <li><a href="/category/electronics">Electronics</a></li>
                <li><span>Smartphones</span></li>
            </ul>
        </nav>
    </header>

    <main id="content">
        <h1>Smartphones</h1>
        <p>Showing 1-5 of 25 results</p>

        <div id="filter-bar" class="filter-bar">
            <span class="filter-label">Price Range:</span>
            <span class="price-min">\$100</span> - <span class="price-max">\$2,000</span>
            <span class="result-count">25 results</span>
        </div>

        <div id="product-list" class="product-grid">
            <!-- Product Card 1 -->
            <div class="product-card" data-product-id="P1001" data-category="smartphone">
                <div class="product-image">
                    <a href="/product/phone-1001.html">
                        <img src="/img/phone-1001.jpg" width="300" height="300" alt="PhoneX Pro 256GB">
                    </a>
                </div>
                <div class="product-info">
                    <h2 class="product-title">
                        <a href="/product/phone-1001.html" class="product-link">PhoneX Pro 256GB - Midnight Black</a>
                    </h2>
                    <div class="product-rating">
                        <span class="stars" title="4.5 out of 5 stars">★★★★☆</span>
                        <span class="rating-value">4.5</span>
                        <span class="review-count">(1,234 reviews)</span>
                    </div>
                    <div class="product-price">
                        <span class="currency">\$</span><span class="price-value">899.99</span>
                    </div>
                    <div class="product-description">
                        <p>Flagship smartphone with 6.7" AMOLED display, 256GB storage, 48MP triple camera system.</p>
                    </div>
                    <div class="product-meta">
                        <span class="brand">BrandX</span>
                        <span class="sku">SKU: PX-PRO-256-BK</span>
                    </div>
                </div>
            </div>

            <!-- Product Card 2 -->
            <div class="product-card" data-product-id="P1002" data-category="smartphone">
                <div class="product-image">
                    <a href="/product/phone-1002.html">
                        <img src="/img/phone-1002.jpg" width="300" height="300" alt="PhoneY Plus 128GB">
                    </a>
                </div>
                <div class="product-info">
                    <h2 class="product-title">
                        <a href="/product/phone-1002.html" class="product-link">PhoneY Plus 128GB - Ocean Blue</a>
                    </h2>
                    <div class="product-rating">
                        <span class="stars" title="4.2 out of 5 stars">★★★★☆</span>
                        <span class="rating-value">4.2</span>
                        <span class="review-count">(856 reviews)</span>
                    </div>
                    <div class="product-price">
                        <span class="currency">\$</span><span class="price-value">649.99</span>
                    </div>
                    <div class="product-description">
                        <p>Mid-range champion with 6.5" OLED display, 128GB storage, 50MP dual camera.</p>
                    </div>
                    <div class="product-meta">
                        <span class="brand">BrandY</span>
                        <span class="sku">SKU: PY-PLUS-128-BL</span>
                    </div>
                </div>
            </div>

            <!-- Product Card 3 -->
            <div class="product-card" data-product-id="P1003" data-category="smartphone">
                <div class="product-image">
                    <a href="/product/phone-1003.html">
                        <img src="/img/phone-1003.jpg" width="300" height="300" alt="PhoneZ Lite 64GB">
                    </a>
                </div>
                <div class="product-info">
                    <h2 class="product-title">
                        <a href="/product/phone-1003.html" class="product-link">PhoneZ Lite 64GB - Pearl White</a>
                    </h2>
                    <div class="product-rating">
                        <span class="stars" title="3.9 out of 5 stars">★★★☆☆</span>
                        <span class="rating-value">3.9</span>
                        <span class="review-count">(432 reviews)</span>
                    </div>
                    <div class="product-price">
                        <span class="currency">\$</span><span class="price-value">399.99</span>
                    </div>
                    <div class="product-description">
                        <p>Budget-friendly smartphone with 6.1" LCD display, 64GB storage, 12MP camera.</p>
                    </div>
                    <div class="product-meta">
                        <span class="brand">BrandZ</span>
                        <span class="sku">SKU: PZ-LITE-64-WH</span>
                    </div>
                </div>
            </div>

            <!-- Product Card 4 -->
            <div class="product-card" data-product-id="P1004" data-category="smartphone">
                <div class="product-image">
                    <a href="/product/phone-1004.html">
                        <img src="/img/phone-1004.jpg" width="300" height="300" alt="PhoneX Ultra 512GB">
                    </a>
                </div>
                <div class="product-info">
                    <h2 class="product-title">
                        <a href="/product/phone-1004.html" class="product-link">PhoneX Ultra 512GB - Titanium Gray</a>
                    </h2>
                    <div class="product-rating">
                        <span class="stars" title="4.8 out of 5 stars">★★★★★</span>
                        <span class="rating-value">4.8</span>
                        <span class="review-count">(2,567 reviews)</span>
                    </div>
                    <div class="product-price">
                        <span class="currency">\$</span><span class="price-value">1,299.99</span>
                    </div>
                    <div class="product-description">
                        <p>Ultimate flagship with 6.9" LTPO AMOLED, 512GB, 200MP quad camera, titanium frame.</p>
                    </div>
                    <div class="product-meta">
                        <span class="brand">BrandX</span>
                        <span class="sku">SKU: PX-ULT-512-TG</span>
                    </div>
                </div>
            </div>

            <!-- Product Card 5 -->
            <div class="product-card" data-product-id="P1005" data-category="smartphone">
                <div class="product-image">
                    <a href="/product/phone-1005.html">
                        <img src="/img/phone-1005.jpg" width="300" height="300" alt="FoldablePhone Flex">
                    </a>
                </div>
                <div class="product-info">
                    <h2 class="product-title">
                        <a href="/product/phone-1005.html" class="product-link">FoldablePhone Flex 256GB - Cosmic Black</a>
                    </h2>
                    <div class="product-rating">
                        <span class="stars" title="4.0 out of 5 stars">★★★★☆</span>
                        <span class="rating-value">4.0</span>
                        <span class="review-count">(678 reviews)</span>
                    </div>
                    <div class="product-price">
                        <span class="currency">\$</span><span class="price-value">1,799.99</span>
                    </div>
                    <div class="product-description">
                        <p>Revolutionary foldable design with 7.6" inner display, 256GB, 50MP camera system.</p>
                    </div>
                    <div class="product-meta">
                        <span class="brand">BrandF</span>
                        <span class="sku">SKU: FL-FLEX-256-CB</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Comparison Table -->
        <section id="comparison-section">
            <h2>Compare Products</h2>
            <table id="comparison-table" class="comparison-table">
                <thead>
                    <tr>
                        <th>Feature</th>
                        <th>PhoneX Pro</th>
                        <th>PhoneY Plus</th>
                        <th>PhoneZ Lite</th>
                        <th>PhoneX Ultra</th>
                        <th>FoldablePhone Flex</th>
                    </tr>
                </thead>
                <tbody>
                    <tr id="comparison_price_row">
                        <td>Price</td>
                        <td>\$899.99</td>
                        <td>\$649.99</td>
                        <td>\$399.99</td>
                        <td>\$1,299.99</td>
                        <td>\$1,799.99</td>
                    </tr>
                    <tr id="comparison_rating_row">
                        <td>Rating</td>
                        <td>4.5 / 5</td>
                        <td>4.2 / 5</td>
                        <td>3.9 / 5</td>
                        <td>4.8 / 5</td>
                        <td>4.0 / 5</td>
                    </tr>
                    <tr id="comparison_storage_row">
                        <td>Storage</td>
                        <td>256GB</td>
                        <td>128GB</td>
                        <td>64GB</td>
                        <td>512GB</td>
                        <td>256GB</td>
                    </tr>
                    <tr id="comparison_display_row">
                        <td>Display</td>
                        <td>6.7" AMOLED</td>
                        <td>6.5" OLED</td>
                        <td>6.1" LCD</td>
                        <td>6.9" LTPO AMOLED</td>
                        <td>7.6" Foldable AMOLED</td>
                    </tr>
                    <tr id="comparison_camera_row">
                        <td>Camera</td>
                        <td>48MP Triple</td>
                        <td>50MP Dual</td>
                        <td>12MP Single</td>
                        <td>200MP Quad</td>
                        <td>50MP Triple</td>
                    </tr>
                </tbody>
            </table>
        </section>

        <!-- Sidebar with links -->
        <aside id="sidebar" class="sidebar">
            <h3>Related Categories</h3>
            <ul class="category-links">
                <li><a href="/category/electronics/tablets">Tablets</a></li>
                <li><a href="/category/electronics/laptops">Laptops</a></li>
                <li><a href="/category/electronics/accessories">Phone Accessories</a></li>
                <li><a href="/category/electronics/wearables">Wearables</a></li>
            </ul>
            <h3>Top Brands</h3>
            <ul class="brand-links">
                <li><a href="/brand/brandx">BrandX</a></li>
                <li><a href="/brand/brandy">BrandY</a></li>
                <li><a href="/brand/brandz">BrandZ</a></li>
                <li><a href="/brand/brandf">BrandF</a></li>
            </ul>
        </aside>
    </main>

    <footer id="site-footer">
        <div class="footer-links">
            <a href="/about">About Us</a>
            <a href="/contact">Contact</a>
            <a href="/privacy">Privacy Policy</a>
            <a href="/terms">Terms of Service</a>
        </div>
        <p class="copyright">© 2024 UDF Test Store. All rights reserved.</p>
    </footer>
</body>
</html>"""
    }

    /**
     * A simple article/blog page with headings, paragraphs, lists, code blocks,
     * blockquote, images, and nested section structure for testing text extraction UDFs.
     */
    @GetMapping("udf-test/article.html", produces = ["text/html"])
    fun udfArticle(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Understanding Web Scraping with X-SQL - UDF Test Blog</title>
    <meta name="author" content="Test Author">
    <meta name="date" content="2024-01-15">
</head>
<body>
    <article id="main-article" class="blog-post" data-post-id="B2024-001">
        <header class="article-header">
            <h1>Understanding Web Scraping with X-SQL</h1>
            <div class="article-meta">
                <span class="author">By <a href="/author/test-author">Test Author</a></span>
                <span class="date">Published: <time datetime="2024-01-15">January 15, 2024</time></span>
                <span class="category">Category: <a href="/category/web-scraping">Web Scraping</a></span>
                <span class="read-time">5 min read</span>
            </div>
        </header>

        <section id="introduction" class="article-section">
            <h2>Introduction</h2>
            <p>X-SQL is a powerful extension of SQL designed for web data extraction. It combines the simplicity of CSS selectors with the power of SQL queries, making web scraping as easy as writing a SELECT statement.</p>
            <p>In this article, we'll explore the core concepts and practical applications of X-SQL for web data extraction tasks.</p>
            <blockquote>
                <p>"The web is the world's largest database. X-SQL makes it queryable." — Platon AI Team</p>
            </blockquote>
        </section>

        <section id="getting-started" class="article-section">
            <h2>Getting Started</h2>
            <p>To start using X-SQL, you need to understand two fundamental concepts:</p>
            <ol class="steps">
                <li><strong>DOM loading:</strong> Load a web page into a DOM object</li>
                <li><strong>CSS selection:</strong> Select elements from the DOM using CSS selectors</li>
                <li><strong>Data extraction:</strong> Extract text, attributes, or other data from selected elements</li>
            </ol>
            <div class="code-block">
                <pre><code>SELECT
    DOM_TEXT(DOM) AS title,
    DOM_ABS_HREF(DOM) AS link
FROM DOM_SELECT(
    DOM_LOAD('https://example.com/products'),
    '.product-card a.title'
);</code></pre>
            </div>
        </section>

        <section id="features" class="article-section">
            <h2>Key Features</h2>
            <ul class="feature-list">
                <li>
                    <h3>CSS Selector Support</h3>
                    <p>Full CSS selector support including pseudo-classes like <code>:contains()</code> and <code>:in-box()</code>.</p>
                </li>
                <li>
                    <h3>Regex Extraction</h3>
                    <p>Built-in regex functions <code>DOM_RE1</code> and <code>DOM_RE2</code> for pattern-based data extraction.</p>
                </li>
                <li>
                    <h3>Table Functions</h3>
                    <p>Powerful table-valued functions like <code>LOAD_AND_SELECT</code> for multi-row data extraction.</p>
                </li>
                <li>
                    <h3>LLM Integration</h3>
                    <p>AI-powered extraction with <code>LLM_EXTRACT</code> for complex unstructured data.</p>
                </li>
            </ul>
        </section>

        <section id="examples" class="article-section">
            <h2>Practical Examples</h2>

            <h3>Example 1: Extract Product Listings</h3>
            <div class="example" id="example-1">
                <pre><code>SELECT
    DOM_FIRST_TEXT(DOM, '.product-title') AS name,
    DOM_FIRST_TEXT(DOM, '.price-value') AS price,
    DOM_FIRST_IMG(DOM, '.product-image') AS image
FROM LOAD_AND_SELECT(
    'https://example.com/products',
    '.product-card',
    1,
    10
);</code></pre>
            </div>

            <h3>Example 2: Extract with Regex</h3>
            <div class="example" id="example-2">
                <pre><code>SELECT
    DOM_FIRST_RE1(DOM, '.sku', 'SKU:\\s*(\\S+)') AS sku
FROM LOAD_AND_SELECT(
    'https://example.com/products',
    '.product-card'
);</code></pre>
            </div>

            <h3>Example 3: Pagination Handling</h3>
            <div class="example" id="example-3">
                <pre><code>-- Load and follow all pagination links
SELECT DOM_FIRST_TEXT(DOM, 'h1') AS title
FROM LOAD_OUT_PAGES(
    'https://example.com/products?page=1',
    '.pagination a',
    1,
    5
);</code></pre>
            </div>
        </section>

        <section id="conclusion" class="article-section">
            <h2>Conclusion</h2>
            <p>X-SQL transforms the way we approach web data extraction. By combining familiar SQL syntax with powerful DOM manipulation functions, it enables both beginners and experts to extract web data efficiently.</p>
            <p>Ready to learn more? Check out the <a href="/docs">documentation</a> or join our <a href="/community">community forum</a>.</p>
        </section>

        <footer class="article-footer">
            <div class="tags">
                <span class="tag-label">Tags:</span>
                <a href="/tag/xsql" class="tag">X-SQL</a>
                <a href="/tag/web-scraping" class="tag">Web Scraping</a>
                <a href="/tag/tutorial" class="tag">Tutorial</a>
                <a href="/tag/dom" class="tag">DOM</a>
            </div>
        </footer>
    </article>

    <!-- Sidebar -->
    <aside id="article-sidebar">
        <div class="related-posts">
            <h3>Related Articles</h3>
            <ul>
                <li><a href="/blog/css-selectors-guide">CSS Selectors Guide</a></li>
                <li><a href="/blog/sql-basics">SQL Basics for Web Scraping</a></li>
                <li><a href="/blog/advanced-regex">Advanced Regex Patterns</a></li>
            </ul>
        </div>
    </aside>
</body>
</html>"""
    }

    /**
     * A directory/links page with various link patterns (relative, absolute, external,
     * mailto, tel, js) for testing link extraction UDFs.
     */
    @GetMapping("udf-test/link-directory.html", produces = ["text/html"])
    fun udfLinkDirectory(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Link Directory - UDF Test</title>
</head>
<body>
    <h1>Link Directory</h1>
    <p>This page contains various types of links for testing UDF link extraction functions.</p>

    <section id="internal-links">
        <h2>Internal Links</h2>
        <nav>
            <ul>
                <li><a href="/">Home</a></li>
                <li><a href="/about">About</a></li>
                <li><a href="/products">Products</a></li>
                <li><a href="/services">Services</a></li>
                <li><a href="/contact">Contact</a></li>
            </ul>
        </nav>
    </section>

    <section id="category-links">
        <h2>Category Links</h2>
        <ul class="categories">
            <li><a href="/category/electronics" class="category-link" data-cat-id="1">Electronics</a></li>
            <li><a href="/category/books" class="category-link" data-cat-id="2">Books</a></li>
            <li><a href="/category/clothing" class="category-link" data-cat-id="3">Clothing</a></li>
            <li><a href="/category/home-garden" class="category-link" data-cat-id="4">Home & Garden</a></li>
            <li><a href="/category/sports" class="category-link" data-cat-id="5">Sports & Outdoors</a></li>
        </ul>
    </section>

    <section id="external-links">
        <h2>External Links</h2>
        <ul class="external-links">
            <li><a href="https://www.example.com" rel="external nofollow">Example.com</a></li>
            <li><a href="https://github.com/platonai/pulsar" rel="external">GitHub Repository</a></li>
            <li><a href="https://stackoverflow.com/questions/tagged/xsql" rel="external">Stack Overflow</a></li>
        </ul>
    </section>

    <section id="special-links">
        <h2>Special Links</h2>
        <ul>
            <li><a href="mailto:test@example.com">Email Us</a></li>
            <li><a href="tel:+1-555-0123">Call Us</a></li>
            <li><a href="javascript:void(0)">JavaScript Link</a></li>
            <li><a href="#" onclick="return false;">Hash Link</a></li>
            <li><a href="/download/brochure.pdf">Download PDF</a></li>
            <li><a href="/download/catalog.zip">Download ZIP</a></li>
        </ul>
    </section>

    <section id="image-links">
        <h2>Image Links</h2>
        <ul class="image-gallery">
            <li>
                <a href="/product/1">
                    <img src="/img/product1.jpg" width="200" height="200" alt="Product 1">
                </a>
            </li>
            <li>
                <a href="/product/2">
                    <img src="/img/product2.jpg" width="200" height="200" alt="Product 2">
                </a>
            </li>
            <li>
                <a href="/product/3">
                    <img src="/img/product3.jpg" width="200" height="200" alt="Product 3">
                </a>
            </li>
        </ul>
    </section>

    <section id="pagination">
        <h2>Pagination</h2>
        <div class="pagination">
            <span class="page current">1</span>
            <a href="/links?page=2" class="page">2</a>
            <a href="/links?page=3" class="page">3</a>
            <a href="/links?page=4" class="page">4</a>
            <a href="/links?page=5" class="page">5</a>
            <a href="/links?page=2" class="page next">Next →</a>
        </div>
    </section>

    <footer>
        <p><a href="/sitemap">Sitemap</a> | <a href="/legal">Legal</a></p>
    </footer>
</body>
</html>"""
    }

    @GetMapping("assets/test-pages/form-page.html", produces = ["text/html"])
    fun formPage(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Form Test Page</title>
</head>
<body>
    <h1>Form Test Page</h1>
    <form id="testForm" action="/submit" method="post">
        <div>
            <label for="username">Username:</label>
            <input type="text" id="username" name="username" data-testid="username-input">
        </div>
        <div>
            <label for="email">Email:</label>
            <input type="email" id="email" name="email" data-testid="email-input">
        </div>
        <div>
            <label for="password">Password:</label>
            <input type="password" id="password" name="password" data-testid="password-input">
        </div>
        <div>
            <input type="checkbox" id="remember" name="remember" data-testid="remember-checkbox">
            <label for="remember">Remember me</label>
        </div>
        <div>
            <input type="checkbox" id="newsletter" name="newsletter" data-testid="newsletter-checkbox">
            <label for="newsletter">Subscribe to newsletter</label>
        </div>
        <div>
            <input type="radio" id="option1" name="option" value="1" data-testid="radio-option1">
            <label for="option1">Option 1</label>
            <input type="radio" id="option2" name="option" value="2" data-testid="radio-option2">
            <label for="option2">Option 2</label>
        </div>
        <div>
            <button type="button" id="clickButton" data-testid="click-button">Click Me</button>
            <button type="submit" id="submitButton" data-testid="submit-button">Submit</button>
        </div>
    </form>
    <div id="result" data-testid="result"></div>
    <div id="attrTest" data-custom="custom-value" title="Test Title" class="test-class" data-testid="attr-test-div">Attributes Test</div>
    <a href="https://example.com" id="testLink" target="_blank" rel="noopener" data-testid="test-link">Test Link</a>
    <script>
        document.getElementById('clickButton').addEventListener('click', function() {
            document.getElementById('result').textContent = 'Button clicked!';
        });
        document.getElementById('testForm').addEventListener('submit', function(e) {
            e.preventDefault();
            document.getElementById('result').textContent = 'Form submitted!';
        });
    </script>
</body>
</html>"""
    }

    @GetMapping("assets/test-pages/error-page.html", produces = ["text/html"])
    fun errorPage(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Error Test Page</title>
</head>
<body>
    <h1>Error Test Page</h1>
    <div id="emptyDiv" data-testid="empty-div"></div>
    <div id="contentDiv" data-testid="content-div"><p>This has content</p></div>
    <div id="hiddenDiv" style="display: none;" data-testid="hidden-div">Hidden content</div>
    <div id="delayedDiv" data-testid="delayed-div"></div>
    <script>
        setTimeout(function() {
            document.getElementById('delayedDiv').textContent = 'Delayed content loaded';
        }, 2000);
    </script>
</body>
</html>"""
    }

    @GetMapping("assets/test-pages/keyboard-test.html", produces = ["text/html"])
    fun keyboardPage(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Keyboard Test Page</title>
</head>
<body>
    <h1>Keyboard Test Page</h1>
    <div>
        <label for="keyInput">Type here:</label>
        <input type="text" id="keyInput" data-testid="key-input" placeholder="Type something...">
    </div>
    <div>
        <label for="focusInput">Focus test:</label>
        <input type="text" id="focusInput" data-testid="focus-input" placeholder="Focus test">
    </div>
    <div id="keyResult" data-testid="key-result"></div>
    <div id="focusResult" data-testid="focus-result"></div>
    <script>
        document.getElementById('keyInput').addEventListener('keypress', function(e) {
            document.getElementById('keyResult').textContent = 'Key pressed: ' + e.key;
        });
        document.getElementById('focusInput').addEventListener('focus', function() {
            document.getElementById('focusResult').textContent = 'Input focused';
        });
        document.getElementById('focusInput').addEventListener('blur', function() {
            document.getElementById('focusResult').textContent = 'Input blurred';
        });
    </script>
</body>
</html>"""
    }
}
