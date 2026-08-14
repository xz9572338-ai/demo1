#!/bin/sh
exec "$(dirname "$0")/apps/api/mvnw" -f "$(dirname "$0")/pom.xml" "$@"
