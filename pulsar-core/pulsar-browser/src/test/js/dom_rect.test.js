/**
 * Tests for dom_rect.js — Rectangle formatting utilities.
 */
const { loadScript, ensureConfig, setupDOM } = require('./test-helper');

beforeAll(() => {
    loadScript('configs.js');
    loadScript('tree_walker.js');
    loadScript('node_ops.js');
    loadScript('node_ext_data.js');
    loadScript('feature_calculator.js');
    loadScript('__pulsar_utils__.js');
    loadScript('dom_rect.js');
    loadScript('dom_style.js');
    loadScript('dom_text.js');
    ensureConfig();
});

beforeEach(() => {
    setupDOM();
});

/**
 * Give an element a deterministic bounding rect (jsdom has no layout engine).
 */
function stubRect(el, left, top, width, height) {
    el.getBoundingClientRect = () => new DOMRect(left, top, width, height);
}

/**
 * jsdom keeps window.scrollX/scrollY at 0; make them readable as `x`/`y` for
 * the duration of `fn`, then restore the original property descriptors.
 */
function withScroll(x, y, fn) {
    const scrollXDescriptor = Object.getOwnPropertyDescriptor(window, 'scrollX');
    const scrollYDescriptor = Object.getOwnPropertyDescriptor(window, 'scrollY');

    Object.defineProperty(window, 'scrollX', {configurable: true, value: x});
    Object.defineProperty(window, 'scrollY', {configurable: true, value: y});

    try {
        return fn();
    } finally {
        restoreProperty('scrollX', scrollXDescriptor);
        restoreProperty('scrollY', scrollYDescriptor);
    }
}

function restoreProperty(name, descriptor) {
    if (descriptor) {
        Object.defineProperty(window, name, descriptor);
    } else {
        delete window[name];
    }
}

describe('formatRect', () => {
    test('formats coordinates as space-separated integers (default)', () => {
        const result = __pulsar_utils__.formatRect(10.56, 20.34, 100.78, 200.12);
        // left=Math.round(20.34)=20, top=Math.round(10.56)=11,
        // width=Math.round(100.78)=101, height=Math.round(200.12)=200
        expect(result).toBe('20 11 101 200');
    });

    test('formats coordinates as compact base-36 integers when VI_COMPRESSION=base36', () => {
        var config = window.__pulsar_CONFIGS || __pulsar_DEFAULT_CONFIGS;
        var saved = config.VI_COMPRESSION;
        config.VI_COMPRESSION = 'base36';
        try {
            const result = __pulsar_utils__.formatRect(10.56, 20.34, 100.78, 200.12);
            // left=20→"k", top=11→"b", width=101→"2t", height=200→"5k"
            expect(result).toBe('k,b,2t,5k');
        } finally {
            config.VI_COMPRESSION = saved;
        }
    });

    test('returns false for zero dimensions', () => {
        const result = __pulsar_utils__.formatRect(10, 20, 0, 0);
        expect(result).toBe(false);
    });
});

describe('formatDOMRect', () => {
    test('formats DOMRect as space-separated integers (default)', () => {
        const rect = new DOMRect(10.56, 20.34, 100.78, 200.12);
        const result = __pulsar_utils__.formatDOMRect(rect);
        // left=11, top=20, width=101, height=200
        expect(result).toBe('11 20 101 200');
    });

    test('formats DOMRect as compact base-36 integers when VI_COMPRESSION=base36', () => {
        var config = window.__pulsar_CONFIGS || __pulsar_DEFAULT_CONFIGS;
        var saved = config.VI_COMPRESSION;
        config.VI_COMPRESSION = 'base36';
        try {
            const rect = new DOMRect(10.56, 20.34, 100.78, 200.12);
            const result = __pulsar_utils__.formatDOMRect(rect);
            // left=11→"b", top=20→"k", width=101→"2t", height=200→"5k"
            expect(result).toBe('b,k,2t,5k');
        } finally {
            config.VI_COMPRESSION = saved;
        }
    });

    test('returns false for null rect', () => {
        expect(__pulsar_utils__.formatDOMRect(null)).toBe(false);
    });

    test('returns false for zero-dimension rect', () => {
        const rect = new DOMRect(0, 0, 0, 0);
        expect(__pulsar_utils__.formatDOMRect(rect)).toBe(false);
    });
});

