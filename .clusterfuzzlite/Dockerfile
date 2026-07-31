FROM gcr.io/oss-fuzz-base/base-builder-jvm@sha256:cc3c3da52c2f48d7a6caf1d5736ab8ec8cb213f4345329b33cb6e497780b1ed0

RUN apt-get update && apt-get install -y openjdk-21-jdk-headless maven \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

COPY . $SRC/kubetest4j
WORKDIR $SRC/kubetest4j

COPY .clusterfuzzlite/build.sh $SRC/build.sh
