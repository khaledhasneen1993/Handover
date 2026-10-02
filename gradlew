#!/bin/sh
# Development fallback until the official Gradle wrapper JAR is obtained and its SHA-256 verified.
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$DIR/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  echo 'Official wrapper JAR unavailable; using installed Gradle (expected 9.3.1).' >&2
  exec gradle "$@"
fi
echo 'Official Gradle wrapper JAR is missing. Install verified 9.3.1 wrapper or use GitHub Actions setup-gradle.' >&2
exit 1
