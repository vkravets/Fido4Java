# Binkp protocol references

This directory preserves English FTSC publications and historical reference
material. Publication identifiers matter: FTS documents are standards, FSP files
are proposals, and FRL files are reference material. A document being present
does not mean that JFtn implements its extensions. The publication headers retain
their original dates, revisions and authors; they are not current project docs.

## Core protocol and implemented behavior

- [FTS-1026.001](fts-1026.001): binkp 1.0 framing, commands and session states.
- [FSP-1024.000](fsp-1024.000): historical binkp 1.1 specification proposal.
- [FTS-1027.001](fts-1027.001): CRAM authentication.
- [FTS-1028.001](fts-1028.001): NR mode.

JFtn advertises 1.1 when enabled and supports multiple batches and NR, with 1.0
fallback. Its implementation was also compared with
[binkd protocol.c at revision 5a84e85](https://github.com/pgul/binkd/blob/5a84e8596a96ca8b1f4ec69d9a608cd264aa06ba/protocol.c).
See the [project README](../../README.md#developers) for capability flags and
[real binkd tests](../../binkp/src/integrationTestShared/README.md) for the
interoperability scenarios actually checked.

## Additional and historical references

Incoming transfer commands and data require a completed password handshake.
`M_OK` is accepted only by a client waiting for authorization; `M_PWD` only by
a server waiting for the password. Repeated authentication and address changes
after authentication terminate the connection. These checks are shared by Netty
and MINA2 and covered by negative handler tests and normal loopback TCP exchanges.
CRAM responses may include optional arguments after the digest token; those do
not change password verification. Plain-text password suffixes are not ignored.

- [Original HTML specification](<Binkp Specification.html>)
- [FRL-1006.001](frl-1006.001): early binkp protocol reference.
- [FRL-1013.001](frl-1013.001): multiple-password extension.
- [FRL-1018.001](frl-1018.001): obsolete binkp 1.0 OPT MB extension; this differs
  from the binkp 1.1 batch mechanism and is not advertised by JFtn.
- [FSP-1027.001](fsp-1027.001): ND/NDA extension proposal.
- [FRL-1022.001](frl-1022.001) and [FTS-1030.001](fts-1030.001): CRC extension.
- [FTS-1029.001](fts-1029.001) and [FRL-1032.001](frl-1032.001): dataframe extension.

These references are retained for comparison. Consult the implementation and
tests before assuming support for an optional extension.

## Outgoing SSH connections

JFtn can run OpenSSH as a duplex process and start `binkd -i` on the remote host.
TCP remains the default for links without an SSH mapping. Add mappings to
`conf.d/50-binkp-transports.conf`, not the UI-managed main configuration:

```text
BinkpSsh=1:500/101|uplink-ssh|/etc/binkd/binkd.conf|/usr/sbin/binkd
BinkpSshConfig=conf.d/ssh_config
BinkpSshExecutable=ssh
```

The fields are `peer|SSH-host-alias|remote-binkd-config[|remote-binkd-executable]`.
The executable defaults to `binkd`. A normal `Link` declaration remains required;
its binkp password is checked independently of SSH authentication. Map multiple
links with separate entries. SSH mappings are file-managed and validated during
configuration reload; subsequent polls use the new configuration.

Install OpenSSH client separately on the JFtn host. Without `BinkpSshConfig`, its
normal per-user configuration is used. An explicit configuration path is relative
to the main JFtn configuration directory. For example, `conf.d/ssh_config` contains:

```sshconfig
Host uplink-ssh
    HostName mail.example.org
    Port 22
    User fido
    IdentityFile ~/.ssh/jftn_ed25519
    IdentitiesOnly yes
```

Keep that file without a `.conf` extension so JFtn does not parse it as a fragment.
Prepare the host key in the SSH user's `known_hosts` through a trusted channel.
JFtn forces `BatchMode=yes`, `StrictHostKeyChecking=yes` and no terminal (`-T`);
there are no interactive password or host-key prompts. Remote paths are quoted
for a POSIX shell. Configure remote shell startup files to keep stdout free of text.

The remote side needs a working binkd configuration and permission to run
`binkd -i`. No remote TCP binkp listener is needed. This release adds outgoing
SSH sessions, not an SSH server or a JFtn stdin/stdout command-line server.

The stream uses the common binkp handshake/commands, including NR and batches.
Writes are bounded, incomplete input frames fail, and a 30-second stream I/O
inactivity timeout aborts a stalled child. Process stderr is drained separately
and included in poll diagnostics, with the link password redacted. Successful
poll completion waits for final stream writes and a zero process exit status.
Cancellation terminates the child process and retains unacknowledged mail.

The Netty module hosts SSH interoperability tests alongside its existing binkd
suite. They run a real OpenSSH server and `binkd -i` in an isolated container,
generate temporary client keys, pin the container's host key, exchange files in
both directions with NR on/off, and reject unknown host/unaccepted client keys.
No private keys are copied to test report artifacts. The normal binkd CI stage
runs these scenarios too, using the sealed compiled integration runtime.
The fixture waits up to 30 seconds for per-container host key generation and
the sshd process before reading the trusted key. A startup failure includes the
container log; an allocated container port alone does not indicate SSH readiness.

To run only SSH scenarios with the Podman CLI on a Windows development host:

```powershell
.\gradlew.bat :binkp-root:binkp-netty:binkdDockerTest --tests '*SshBinkdInteropTest' -PsshContainerEngine=podman
```

The test runner needs `ssh` and `ssh-keygen` on PATH, or explicit
`-PsshExecutable=...` and `-PsshKeygen=...` paths. CI defaults to the Docker CLI.
This fixture does not require a Docker-compatible API endpoint when using Podman.
If Windows/WSL localhost forwarding is unavailable, supply a reachable VM address
with `'-PsshContainerHost=<VM-IP>'`. Discover that address from the current engine;
do not reuse another machine's IP. This changes only the fixture endpoint and
does not alter SSH trust checks or the production transport configuration.
