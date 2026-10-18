FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends binkd=1.1a-115-1 openssh-server \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /etc/ssh/ssh_host_* \
    && mkdir -p /run/sshd /root/.ssh /data/inbound /data/nonsecure /data/partial /data/outbound /data/outbox \
    && chmod 700 /root/.ssh \
    && printf '\nPasswordAuthentication no\nKbdInteractiveAuthentication no\nPermitRootLogin prohibit-password\n' >> /etc/ssh/sshd_config

EXPOSE 22
ENTRYPOINT ["sh", "-c", "ssh-keygen -A && exec /usr/sbin/sshd -D -e"]
