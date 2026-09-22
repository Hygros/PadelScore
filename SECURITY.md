# Security Policy

## Supported Versions

Security fixes are planned for the latest published version only.

| Version | Supported |
| --- | --- |
| Latest release | Yes |
| Older releases | No |
| Unreleased development builds | Best effort |

## Reporting a Vulnerability

Do not report suspected vulnerabilities, credentials, private keys, device identifiers, or other sensitive information in a public issue, discussion, or pull request.

Use GitHub's private vulnerability reporting feature from the repository's **Security** tab when it is available.

If private vulnerability reporting is not available, open a public issue containing only a request for a private reporting channel. Do not include technical vulnerability details in that issue.

A useful private report should include:

- the affected version, tag, branch, or commit;
- the impacted file or component;
- steps to reproduce the issue;
- the expected and observed behavior;
- the potential impact;
- a proof of concept, if safe to provide; and
- any suggested mitigation.

Reports will be reviewed on a best-effort basis. No response time, remediation deadline, bounty, or public-disclosure date is guaranteed.

## Scope

Relevant reports include vulnerabilities in:

- the application source code;
- local data storage and restoration behavior;
- build or release configuration committed to this repository;
- dependency use; and
- accidental exposure of secrets or personal information in the repository.

The repository must not contain production signing keys, upload keys, passwords, tokens, local SDK paths, IP addresses, ADB identifiers, or private service configuration.

## Coordinated Disclosure

Please allow reasonable time for investigation and remediation before public disclosure. The maintainer may request additional information, propose a fix, or determine that a report does not represent a security vulnerability.
