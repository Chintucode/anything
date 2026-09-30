#!/bin/sh
# Copies the plans on this machine into the database the deployed app uses.
#
#   DATABASE_URL='postgresql://...' tools/import-local-data.sh --dry-run
#   DATABASE_URL='postgresql://...' tools/import-local-data.sh
#
# Safe to run twice: a plan already in the target is left exactly as it is,
# and the database on this machine is only ever read.
set -e
cd "$(dirname "$0")/../server"

if [ -z "$DATABASE_URL" ]; then
  echo "DATABASE_URL is not set. Run it like this, with the quotes:" >&2
  echo "  DATABASE_URL='postgresql://...' tools/import-local-data.sh" >&2
  exit 2
fi

# The tool needs only two jars. Maven has already downloaded both, because the
# app itself uses them, so there is nothing to fetch and this works offline.
CLASSPATH=""
for artifact in com/h2database/h2 org/postgresql/postgresql; do
  jar=$(find "$HOME/.m2/repository/$artifact" -name '*.jar' ! -name '*-sources.jar' 2>/dev/null | sort -V | tail -1)
  if [ -z "$jar" ]; then
    echo "Can't find the $(basename "$artifact") driver in ~/.m2. Run ./mvnw test once first." >&2
    exit 1
  fi
  CLASSPATH="$CLASSPATH:$jar"
done
CLASSPATH=${CLASSPATH#:}

BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
javac -nowarn -cp "$CLASSPATH" -d "$BUILD" \
  src/main/java/com/chintu/anything/config/DatabaseUrl.java \
  src/main/java/com/chintu/anything/tools/ImportLocalData.java

# Dates and times are copied with no time zone on them (see zoneFree in the tool),
# so the clock this runs under makes no difference to what is written.
java -Duser.timezone=UTC -cp "$BUILD:$CLASSPATH" com.chintu.anything.tools.ImportLocalData "$@"
