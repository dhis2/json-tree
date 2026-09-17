#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# 1. Compile the benchmarks via Maven. The jmh profile adds the JMH deps and
#    registers src/jmh/java as a test source root. The compiler plugin override
#    in the profile makes the module-info --add-reads work.
mvn -q -Pjmh test-compile

# 2. Resolve the full test classpath (JMH + your library's deps).
mvn -q -Pjmh dependency:build-classpath \
    -Dmdep.outputFile=target/jmh-classpath.txt > /dev/null

# 3. Run JMH. Benchmark classes live in target/test-classes, your library in
#    target/classes, and everything else comes from the resolved classpath file.
java -cp "target/test-classes:target/classes:$(cat target/jmh-classpath.txt)" \
     org.openjdk.jmh.Main "$@"