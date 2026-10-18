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
