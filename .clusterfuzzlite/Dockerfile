FROM gcr.io/oss-fuzz-base/base-builder-jvm

RUN apt-get update && apt-get install -y openjdk-21-jdk-headless maven \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

COPY . $SRC/kubetest4j
WORKDIR $SRC/kubetest4j

COPY .clusterfuzzlite/build.sh $SRC/build.sh
