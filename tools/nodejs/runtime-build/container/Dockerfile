FROM ubuntu:24.04

ARG DEBIAN_FRONTEND=noninteractive
ARG ANDROID_NDK_VERSION=26.1.10909125
ARG CMAKE_VERSION=3.22.1
ARG PYTHON_VERSION=3.11

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        ninja-build \
        openjdk-21-jdk-headless \
        python${PYTHON_VERSION} \
        xz-utils \
        zstd \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_NDK_HOME=/opt/android-ndk-${ANDROID_NDK_VERSION}
ENV CMAKE_VERSION=${CMAKE_VERSION}

WORKDIR /work

CMD ["sh", "tools/nodejs/runtime-build/build-node-runtime.sh"]
