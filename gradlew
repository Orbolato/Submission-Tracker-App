#!/bin/sh
# Lightweight wrapper bootstrap. Downloads Gradle's tagged, standard wrapper JAR
# the first time. Avoid executing this script if you have not inspected it.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
if [ ! -s "$JAR" ]; then
  echo 'Fetching official Gradle 8.9 wrapper JAR (Internet required)...' >&2
  URL='https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar'
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "$URL" -o "$JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$JAR"
  else
    echo 'Install curl/wget or generate a standard Gradle wrapper locally.' >&2
    exit 1
  fi
fi
exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