describe('formatDOMRectList', () => {
    test('returns JSON-like array for empty list', () => {
        document.body.innerHTML = '<div></div>';
        const div = document.body.querySelector('div');
        // getClientRects on an empty div returns an empty DOMRectList
        const rects = div.getClientRects();
        const result = __pulsar_utils__.formatDOMRectList(rects);
        expect(result).toContain('[');
        expect(result).toContain(']');
    });
});

describe('getClientRect', () => {
    test('returns false or DOMRect for element node (jsdom has no layout)', () => {
        const div = document.body.querySelector('div');
        const rect = __pulsar_utils__.getClientRect(div);
        // In jsdom, elements have 0×0 bounds, so getElementClientRect returns false.
        // In a real browser, this would be a DOMRect.
        expect(rect === false || rect instanceof DOMRect).toBe(true);
    });

    test('returns null for unsupported node types', () => {
        const comment = document.createComment('test');
        document.body.appendChild(comment);
        expect(__pulsar_utils__.getClientRect(comment)).toBeNull();
        document.body.removeChild(comment);
    });
});

describe('getElementClientRect', () => {
    test('returns false or DOMRect for element (jsdom has no layout)', () => {
        const div = document.body.querySelector('div');
        const rect = __pulsar_utils__.getElementClientRect(div);
        expect(rect === false || rect instanceof DOMRect).toBe(true);
    });

    test('returns the viewport rect as-is, independent of the scroll position', () => {
        const div = document.body.querySelector('div');
        stubRect(div, 10, 20, 100, 40);

        withScroll(5, 120, () => {
            const rect = __pulsar_utils__.getElementClientRect(div);
            expect(rect.left).toBe(10);
            expect(rect.top).toBe(20);
            expect(rect.width).toBe(100);
            expect(rect.height).toBe(40);
        });
    });
});

describe('getDocumentRect', () => {
    test('adds the scroll offsets to the client rect', () => {
        const div = document.body.querySelector('div');
        stubRect(div, 10, 20, 100, 40);

        withScroll(5, 120, () => {
            const rect = __pulsar_utils__.getDocumentRect(div);
            expect(rect.left).toBe(15);
            expect(rect.top).toBe(140);
            expect(rect.width).toBe(100);
            expect(rect.height).toBe(40);
        });
    });

    test('equals the client rect at scroll position 0', () => {
        const div = document.body.querySelector('div');
        stubRect(div, 10, 20, 100, 40);

        const rect = __pulsar_utils__.getDocumentRect(div);
        expect(rect.left).toBe(10);
        expect(rect.top).toBe(20);
    });

    test('passes false through for zero-size elements', () => {
        const div = document.body.querySelector('div');
        stubRect(div, 10, 20, 0, 0);

        expect(__pulsar_utils__.getDocumentRect(div)).toBe(false);
    });

    test('passes null through for unsupported nodes', () => {
        const comment = document.createComment('test');
        document.body.appendChild(comment);
        expect(__pulsar_utils__.getDocumentRect(comment)).toBeNull();
        document.body.removeChild(comment);
    });
});

describe('NodeOps.getRect', () => {
    test('returns document coordinates for the best element', () => {
        const span = document.body.querySelector('span');
        stubRect(span, 7, 11, 30, 12);

        withScroll(0, 200, () => {
            const rect = NodeOps.getRect(span);
            expect(rect.left).toBe(7);
            expect(rect.top).toBe(211);
        });
    });
});

