#!/bin/zsh

SCRIPT_DIR="${0:A:h}"

cd "$SCRIPT_DIR" || {
    echo "FEHLER: Projektordner konnte nicht geöffnet werden."
    read "?Zum Schließen Enter drücken ..."
    exit 1
}

java_home_is_25() {
    local candidate="$1"

    if [[ -z "$candidate" \
            || ! -x "$candidate/bin/java" ]]; then
        return 1
    fi

    local version_line

    version_line="$(
        "$candidate/bin/java" -version 2>&1 |
        head -n 1
    )"

    [[ "$version_line" == *'version "25.'* ]]
}

find_java_home() {
    local candidate
    local detected_java_home

    if [[ -n "${JAVA_HOME:-}" ]] \
            && java_home_is_25 "$JAVA_HOME"; then
        return 0
    fi

    for candidate in \
        "/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home" \
        "/usr/local/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
    do
        if java_home_is_25 "$candidate"; then
            export JAVA_HOME="$candidate"
            return 0
        fi
    done

    if [[ -x "/usr/libexec/java_home" ]]; then
        detected_java_home="$(
            /usr/libexec/java_home -v 25 2>/dev/null
        )"

        if java_home_is_25 "$detected_java_home"; then
            export JAVA_HOME="$detected_java_home"
            return 0
        fi
    fi

    return 1
}

echo "============================================================"
echo "RACE REPLAY LAB"
echo "============================================================"
echo

if ! find_java_home; then
    echo "FEHLER: Java JDK 25 wurde nicht gefunden."
    echo
    echo "Bitte Java 25 installieren und JAVA_HOME setzen."
    echo
    read "?Zum Schließen Enter drücken ..."
    exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

JAVA_LINE="$(
    "$JAVA_HOME/bin/java" -version 2>&1 |
    head -n 1
)"

echo "$JAVA_LINE"
echo

if [[ "$JAVA_LINE" != *'version "25.'* ]]; then
    echo "FEHLER: Gefunden wurde nicht Java 25."
    echo "JAVA_HOME=$JAVA_HOME"
    echo
    read "?Zum Schließen Enter drücken ..."
    exit 1
fi

echo "Starte Race Replay Lab ..."
echo

./mvnw javafx:run
EXIT_CODE=$?

echo

if [[ "$EXIT_CODE" -eq 0 ]]; then
    echo "Race Replay Lab wurde ordnungsgemäß beendet."
else
    echo "Race Replay Lab wurde mit einem Fehler beendet."
    echo "Fehlercode: $EXIT_CODE"
fi

echo
read "?Zum Schließen Enter drücken ..."

exit "$EXIT_CODE"
