import type {ReactNode} from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import CodeBlock from '@theme/CodeBlock';
import HomepageFeatures from '@site/src/components/HomepageFeatures';
import Heading from '@theme/Heading';

import styles from './index.module.css';

const example = `Untouchable untouchable = Untouchable.builder()
    .signer(Ed25519.signer(privateKey, "key-2026"))
    .keyResolver(KeyResolver.of("key-2026", Ed25519.verifier(publicKey)))
    .build();

// Seal: from now on, only adding signatures is allowed.
byte[] sealed = untouchable.seal(pdf, SealOptions.builder()
    .docId("invoice-123")
    .policy(Policies.SIGNATURES_ONLY)
    .build());

// ...the PDF goes out, gets signed in Acrobat and comes back...

VerificationResult result = untouchable.verify(received, VerifyOptions.expectDocId("invoice-123"));
result.isTrusted();   // true: same content, only signatures were added
result.findings();    // [INFO SIGNATURE_ADDED p.1 …]`;

function HomepageHeader() {
  const {siteConfig} = useDocusaurusContext();
  return (
    <header className={clsx('hero hero--primary', styles.heroBanner)}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          {siteConfig.title}
        </Heading>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <div className={styles.buttons}>
          <Link className="button button--secondary button--lg" to="/docs/getting-started/installation">
            Get started
          </Link>
          <Link className="button button--secondary button--lg" to="/docs/guides/how-it-works">
            How it works
          </Link>
        </div>
      </div>
    </header>
  );
}

export default function Home(): ReactNode {
  return (
    <Layout
      title="Tamper-evident documents for Java"
      description="Untouchable seals documents and detects whether their content was modified, tolerating allowed changes such as signatures.">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
        <section className="container margin-bottom--xl">
          <Heading as="h2" className="text--center">Seal once, verify anywhere</Heading>
          <CodeBlock language="java">{example}</CodeBlock>
        </section>
      </main>
    </Layout>
  );
}
