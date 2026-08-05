module.exports = {
    testEnvironment: 'jsdom',
    roots: ['<rootDir>'],
    testMatch: ['**/*.test.js'],
    setupFiles: ['./jest.setup.js'],
    // The source files use global variables — define them here
    // so the test environment knows what to expect.
    transform: {},
    // The browser-side sources are eval'd into the jsdom global scope by
    // test-helper.js, so istanbul cannot attribute executed lines to the
    // source files. List them anyway so `--coverage` surfaces which source
    // scripts have dedicated tests and which do not.
    collectCoverageFrom: ['../../main/resources/js/*.js'],
    coverageDirectory: 'coverage',
};
