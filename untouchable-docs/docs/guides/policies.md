---
sidebar_position: 2
title: Policies
---

# Policies

A policy decides which changes are allowed after sealing. The **issuer** chooses it when sealing and it is
stored inside the signed seal, so whoever verifies cannot relax it.

```java
byte[] sealed = untouchable.seal(pdf, SealOptions.builder()
    .policy(Policies.SIGNATURES_ONLY)
    .build());
```

## Built-in policies

| Policy | Id | Allows | Typical use |
| --- | --- | --- | --- |
| `Policies.STRICT` *(default)* | `strict` | Only changes that do not touch the content: re-saving, metadata. | Issued documents, evidence. |
| `Policies.SIGNATURES_ONLY` | `signatures-only` | The above + new signatures, with their appearance (text or image). | Documents that are signed outside your system. |
| `Policies.SIGNATURES_AND_FORMS` | `signatures-and-forms` | The above + filling in **existing** form fields. | Forms to complete and sign. |

## How a policy works

A policy assigns a **severity** to each kind of [finding](./verification-results.md#finding-types):

| Finding | `strict` | `signatures-only` | `signatures-and-forms` |
| --- | --- | --- | --- |
| `SIGNATURE_ADDED` | VIOLATION | INFO | INFO |
| `FORM_FIELD_CHANGED` | VIOLATION | VIOLATION | INFO |
| `SIGNATURE_OVERLAPS_CONTENT`, `SIGNATURE_OUTSIDE_DECLARED_FIELDS`, `CERTIFICATION_INVALID` | WARNING | WARNING | WARNING |
| Everything else (text, images, pages, annotations…) | VIOLATION | VIOLATION | VIOLATION |

The status follows from the severities: any `VIOLATION` gives `MODIFIED`; only allowed changes give
`INTACT_WITH_ALLOWED_CHANGES`; no changes give `INTACT`. Warnings never change the status, but
`result.hasWarnings()` tells you they are there.

## Custom policies

Implement `ChangePolicy` and register it in the builder — **both** where you seal and where you verify:

```java
ChangePolicy commentsAllowed = new ChangePolicy() {
    @Override public String id() { return "comments-allowed"; }

    @Override public Severity severity(FindingType type) {
        switch (type) {
            case ANNOTATION_ADDED:
            case SIGNATURE_ADDED:
                return Severity.INFO;
            case SIGNATURE_OVERLAPS_CONTENT:
                return Severity.WARNING;
            default:
                return Severity.VIOLATION;
        }
    }
};

Untouchable untouchable = Untouchable.builder()
    .policy(commentsAllowed)
    // signer, keyResolver…
    .build();
```

The seal only stores the policy **id**. A verifier that does not know the id returns `UNSUPPORTED` instead of
guessing.
