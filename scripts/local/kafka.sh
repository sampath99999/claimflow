#!/usr/bin/env bash
# Runs a single node Kafka (KRaft) from the official binary in .local/kafka.
# Download once:
#   curl -O https://downloads.apache.org/kafka/4.3.2/kafka_2.13-4.3.2.tgz
#   mkdir -p .local/kafka && tar -xzf kafka_2.13-4.3.2.tgz -C .local/kafka --strip-components=1
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${JAVA_HOME:?Set JAVA_HOME to a JDK 21}"
KAFKA_HOME=.local/kafka
CONFIG=scripts/local/kafka.properties
CP="$KAFKA_HOME/libs/*"
JAVA="$JAVA_HOME/bin/java"
LOG4J="-Dlog4j2.configurationFile=$KAFKA_HOME/config/log4j2.yaml -Dkafka.logs.dir=.local/kafka-logs"

if [ ! -f .local/kafka-data/meta.properties ]; then
  CLUSTER_ID=$("$JAVA" -cp "$CP" $LOG4J kafka.tools.StorageTool random-uuid)
  "$JAVA" -cp "$CP" $LOG4J kafka.tools.StorageTool format --standalone -t "$CLUSTER_ID" -c "$CONFIG"
fi

exec "$JAVA" -Xmx512m -cp "$CP" $LOG4J kafka.Kafka "$CONFIG"
