#!/usr/bin/env sh
# =====================================================
# Maven launcher — CRM System
# Uses Maven from PATH; falls back to a local Maven 3.9.6 install.
# =====================================================
if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
fi

MAVEN_HOME="${MAVEN_HOME:-${USERPROFILE:-$HOME}/.gemini/maven/apache-maven-3.9.6}"
exec "${MAVEN_HOME}/bin/mvn" "$@"
