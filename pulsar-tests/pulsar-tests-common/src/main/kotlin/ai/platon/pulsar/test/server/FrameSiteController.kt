package ai.platon.pulsar.test.server

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Deterministic frame fixtures for the frame-switching E2E tests
 * (see `FrameSwitchE2ETest` in pulsar-e2e-tests).
 *
 * The pages are served over real HTTP from the same origin (127.0.0.1:17080):
 *
 * - `frame-host.html` embeds three iframes: `payframe` (a payment form with a
 *   nested `innerframe`), `notesframe` (static content) and `xoriginframe`
 *   (a real cross-origin page, https://example.com).
 *
 * All documents carry distinct ids so tests can tell which document a scoped
 * selector resolved in.
 */
@RestController
class FrameSiteController {

    @GetMapping("assets/frames/frame-host.html", produces = ["text/html"])
    fun frameHost(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Frame Host Page</title>
</head>
<body>
    <h1 id="host-title">Frame Host Page</h1>
    <button id="host-button" type="button">host button</button>
    <p id="host-status">initial</p>
    <script>
        document.getElementById('host-button').addEventListener('click', function () {
            document.getElementById('host-status').textContent = 'host button clicked';
        });
    </script>
    <iframe id="pay-frame" name="payframe" src="/assets/frames/pay.html" width="700" height="320"></iframe>
    <iframe id="notes-frame" name="notesframe" src="/assets/frames/notes.html" width="700" height="160"></iframe>
    <iframe id="xorigin-frame" name="xoriginframe" src="https://example.com/" width="700" height="160"></iframe>
</body>
</html>"""
    }

    @GetMapping("assets/frames/pay.html", produces = ["text/html"])
    fun payFrame(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Pay Frame</title>
</head>
<body>
    <h2 id="pay-title">Payment</h2>
    <input type="text" id="card-number" name="cardNumber" placeholder="Card number">
    <input type="text" id="card-expiry" name="cardExpiry" placeholder="MM/YY">
    <button id="pay-button" type="button">Pay now</button>
    <p id="pay-result">pending</p>
    <script>
        document.getElementById('pay-button').addEventListener('click', function () {
            var card = document.getElementById('card-number').value;
            document.getElementById('pay-result').textContent = 'paid:' + card;
        });
    </script>
    <iframe id="inner-frame" name="innerframe" src="/assets/frames/inner.html" width="300" height="120"></iframe>
</body>
</html>"""
    }

    @GetMapping("assets/frames/notes.html", produces = ["text/html"])
    fun notesFrame(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Notes Frame</title>
</head>
<body>
    <p id="notes-text">notes from notesframe</p>
    <input type="text" id="notes-input" name="note">
</body>
</html>"""
    }

    @GetMapping("assets/frames/inner.html", produces = ["text/html"])
    fun innerFrame(): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Inner Frame</title>
</head>
<body>
    <p id="inner-text">inner frame content</p>
</body>
</html>"""
    }
}
