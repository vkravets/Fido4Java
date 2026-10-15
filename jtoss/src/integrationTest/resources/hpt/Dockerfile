FROM debian:bookworm-slim AS builder
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates git cmake make gcc libc6-dev zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*
COPY revisions.txt /src/revisions.txt
RUN set -eu; while read -r project revision; do \
      git init "/src/$project"; \
      git -C "/src/$project" fetch --depth 1 "https://github.com/huskyproject/$project.git" "$revision"; \
      git -C "/src/$project" checkout --detach FETCH_HEAD; \
      cd "/src/$project"; \
      cmake -S "/src/$project" -B "/build/$project" \
        -DBUILD_SHARED_LIBS=OFF -DCMAKE_INSTALL_LIBDIR=lib \
        -DCMAKE_DISABLE_FIND_PACKAGE_PerlLibs=TRUE; \
      cmake --build "/build/$project" -j2; \
      cmake --install "/build/$project"; \
    done < /src/revisions.txt

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data /opt/jftn/lib && chown -R 10001:10001 /data /opt/jftn
COPY --from=builder /usr/local/bin/ /usr/local/bin/
COPY --from=builder /src/revisions.txt /opt/hpt-revisions.txt
ENV LANG=C.UTF-8 TZ=UTC
USER 10001:10001
WORKDIR /data
CMD ["sh", "-c", "echo HPT_READY; exec sleep infinity"]
