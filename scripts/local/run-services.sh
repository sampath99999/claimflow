#!/usr/bin/env bash
# Starts the four services from their built jars with the demo profile.
# JVMs run in GMT: the pgserver Postgres build ships without the tz database, and pgJDBC
# sends the JVM zone on connect. GMT is the one zone Postgres knows without it.
# Build first: ./mvnw -DskipTests package. Logs go to .local/logs/<service>.log.
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21}"
mkdir -p .local/logs .local/pids
for service in member-service claims-service audit-service gateway; do
  nohup "$JAVA_HOME/bin/java" -Xmx384m -Duser.timezone=GMT -jar "$service/target/$service-0.1.0-SNAPSHOT.jar"     --spring.profiles.active=demo > ".local/logs/$service.log" 2>&1 &
  echo $! > ".local/pids/$service.pid"
  echo "started $service (pid $!)"
done
