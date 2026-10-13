FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends binkd=1.1a-115-1 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /data/outbound /data/outbox /data/inbound /data/nonsecure /data/partial \
    && chown -R 10001:10001 /data

USER 10001:10001
WORKDIR /data
EXPOSE 24554
# Foreground, server only, plaintext authentication and no traffic encryption.
ENTRYPOINT ["/usr/sbin/binkd", "-s", "-m", "/data/binkd.cfg"]