describe('queryClientRect', () => {
    test('stays in viewport space (the name is not a lie)', () => {
        document.body.innerHTML = '<div id="query-target"></div>';
        const div = document.getElementById('query-target');
        stubRect(div, 10, 20, 100, 40);

        withScroll(0, 999, () => {
            expect(__pulsar_utils__.queryClientRect('#query-target')).toBe('10 20 100 40');
        });
    });
});

describe('compute', () => {
    let originalGetTextWidth;

    beforeEach(() => {
        // jsdom has no canvas: stub the text measurement used by the feature
        // calculator so text nodes (and therefore lz/tp) can be exercised.
        originalGetTextWidth = __pulsar_utils__.getTextWidth;
        __pulsar_utils__.getTextWidth = () => 10;

        // compute() is once per document; reset the runtime flags between tests.
        delete __pulsar_utils__._computeDone;
        delete __pulsar_utils__._viDataMap;
        delete __pulsar_utils__._viDataComputed;
        delete __pulsar_utils__._captureDataJson;
        delete __pulsar_utils__._captureMeta;
        delete __pulsar_utils__._computedLayoutVersion;
        delete __pulsar_utils__._layoutMutationCount;
        if (__pulsar_utils__._layoutObserver) {
            __pulsar_utils__._layoutObserver.disconnect();
            __pulsar_utils__._layoutObserver = null;
        }
    });

    afterEach(() => {
        __pulsar_utils__.getTextWidth = originalGetTextWidth;
    });

    test('does not scroll the page and does not stop resource loading', () => {
        // Avoid text nodes so the canvas-based text measurement is not exercised
        // (jsdom has no canvas implementation).
        document.body.innerHTML = '<div><img src="x.png" alt=""></div>';

        const scrollSpy = jest.spyOn(window, 'scrollTo').mockImplementation(() => {});
        const stopSpy = typeof window.stop === 'function'
            ? jest.spyOn(window, 'stop').mockImplementation(() => {})
            : null;

        try {
            __pulsar_utils__.compute();

            expect(scrollSpy).not.toHaveBeenCalled();
            if (stopSpy) {
                expect(stopSpy).not.toHaveBeenCalled();
            }
            expect(__pulsar_utils__._viDataComputed).toBe(true);
        } finally {
            scrollSpy.mockRestore();
            if (stopSpy) stopSpy.mockRestore();
        }
    });

    test('never writes the annotation into the live DOM', async () => {
        document.body.innerHTML = '<p><span>3412.25</span></p>';

        const records = [];
        const observer = new MutationObserver((mutations) => records.push(...mutations));
        observer.observe(document.body, {
            subtree: true, childList: true, attributes: true, characterData: true
        });

        try {
            __pulsar_utils__.compute();
            await new Promise((resolve) => setTimeout(resolve, 0));
        } finally {
            observer.disconnect();
        }

        expect(document.getElementById('PulsarScriptSection')).toBeNull();
        expect(document.getElementById('PulsarMetaInformation')).toBeNull();
        expect(document.body.hasAttribute('data-error')).toBe(false);
        expect(document.querySelectorAll('[lz]')).toHaveLength(0);
        expect(document.querySelectorAll('[tp]')).toHaveLength(0);
        expect(records).toHaveLength(0);
    });

    test('serializes the annotations that are absent from the live DOM', () => {
        document.body.innerHTML = '<p><span>3412.25</span></p>';

        __pulsar_utils__.compute();
        const html = __pulsar_utils__.serializeAnnotatedHTML(document.documentElement);

        // The captured HTML keeps everything its consumers expect ...
        expect(html).toContain('id="PulsarScriptSection"');
        expect(html).toContain('__pulsar__Data');
        expect(html).toContain('id="PulsarMetaInformation"');
        expect(html).toContain('view-port=');
        // The serialized document states the coordinate space of its `vi`
        // boxes, so an offline consumer of `box=` does not have to guess.
        expect(html).toContain('box-space="document"');
        expect(html).toContain('data-error="0"');
        expect(html).toContain('lz="1"');
        // `tp` stays runtime-internal: it was removed from the DOM before
        // serialization even before this change, and is not emitted now.
        expect(html).not.toContain('tp="');

        // ... while the live document still has none of it.
        expect(document.getElementById('PulsarScriptSection')).toBeNull();
        expect(document.getElementById('PulsarMetaInformation')).toBeNull();
        expect(document.body.hasAttribute('data-error')).toBe(false);
        expect(document.querySelectorAll('[lz], [tp]')).toHaveLength(0);
    });

    test('recomputes only when the layout changed', async () => {
        // M14: compute() used to be once per document, so an SPA/lazy-loaded or
        // re-laid-out page kept returning the first layout's boxes forever. The
        // layout fingerprint must make the second capture of a settled page a
        // no-op while a real layout change triggers a fresh traversal.
        document.body.innerHTML = '<div id="layout-target"><img src="x.png" alt=""></div>';
        const target = document.getElementById('layout-target');
        stubRect(target, 0, 100, 100, 40);

        const writeSpy = jest.spyOn(__pulsar_utils__, 'writeData');
        try {
            __pulsar_utils__.compute();
            expect(writeSpy).toHaveBeenCalledTimes(1);
            expect(__pulsar_utils__.isLayoutStale()).toBe(false);

            // A settled page: nothing changed, so the stored vi is reused.
            __pulsar_utils__.compute();
            expect(writeSpy).toHaveBeenCalledTimes(1);
            expect(__pulsar_utils__.isLayoutStale()).toBe(false);

            // The page re-lays-out: a block is inserted above the target.
            const spacer = document.createElement('div');
            spacer.innerHTML = '<img src="spacer.png" alt="">';
            document.body.insertBefore(spacer, target);
            // MutationObserver callbacks are delivered in a microtask.
            await new Promise((resolve) => setTimeout(resolve, 0));

            expect(__pulsar_utils__.isLayoutStale()).toBe(true);
            __pulsar_utils__.compute();
            expect(writeSpy).toHaveBeenCalledTimes(2);
            expect(__pulsar_utils__.isLayoutStale()).toBe(false);
        } finally {
            writeSpy.mockRestore();
        }
    });

    test('layout fingerprint covers scroll-size changes without a mutation record', () => {
        // A resize/zoom changes the layout without touching the observed subtree;
        // the scroll size / viewport terms keep the check from going blind then.
        document.body.innerHTML = '<div><img src="x.png" alt=""></div>';

        const descriptor = Object.getOwnPropertyDescriptor(window, 'innerWidth');
        Object.defineProperty(window, 'innerWidth', {configurable: true, value: 800});
        try {
            __pulsar_utils__.compute();
            expect(__pulsar_utils__.isLayoutStale()).toBe(false);
            Object.defineProperty(window, 'innerWidth', {configurable: true, value: 1024});
            expect(__pulsar_utils__.isLayoutStale()).toBe(true);
        } finally {
            if (descriptor) {
                Object.defineProperty(window, 'innerWidth', descriptor);
            } else {
                delete window.innerWidth;
            }
        }
    });

    test('compute still works when MutationObserver is unavailable', () => {
        document.body.innerHTML = '<div><img src="x.png" alt=""></div>';

        const descriptor = Object.getOwnPropertyDescriptor(window, 'MutationObserver');
        Object.defineProperty(window, 'MutationObserver', {configurable: true, value: undefined});
        try {
            expect(() => __pulsar_utils__.compute()).not.toThrow();
            expect(__pulsar_utils__._viDataComputed).toBe(true);
            expect(__pulsar_utils__._layoutObserver).toBeNull();
            // Without an observer, freshness still has the size terms.
            expect(__pulsar_utils__.isLayoutStale()).toBe(false);
        } finally {
            if (descriptor) {
                Object.defineProperty(window, 'MutationObserver', descriptor);
            }
        }
    });
});
