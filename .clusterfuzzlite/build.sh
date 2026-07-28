#!/bin/bash -eu

cd $SRC/kubetest4j

# Install to local repo so inter-module deps resolve for dependency:copy-dependencies
mvn install -DskipTests \
    -Dcheckstyle.skip=true \
    -Dspotbugs.skip=true \
    -Dmaven.javadoc.skip=true \
    -q

CURRENT_VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.2.0:evaluate \
    -Dexpression=project.version -q -DforceStdout)

# Copy module JARs to $OUT
cp "kubetest4j/target/kubetest4j-$CURRENT_VERSION.jar" "$OUT/kubetest4j.jar"
cp "metrics-collector/target/metrics-collector-$CURRENT_VERSION.jar" "$OUT/metrics-collector.jar"

# Copy transitive runtime dependencies into $OUT (flat)
mvn dependency:copy-dependencies \
    -pl kubetest4j,metrics-collector \
    -DincludeScope=runtime \
    -DoutputDirectory="$OUT" \
    -q

# Build classpath from all JARs in $OUT + Jazzer API
BUILD_CLASSPATH=$(find "$OUT" -maxdepth 1 -name '*.jar' | sort | tr '\n' ':')
BUILD_CLASSPATH="${BUILD_CLASSPATH}${JAZZER_API_PATH}"

# Build runtime classpath (all JARs relative to $this_dir)
RUNTIME_CLASSPATH=""
for jar in $(find "$OUT" -maxdepth 1 -name '*.jar' -exec basename {} \; | sort); do
    RUNTIME_CLASSPATH="${RUNTIME_CLASSPATH}\$this_dir/${jar}:"
done
RUNTIME_CLASSPATH="${RUNTIME_CLASSPATH}\$this_dir"

# Compile and package each fuzzer
for fuzzer in $(find "$SRC/kubetest4j/.clusterfuzzlite" -name '*Fuzzer.java'); do
    fuzzer_basename=$(basename -s .java "$fuzzer")
    javac -cp "$BUILD_CLASSPATH" "$fuzzer" -d "$OUT/"

    # Create an execution wrapper
    cat > "$OUT/$fuzzer_basename" <<FUZZER_EOF
#!/bin/sh
# LLVMFuzzerTestOneInput for fuzzer detection.
this_dir=\$(dirname "\$0")
LD_LIBRARY_PATH="$JVM_LD_LIBRARY_PATH":\$this_dir \\
\$this_dir/jazzer_driver \\
    --agent_path=\$this_dir/jazzer_agent_deploy.jar \\
    --cp=${RUNTIME_CLASSPATH} \\
    --target_class=${fuzzer_basename} \\
    --jvm_args="-Xmx2048m" \\
    \$@
FUZZER_EOF
    chmod +x "$OUT/$fuzzer_basename"
done
