# Reader control classification

The server classifies display records in `ReaderKludges`; it never uses this
classification to discard, validate, interpret or rewrite received controls.
`Body.getReaderLines()` returns all controls in reconciled stored order,
including unknown and repeated lines. AREA and parsed Origin/SEEN-BY trailers
remain outside the reading body. Parsed PATH/Via trailers follow its content.

## Sources and meaning of "known"

Reviewed GoldED+ revision
[`1a251c6375081453a410bb8d647be120b8902de3`](https://github.com/golded-plus/golded-plus/blob/1a251c6375081453a410bb8d647be120b8902de3/golded3/geline.cpp#L237):
`fts_list`, `fsc_list`, `xxx_list`, `rfc_list`, `ScanCtrlList`, `ScanLine` and
`MsgLineReIndex`. GoldED distinguishes recognized kludges from other hidden
lines; `VIEWKLUDGE` and `VIEWHIDDEN` are independent. It also supports configured
user-defined kludges; JFtn does not add that separate configuration mechanism.

"Known" means a recognized display tag, not that JFtn implements its semantics,
that its value is valid, or that FTSC adopted it as a standard. GoldED's historical
table categories are not current standard-status declarations (CHRS and TZUTC,
for example, have later FTS documents).

The official FTSC site could not be reached during this review. The texts below
were read from the [unofficial FTSC snapshot](https://github.com/zoomosis/ftsc/tree/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518),
whose README dates its source snapshot to 2021-02-14. This verifies the cited
revisions, not the latest status of every FTSC publication.

| Document | Relevant display invariant |
| --- | --- |
| [FTS-4000.001, sections 3–4](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fts/fts-4000.001) | SOH identifies control paragraphs. Tags are specified separately; experimental tags are allowed. Preserve unknown controls verbatim and as close to their original positions as possible. It does not define a closed registry or mandate hiding unknown controls in a reader. |
| [FTS-0009.001](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fts/fts-0009.001) | MSGID and REPLY. |
| [FTS-4001.001](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fts/fts-4001.001) | INTL, FMPT and TOPT use historical space-separated syntax. |
| [FTS-0004.001](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fts/fts-0004.001) | Echomail AREA, SEEN-BY and PATH; not all routing lines begin with SOH. |
| [FTS-5003.001](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fts/fts-5003.001) | CHRS character-set declaration. |
| [FTS-4008.002, section 3](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fts/fts-4008.002) | TZUTC; readers should also recognize TZUTCINFO. |
| [FTS-4009.001](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fts/fts-4009.001) | Mixed-case Via and trailing netmail tracking block. GoldED additionally accepts other case variants. |
| [FSC-0046.005](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fsc/fsc-0046.005) | PID product identifier; this revision declares itself a proposed protocol. |
| [FSC-0053.002](https://github.com/zoomosis/ftsc/blob/2f09a5d6c25b9b6e2f71a4403ee919857bf2e518/fsc/fsc-0053.002) | FLAGS extended attributes; this revision declares itself a proposed protocol. |

## Classification rules

Case-sensitive FTN names follow GoldED's FTS/FSC/XXX tables:

```
AREA INTL FMPT TOPT MSGID REPLY SEEN-BY PATH
CHARSET CHRC CHRS CODEPAGE DOMAIN EID ENC ENCLFILE FLAGS
FWDFROM FWDORIG FWDTO FWDDEST FWDSUBJ FWDAREA FWDMSGID
I51 MSGTO PID PTH REPLYADDR REPLYTO SPLIT SPTH TID
ACUPDATE DESTADDR ENCRYPTION EOT GATECHK GID GIF GMD GROUP
MOOD MSGSEQ NOTE ORIGID ORIGREF Recd RFC RFD RID #ROUTE
SN SOT TCL1 TCL2 TZUTCINFO TZUTC TZ XID
```

`Via` and `Original` are case-insensitive. Names end at the first ASCII space
or colon, or at end of line. Values remain literal even if malformed.

Gateway headers follow GoldED's RFC table: case-insensitive names, normally
requiring a colon. The names are Also-Control, Apparently-To, Approved,
Article-Names, Article-Updates, Bcc, Cc, Comment, Comments, Content-Description,
Content-Disposition, Content-ID, Content-Length, Content-Transfer-Encoding,
Content-Type, Control, Date, Delivered-To, Delivery-Date, Distribution,
Encrypted, Errors-To, Expires, Followup-To, From, In-Reply-To, Keywords, Lines,
Message-ID, Mailing-List, MIME-Version, Newsgroups, News-Software,
NNTP-Posting-Date, NNTP-Posting-Host, NNTP-Posting-User, Old-Date, Organization,
Originator, Path, Precedence, Priority, Received, References, Reply-To,
Return-Path, Return-Receipt-To, See-Also, Sender, Status, Subject, Summary,
Supersedes, To, Version and Xref. `From` and `#!` also permit space syntax.
The `RFC` prefix is accepted directly or with one nonletter separator within
a header tag (for example `RFC-Subject:`).
`X-` and `Resent-` headers with a colon are recognized gateway extensions;
they are not unknown FTN tags. An arbitrary `CUSTOM:` tag is unknown.

Only SOH-prefixed records are classified; an ordinary line such as `Subject:`
remains message text. Unknown or malformed SOH-prefixed records are still control
display records and are not dropped from the reader response.

## Reader policy

Unknown controls appear inline by default, even when the reader switch is off.
Personal Settings → Appearance → Unknown kludges in messages can select
**Always show** or **Show with the kludge toggle**. This preference is stored in
the account-owned `workspace` UI document as `unknownKludges: always | toggle`;
missing or invalid values select `always`.

The per-message switch reveals all controls and resets when navigating. The
complete disclosure remains independently available in both modes. All inline
controls use literal, selectable text with distinct styling. This affects neither
draft snapshots nor editing, quoting, templates, storage serialization or outgoing
packet generation.
