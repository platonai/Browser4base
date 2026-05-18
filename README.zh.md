# Browser4-base

[English](README.md) | 简体中文 | [中国镜像](https://gitee.com/platonai_galaxyeye/Browser4)
# 🤖 Browser4

## 🥁 简介
---

💖 **Browser4 - 您的终极 AI-RPA 解决方案！** 💖
[English](README.md) | 简体中文

**Browser4** 是一个**高性能**、**分布式**且**开源**的机器人流程自动化（RPA）框架。
它专为**大规模自动化**而设计，在**浏览器自动化**、**网页内容理解**和**数据提取**方面表现出色。
Browser4 解决了现代网页自动化的挑战，确保即使从最**复杂**和**动态**的网站中也能实现**准确**且**全面**的数据提取。
<!-- TOC -->
**目录**
- [🤖 Browser4](#-browser4)
    - [🌟 简介](#-简介)
        - [✨ 核心能力](#-核心能力)
    - [🎥 演示视频](#-演示视频)
    - [💡 使用示例](#-使用示例)
        - [工作流自动化](#工作流自动化)
        - [LLM + X-SQL](#llm--x-sql)
        - [高速并行处理](#高速并行处理)
        - [自动提取](#自动提取)
    - [📦 模块概览](#-模块概览)
    - [📜 文档](#-文档)
    - [🔧 代理 - 解除网站封锁](#-代理---解除网站封锁)
    - [✨ 特性](#-特性)
    - [🤝 支持与社区](#-支持与社区)
<!-- /TOC -->

## 🌟 简介

## 视频
💖 **Browser4：为你的 AI 打造的闪电般快速、协程安全的浏览器引擎** 💖

YouTube:
[![Watch the video](https://img.youtube.com/vi/rF4wXbFlPXk/0.jpg)](https://www.youtube.com/watch?v=rF4wXbFlPXk)
### ✨ 核心能力

Bilibili:
[https://www.bilibili.com/video/BV1kM2rYrEFC](https://www.bilibili.com/video/BV1kM2rYrEFC)
* 🤖 **浏览器自动化** — 高性能的工作流、导航和数据提取自动化。
* ⚡  **极致性能** — 完全协程安全；支持单机每天 10 万 ~ 20 万次复杂页面访问。
* 🧬 **数据提取** — LLM、ML 和选择器的混合方案，从混乱的页面中提取干净的数据。

## 🚀 开始
## 🎥 演示视频

### 谈论一个网页
🎬 YouTube:
[![观看视频](https://img.youtube.com/vi/rJzXNXH3Gwk/0.jpg)](https://youtu.be/rJzXNXH3Gwk)

```kotlin
val document = session.loadDocument(url)
val response = session.chat("介绍一下这个网页", document)
```
📺 Bilibili:
[https://www.bilibili.com/video/BV1fXUzBFE4L](https://www.bilibili.com/video/BV1fXUzBFE4L)

Example code: [kotlin](/examples/browser4-examples/src/main/kotlin/ai/platon/pulsar/human/manual/llm/ChatAboutPage.kt).
---

### 吩咐浏览器干活
## 💡 使用示例

```kotlin
val prompts = """
移动光标到 id 为 'title' 的元素并点击
滚动到页面中间
滚动到顶部
获取 id 为 'title' 的元素的文本
"""
### 工作流自动化

val eventHandlers = DefaultPageEventHandlers()
eventHandlers.browseEventHandlers.onDocumentActuallyReady.addLast { page, driver ->
    val result = session.instruct(prompts, driver)
}
session.open(url, eventHandlers)
```
底层浏览器自动化和数据提取，提供细粒度控制。

Example code: [kotlin](/examples/browser4-examples/src/main/kotlin/ai/platon/pulsar/human/manual/llm/TalkToActivePage.kt).

### 一行代码抓取
**特性：**
- 同时支持实时 DOM 访问和离线快照解析
- 直接且完整的 Chrome DevTools Protocol (CDP) 控制，协程安全
- 精确的元素交互（点击、滚动、输入）
- 使用 CSS 选择器/XPath 快速提取数据

```kotlin
session.scrapeOutPages(
  "https://www.amazon.com/",  "-outLink a[href~=/dp/]", listOf("#title", "#acrCustomerReviewText"))
val session = AgenticContexts.getOrCreateSession()
val agent = session.companionAgent
val driver = session.getOrCreateBoundDriver()

// 加载输入 URL 所引用的初始页面
var page = session.open(url)

// 使用自然语言指令驱动浏览器
agent.act("滚动到评论区")
// 直接从实时 DOM 中读取第一个匹配的评论节点
val content = driver.selectFirstTextOrNull("#comments")

// 将页面快照保存到内存文档中以供离线解析
var document = session.parse(page)
// 一次性将 CSS 选择器映射到结构化字段
var fields = session.extract(document, mapOf("title" to "#title"))

// 让伴随代理执行多步骤导航/搜索流程
val history = agent.run(
    "前往 amazon.com，搜索 'smart phone'，打开评分最高的商品页面"
)

// 将更新后的浏览器状态捕获回 PageSnapshot
page = session.capture(driver)
document = session.parse(page)
// 从捕获的快照中提取额外的属性
fields = session.extract(document, mapOf("ratings" to "#ratings"))
```

### 结合机器人流程自动化 (RPA) 进行网页抓取
### LLM + X-SQL

非常适合高复杂度的数据提取流水线，涉及数十个实体、每个实体数百个字段。

**优势：**
- 相比传统方法，能够提取 10 倍以上的实体和 100 倍以上的字段
- 将 LLM 智能与精确的 CSS 选择器/XPath 相结合
- 类 SQL 语法，熟悉的查询方式

```kotlin
val options = session.options(args)
val event = options.eventHandlers.browseEventHandlers
event.onBrowserLaunched.addLast { page, driver ->
    // warp up the browser to avoid being blocked by the website,
    // or choose the global settings, such as your location.
    warnUpBrowser(page, driver)
}
event.onWillFetch.addLast { page, driver ->
    // have to visit a referrer page before we can visit the desired page
    waitForReferrer(page, driver)
    // websites may prevent us from opening too many pages at a time, so we should open links one by one.
    waitForPreviousPage(page, driver)
}
event.onWillCheckDocumentState.addLast { page, driver ->
    // wait for a special fields to appear on the page
    driver.waitForSelector("body h1[itemprop=name]")
    // close the mask layer, it might be promotions, ads, or something else.
    driver.click(".mask-layer-close-button")
}
// visit the URL and trigger events
session.load(url, options)
```

Example code: [kotlin](/examples/browser4-examples/src/main/kotlin/ai/platon/pulsar/human/manual/sites/food/dianping/RestaurantCrawler.kt).

### 使用 X-SQL 解决*超级复杂*的数据提取问题

```sql
val context = AgenticContexts.create()
val sql = """
select
    dom_first_text(dom, '#productTitle') as title,
    dom_first_text(dom, '#bylineInfo') as brand,
    dom_first_text(dom, '#price tr td:matches(^Price) ~ td, #corePrice_desktop tr td:matches(^Price) ~ td') as price,
    dom_first_text(dom, '#acrCustomerReviewText') as ratings,
    str_first_float(dom_first_text(dom, '#reviewsMedley .AverageCustomerReviews span:contains(out of)'), 0.0) as score
from load_and_select('https://www.amazon.com/dp/B0C1H26C46  -i 1s -njr 3', 'body');
  llm_extract(dom, 'product name, price, ratings') as llm_extracted_data,
  dom_first_text(dom, '#productTitle') as title,
  dom_first_text(dom, '#bylineInfo') as brand,
  dom_first_text(dom, '#price tr td:matches(^Price) ~ td, #corePrice_desktop tr td:matches(^Price) ~ td') as price,
  dom_first_text(dom, '#acrCustomerReviewText') as ratings,
  str_first_float(dom_first_text(dom, '#reviewsMedley .AverageCustomerReviews span:contains(out of)'), 0.0) as score
from load_and_select('https://www.amazon.com/dp/B08PP5MSVB -i 1s -njr 3', 'body');
"""
val rs = context.executeQuery(sql)
println(ResultSetFormatter(rs, withHeader = true))
```

Example code:
示例代码：

* [X-SQL to scrape 100+ fields from an Amazon's product page](https://github.com/platonai/exotic-amazon/tree/main/src/main/resources/sites/amazon/crawl/parse/sql/crawl)
* [X-SQLs to scrape all types of Amazon webpages](https://github.com/platonai/exotic-amazon/tree/main/src/main/resources/sites/amazon/crawl/parse/sql/crawl)
* [使用 X-SQL 从亚马逊商品页面抓取 100+ 字段](https://github.com/platonai/exotic-amazon/tree/main/src/main/resources/sites/amazon/crawl/parse/sql/crawl)
* [使用 X-SQL 抓取所有类型的亚马逊网页](https://github.com/platonai/exotic-amazon/tree/main/src/main/resources/sites/amazon/crawl/parse/sql/crawl)

### 高速并行处理

通过并行浏览器控制和智能资源优化实现极致吞吐量。

### 连续采集
**性能：**
- 单机每天 1 万 ~ 2 万次复杂页面访问
- 并发会话管理
- 资源拦截以加快页面加载

```kotlin
fun main() {
    val context = PulsarContexts.create()

    val parseHandler = { _: WebPage, document: FeaturedDocument ->
        // use the document
        // ...
        // and then extract further hyperlinks
        context.submitAll(document.selectHyperlinks("a[href~=/dp/]"))
    }
    val urls = LinkExtractors.fromResource("seeds10.txt")
        .map { ParsableHyperlink("$it -refresh", parseHandler) }
    context.submitAll(urls).await()
}
val args = "-refresh -dropContent -interactLevel fastest"
val blockingUrls = listOf("*.png", "*.jpg")
val links = LinkExtractors.fromResource("urls.txt")
    .map { ListenableHyperlink(it, "", args = args) }
    .onEach {
        it.eventHandlers.browseEventHandlers.onWillNavigate.addLast { page, driver ->
            driver.addBlockedURLs(blockingUrls)
        }
    }

session.submitAll(links)
```

Example code: [kotlin](/examples/browser4-examples/src/main/kotlin/ai/platon/pulsar/human/manual/_5_ContinuousCrawler.kt), [java](/examples/browser4-examples/src/main/java/ai/platon/pulsar/human/manual/ContinuousCrawler.java).
🎬 YouTube:
[![观看视频](https://img.youtube.com/vi/_BcryqWzVMI/0.jpg)](https://www.youtube.com/watch?v=_BcryqWzVMI)

📺 Bilibili:
[https://www.bilibili.com/video/BV1kM2rYrEFC](https://www.bilibili.com/video/BV1kM2rYrEFC)


---

### 自动提取

基于自监督/无监督机器学习的自动、大规模、高精度字段发现与提取 — 无需 LLM API 调用，零 Token，确定性且快速。

**它能做什么：**
- 高精度学习商品/详情页上的每一个可提取字段（通常数十到数百个）。
- 当 Browser4 在 GitHub 上达到 10K stars 时开源。

**为什么不仅仅使用 LLM？**
- LLM 提取会增加延迟、成本和 Token 限制。
- 基于 ML 的自动提取是本地化、可复现的，并可扩展到每天 10 万 ~ 20 万页面。
- 你也可以将两者结合：使用自动提取获取结构化基线数据 + LLM 进行语义增强。

**快速命令（PulsarRPAPro）：**
```bash
# 注意：需要 MongoDB
curl -L -o PulsarRPAPro.jar https://github.com/platonai/PulsarRPAPro/releases/download/v3.0.0/PulsarRPAPro.jar
```

**集成状态：**
- 当前可通过配套项目 [PulsarRPAPro](https://github.com/platonai/PulsarRPAPro) 使用。
- 原生的 Browser4 API 接入正在规划中；请关注发布动态。

**核心优势：**
- 高精度：>95% 的字段被发现；其中大多数准确率 >99%（在测试域名上的参考值）。
- 对选择器变化和 HTML 噪声具有韧性。
- 零外部依赖（无需 API Key）→ 大规模使用时成本效益高。
- 可解释：生成的选择器和 SQL 透明且可审计。

👽 使用机器学习代理提取数据：

![自动提取结果快照](docs/assets/images/amazon.png)

（即将推出：更丰富的仓库内示例和直接的 API 接口。）

# 🚄 核心功能
---

- 网络爬虫：可扩展的爬取能力，支持浏览器渲染、AJAX数据提取等功能。
---

- LLM集成：使用自然语言分析和描述网页内容。
## ✨ 特性

- 文生行为：通过简单直观的语言指令控制浏览器操作。
  状态：[可用] 已包含在仓库中，[实验性] 正在积极迭代中，[计划中] 尚未加入仓库，[参考] 性能目标。

- RPA（机器人流程自动化）：模拟人类行为，自动化处理任务，包括单页应用（SPA）爬取及其他高价值工作流。
### 浏览器自动化与 RPA
- [可用] 基于工作流的浏览器操作
- [可用] 精确的协程安全控制（滚动、点击、提取）
- [可用] 灵活的事件处理器和生命周期管理

- 简易API：用一行代码提取数据，或用一条SQL语句将网页转换为结构化表格。
### 数据提取与查询
- [可用] 一行命令完成数据提取
- [可用] 面向 DOM/内容的 X-SQL 扩展查询语言
- [实验性] 结构化与非结构化混合提取（LLM & ML & 选择器）

- X-SQL：扩展SQL功能，用于管理网络数据——爬取、抓取、内容挖掘和基于网页的商业智能分析。
### 性能与可扩展性
- [可用] 高效的并行页面渲染
- [可用] 反封锁设计和智能重试
- [参考] 在普通硬件上每天处理 10 万+ 复杂页面

- 爬虫隐身：高级反检测技术，包括Web驱动隐身、IP轮换和隐私上下文轮换，避免被封锁。
### 隐匿与可靠性
- [实验性] 高级反机器人技术
- [可用] 通过 `PROXY_ROTATION_URL` 进行代理轮换
- [可用] 弹性调度与质量保证

- 高性能：高度优化，单机可并行渲染数百个页面且不被屏蔽。
### 开发者体验
- [可用] 简洁的 API 集成（REST、原生、文本命令）
- [可用] 丰富的配置分层
- [可用] 清晰的结构化日志和指标

- 低成本：每天爬取10万+个浏览器渲染的电商页面或处理数千万数据点，仅需8核CPU/32GB内存。
### 存储与监控
- [可用] 支持本地文件系统和 MongoDB（可扩展）
- [可用] 全面的日志与透明度

- 数据量保障：智能重试机制、精准调度和全面的网页数据生命周期管理。
---

- 大规模支持：完全分布式架构，专为大规模网页爬取设计。
## 🤝 支持与社区

- 大数据支持：支持多种后端存储，包括本地文件、MongoDB、HBase和Gora。
  加入我们的社区，获取支持、反馈和合作！

- 日志与指标：全面监控和详细事件记录，确保完全透明。
- **GitHub Discussions**：与开发者和其他用户交流。
- **Issue Tracker**：报告 Bug 或请求新功能。
- **社交媒体**：关注我们获取最新动态。

- 自动提取：基于AI的模式识别，自动精准提取网页中的所有字段。
  欢迎贡献！详情请参阅 [CONTRIBUTING.md](CONTRIBUTING.md)。

# 🧮 通过可执行 jar 使用 Browser4
---

我们发布了一个基于 Browser4 的独立可执行 jar，它包括：
## 📜 文档

- 顶尖站点的数据采集示例。
- 基于自监督机器学习自动进行信息提取的小程序，AI 算法可以识别详情页的所有字段，字段精确度达到 99% 以上。
- 基于自监督机器学习自动学习并输出所有采集规则的小程序。
- 可以直接从命令行执行网页数据采集任务，无需编写代码。
- 升级的 Browser4 服务器，可以向服务器发送 SQL 语句来采集 Web 数据。
- 一个 Web UI，可以编写 SQL 语句并通过它发送到服务器。
  完整文档可在 `docs/` 目录和我们的 [GitHub Pages 站点](https://platonai.github.io/browser4/) 中查阅。

下载 [Browser4Pro](https://github.com/platonai/Browser4Pro#download) 并使用以下命令行探索其能力：
---

## 🔧 代理配置 - 解除网站封锁

<details>

设置环境变量 `PROXY_ROTATION_URL` 为你的代理服务商提供的轮换 URL：

```shell
java -jar Browser4Pro.jar
```

# 🎁 将 Browser4 用作软件库

要利用 Browser4 的强大功能，最简单的方法是将其作为库添加到您的项目中。

使用 Maven 时，可以在 `pom.xml` 文件中添加以下依赖：

```xml
<dependency>
    <groupId>ai.platon.pulsar</groupId>
    <artifactId>pulsar-bom</artifactId>
    <version>VERSION</version>
</dependency>
```

使用 Gradle 时，可以在 `build.gradle` 文件中添加以下依赖：

```kotlin
implementation("ai.platon.pulsar:pulsar-bom:VERSION")
```

也可以从 Github 克隆模板项目，包括 [kotlin](https://github.com/platonai/pulsar-kotlin-template),
[java-17](https://github.com/platonai/pulsar-java-17-template)。

您还可以基于我们的商业级开源项目启动自己的大规模网络爬虫项目: [Browser4Pro](https://github.com/platonai/Browser4Pro), [Exotic-amazon](https://github.com/platonai/exotic-amazon)。

点击 [基本用法](docs/get-started/2basic-usage.md) 查看详情。

# 🌐 将 Browser4 作为 REST 服务运行

当 Browser4 作为 REST 服务运行时，X-SQL 可用于随时随地抓取网页或直接查询 Web 数据，无需打开 IDE。

## 从源代码构建

```shell
git clone https://github.com/platonai/Browser4.git
cd Browser4 && bin/build-run.sh
export PROXY_ROTATION_URL=https://your-proxy-provider.com/rotation-endpoint
```

对于国内开发者，我们强烈建议您按照 [这个](https://github.com/platonai/pulsar/blob/master/bin/tools/maven/maven-settings.md) 指导来加速构建。
每次访问此轮换 URL 时，应返回一个或多个新的代理 IP。
如果你需要此类 URL，请联系你的代理服务商。

## 使用 X-SQL 查询 Web
</details>

如果未启动，则启动 pulsar 服务器：
---

```shell
bin/pulsar
```
## 许可证

在另一个终端窗口中抓取网页：

```shell
bin/scrape.sh
```

该 bash 脚本非常简单，只需使用 curl 发送 X-SQL：

```shell
curl -X POST --location "http://localhost:8182/api/x/e" -H "Content-Type: text/plain" -d "
  select
      dom_base_uri(dom) as url,
      dom_first_text(dom, '#productTitle') as title,
      dom_first_slim_html(dom, 'img:expr(width > 400)') as img
  from load_and_select('https://www.amazon.com/dp/B0C1H26C46', 'body');
"
```

示例代码: [bash](bin/scrape.sh), [PowerShell](bin/scrape.ps1), [batch](bin/scrape.bat), [java](/pulsar-client/src/main/java/ai/platon/pulsar/client/Scraper.java), [kotlin](/pulsar-client/src/main/kotlin/ai/platon/pulsar/client/Scraper.kt), [php](/pulsar-client/src/main/php/Scraper.php).

点击 [X-SQL](docs/x-sql.md) 查看有关X-SQL的详细介绍和功能描述。






# 📖 循序渐进的课程

我们提供了一个循序渐进的示例课程，帮助您逐步了解和掌握 Browser4 的使用：

1. [Home](docs/zh/get-started/1home.md)
2. [Basic Usage](docs/zh/get-started/2basic-usage.md)
3. [Load Options](docs/zh/get-started/3load-options.md)
4. [Data Extraction](docs/zh/get-started/4data-extraction.md)
5. [URL](docs/zh/get-started/5URL.md)
6. [Java-style Async](docs/zh/get-started/6Java-style-async.md)
7. [Kotlin-style Async](docs/zh/get-started/7Kotlin-style-async.md)
8. [Continuous Crawling](docs/zh/get-started/8continuous-crawling.md)
9. [Event Handling](docs/zh/get-started/9event-handling.md)
10. [RPA](docs/zh/get-started/10RPA.md)
11. [WebDriver](docs/zh/get-started/11WebDriver.md)
12. [Massive Crawling](docs/zh/get-started/12massive-crawling.md)
13. [X-SQL](docs/zh/get-started/13X-SQL.md)
14. [AI Extraction](docs/zh/get-started/14AI-extraction.md)
15. [REST](docs/zh/get-started/15REST.md)
16. [Console](docs/zh/get-started/16console.md)
17. [Top Practice](docs/zh/get-started/17top-practice.md)
18. [Miscellaneous](docs/zh/get-started/18miscellaneous.md)

# 📊 日志和指标

Browser4 精心设计了日志和指标子系统，以记录系统中发生的每一个事件。通过 Browser4 的日志系统，您可以轻松地了解系统中发生的每一件事情，
判断系统运行是否健康，以及成功获取了多少页面、重试了多少页面、使用了多少代理 IP 等信息。

通过观察几个简单的符号，您可以快速了解整个系统的状态：💯 💔 🗙 ⚡ 💿 🔃 🤺。以下是一组典型的页面加载日志。要了解如何阅读日志，
请查看 [日志格式](docs/log-format.md)，以便快速掌握整个系统的状态。

```text
2022-09-24 11:46:26.045  INFO [-worker-14] a.p.p.c.c.L.Task - 3313. 💯 ⚡ U for N got 200 580.92 KiB in 1m14.277s, fc:1 | 75/284/96/277/6554 | 106.32.12.75 | 3xBpaR2 | https://www.walmart.com/ip/Restored-iPhone-7-32GB-Black-T-Mobile-Refurbished/329207863 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:46:09.190  INFO [-worker-32] a.p.p.c.c.L.Task - 3738. 💯 💿 U  got 200 452.91 KiB in 55.286s, last fetched 9h32m50s ago, fc:1 | 49/171/82/238/6172 | 121.205.2.0.6 | https://www.walmart.com/ip/Boost-Mobile-Apple-iPhone-SE-2-Cell-Phone-Black-64GB-Prepaid-Smartphone/490934488 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:46:28.567  INFO [-worker-17] a.p.p.c.c.L.Task - 2269. 💯 🔃 U for SC got 200 565.07 KiB <- 543.41 KiB in 1m22.767s, last fetched 16m58s ago, fc:6 | 58/230/98/295/6272 | 27.158.125.76 | 9uwu602 | https://www.walmart.com/ip/Straight-Talk-Apple-iPhone-11-64GB-Purple-Prepaid-Smartphone/356345388?variantFieldId=actual_color -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:47:18.390  INFO [r-worker-8] a.p.p.c.c.L.Task - 3732. 💔 ⚡ U for N got 1601 0 <- 0 in 32.201s, fc:1/1 Retry(1601) rsp: CRAWL, rrs: EMPTY_0B | 2zYxg52 | https://www.walmart.com/ip/Apple-iPhone-7-256GB-Jet-Black-AT-T-Locked-Smartphone-Grade-B-Used/182353175?variantFieldId=actual_color -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
2022-09-24 11:47:13.860  INFO [-worker-60] a.p.p.c.c.L.Task - 2828. 🗙 🗙 U for SC got 200 0 <- 348.31 KiB <- 684.75 KiB in 0s, last fetched 18m55s ago, fc:2 | 34/130/52/181/5747 | 60.184.124.232 | 11zTa0r2 | https://www.walmart.com/ip/Walmart-Family-Mobile-Apple-iPhone-11-64GB-Black-Prepaid-Smartphone/209201965?athbdg=L1200 -expires PT24H -ignoreFailure -itemExpires PT1M -outLinkSelector a[href~=/ip/] -parse -requireSize 300000
```

# 💻 系统要求

- 内存 4G+
- Maven 3.2+
- Java 11 JDK 最新版本
- java 和 jar 必须在 PATH 中
- Google Chrome 90+

Browser4 已在 Ubuntu 18.04、Ubuntu 20.04、Windows 7、Windows 11、WSL 上进行了测试，任何其他满足要求的操作系统也应该可以正常工作。

# 🛸 高级主题

如果您对 Browser4 的高级主题感兴趣，可以查看 [advanced topics](/docs/faq/advanced-topics.md) 以获取以下问题的答案：

- 大规模网络爬虫有什么困难？
- 如何每天从电子商务网站上抓取一百万个产品页面？
- 如何在登录后抓取页面？
- 如何在浏览器上下文中直接下载资源？
- 如何抓取单页应用程序（SPA）？
- 资源模式
- RPA 模式
- 如何确保正确提取所有字段？
- 如何抓取分页链接？
- 如何抓取新发现的链接？
- 如何爬取整个网站？
- 如何模拟人类行为？
- 如何安排优先任务？
- 如何在固定时间点开始任务？
- 如何删除计划任务？
- 如何知道任务的状态？
- 如何知道系统中发生了什么？
- 如何为要抓取的字段自动生成 css 选择器？
- 如何使用机器学习自动从网站中提取内容并具有商业准确性？
- 如何抓取 amazon.com 以满足行业需求？

# 🆚 同其他方案的对比

Browser4 在 “主要特性” 部分中提到的特性都得到了良好的支持，而其他解决方案可能不支持或者支持不好。您可以点击 [solution comparison](docs/faq/solution-comparison.md) 查看以下问题的答案：

- Browser4 vs selenium/puppeteer/playwright
- Browser4 vs nutch
- Browser4 vs scrapy+splash

# 🤓 技术细节

如果您对 Browser4 的技术细节感兴趣，可以查看 [technical details](docs/faq/technical-details.md) 以获取以下问题的答案：

- 如何轮换我的 IP 地址？
- 如何隐藏我的机器人不被检测到？
- 如何以及为什么要模拟人类行为？
- 如何在一台机器上渲染尽可能多的页面而不被屏蔽？

# 🐦 联系方式

- 微信：galaxyeye
- 微博：[galaxyeye](https://weibo.com/galaxyeye)
- 邮箱：galaxyeye@live.cn, ivincent.zhang@gmail.com
- Twitter: galaxyeye8
- 网站：[platon.ai](http://platon.ai)
  Apache 2.0 License。详情请参阅 [LICENSE](LICENSE)。



