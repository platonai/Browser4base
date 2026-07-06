#!/bin/bash

use_native=false
for arg in "$@"; do
    if [[ "$arg" == "--native" ]]; then
        use_native=true
        break
    fi
done

if [ "$use_native" = false ]; then
    echo "Launching OpenChrome.kt..."
    # Determine project root relative to this script
    SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
    PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

    # Check if we need to build first, or just run.
    # To avoid running exec:java on parent project, we run in two steps or use -f

    # Method 1: Use -f to point to the specific pom, but that might lose parent context if not careful.
    # Method 2: Use -pl and ensuring we only run exec:java on the leaf.

    # We'll try running exec:java only on the project.
    # If it fails due to missing dependencies, we might need to compile first.
    # But for now, let's assume dependencies are there or we use -am with a phase that includes compilation but not exec for parents?
    # No, -am includes dependencies in the reactor.

    # Correct approach:
    # 1. Compile everything needed
    "$PROJECT_ROOT/mvnw" -pl examples/pulsar-examples -am compile -DskipTests

    # 2. Run the specific project
    "$PROJECT_ROOT/mvnw" -pl examples/pulsar-examples exec:java -Dexec.mainClass="ai.platon.pulsar.tools.OpenChromeKt" -Dexec.classpathScope="test"

    if [ $? -eq 0 ]; then
        exit 0
    fi
    echo "Failed to launch OpenChrome.kt. Falling back to native launcher..."
fi

# Default Chrome locations on Windows (for Git Bash / WSL)
CHROME_PATHS=(
    "/c/Program Files/Google/Chrome/Application/chrome.exe"
    "/c/Program Files (x86)/Google/Chrome/Application/chrome.exe"
    "/mnt/c/Program Files/Google/Chrome/Application/chrome.exe"
    "/mnt/c/Program Files (x86)/Google/Chrome/Application/chrome.exe"
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
)

# Function to find Chrome
find_chrome() {
    for path in "${CHROME_PATHS[@]}"; do
        if [ -f "$path" ]; then
            echo "$path"
            return
        fi
    done

    if command -v google-chrome >/dev/null 2>&1; then
        echo "google-chrome"
        return
    fi
    if command -v google-chrome-stable >/dev/null 2>&1; then
        echo "google-chrome-stable"
        return
    fi

    echo ""
}

CHROME_EXE=$(find_chrome)
if [ -z "$CHROME_EXE" ]; then
    echo "Error: Chrome executable not found."
    exit 1
fi

# Define User Data Dir
USER_DATA_DIR="$HOME/.browser4/browser/chrome/default/pulsar_chrome"

# Handle Windows path formatting if on Windows/WSL
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    # Convert to Windows path for Chrome argument if on Windows
    # Using cygpath if available, or manual conversion
    if command -v cygpath >/dev/null 2>&1; then
        USER_DATA_DIR_ARG=$(cygpath -w "$USER_DATA_DIR")
    else
        # Fallback simple conversion
        USER_DATA_DIR_ARG=$(echo "$USER_DATA_DIR" | sed 's|^/c/|C:/|' | sed 's|/|\\|g')
    fi
elif grep -q Microsoft /proc/version 2>/dev/null; then
    # WSL
    if command -v wslpath >/dev/null 2>&1; then
        USER_DATA_DIR_ARG=$(wslpath -w "$USER_DATA_DIR")
    else
        USER_DATA_DIR_ARG="$USER_DATA_DIR"
    fi
else
    # Native Linux / Mac
    USER_DATA_DIR_ARG="$USER_DATA_DIR"
fi

# Ensure directory exists
mkdir -p "$USER_DATA_DIR"

echo "Launching Chrome..."
echo "EXE: $CHROME_EXE"
echo "DIR: $USER_DATA_DIR_ARG"

"$CHROME_EXE" --user-data-dir="$USER_DATA_DIR_ARG" --no-first-run --no-default-browser-check &
