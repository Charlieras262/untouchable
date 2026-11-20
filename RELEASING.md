# Releasing to Maven Central

Untouchable is published to [Maven Central](https://central.sonatype.com) as `io.qzz.toshiro:untouchable-parent`,
`untouchable-core` and `untouchable-pdf`. Publishing is done by the **Publish to Maven Central** workflow
(`.github/workflows/publish_central.yml`) when a GitHub release is published.

> A version published to Maven Central **can never be removed or replaced**. The workflow only uploads and
> validates the deployment; the final "Publish" is done by hand in the Central Portal.

## One-time setup

### 1. Verify the namespace

The `groupId` is `io.qzz.toshiro`, so the domain `toshiro.qzz.io` has to be verified:

1. In the [Central Portal](https://central.sonatype.com), open **View Namespaces → Add Namespace** and enter
   `io.qzz.toshiro`.
2. The portal shows a verification key. Add it as a **TXT record** on `toshiro.qzz.io` in its DNS provider.
3. Click **Verify Namespace** and wait until it shows as verified.

### 2. Create a Portal user token

**View Account → Generate User Token.** It gives a username and a password (they are not your login
credentials). Keep them for step 4.

### 3. Create and publish a GPG key

Central only accepts signed artifacts, and checks the signatures against public key servers.

```bash
gpg --full-generate-key
```

Choose `ECC (sign only)` / `Curve 25519` (or RSA 4096), an expiration date and a passphrase. Then publish the
public key (use the key id that `gpg --list-secret-keys --keyid-format long` shows):

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

### 4. Add the repository secrets

In **GitHub → Settings → Secrets and variables → Actions**, add:

| Secret | Value |
| --- | --- |
| `CENTRAL_TOKEN_USERNAME` | Username of the Portal user token |
| `CENTRAL_TOKEN_PASSWORD` | Password of the Portal user token |
| `GPG_PRIVATE_KEY` | Output of `gpg --armor --export-secret-keys <KEY_ID>` |
| `GPG_PASSPHRASE` | The key's passphrase |

## Releasing a version

1. Make sure `main` builds: `./mvnw verify`. The version in the POMs stays `X.Y.Z-SNAPSHOT`; the workflow sets
   the release version from the tag.
2. Update the [changelog](untouchable-docs/docs/changelog.md).
3. Create a GitHub release with a **`vX.Y.Z`** tag (for example `v0.1.0`).
4. The workflow runs the tests on Java 8, 11, 17, 21 and 25, then builds, signs and uploads the deployment.
5. In the Central Portal, open **Publish → Deployments**, check the deployment (it should be `VALIDATED`) and
   click **Publish**. The artifacts show up in Maven Central after a few minutes, and in search after a few hours.

The workflow can also be started by hand from the Actions tab with a version number.

## Publishing from a local machine

Add the token to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

Then set the release version and deploy (GPG asks for the passphrase):

```bash
./mvnw org.codehaus.mojo:versions-maven-plugin:2.19.1:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
./mvnw -Prelease deploy
```

Do not commit the version change: `main` keeps the `-SNAPSHOT` version.
