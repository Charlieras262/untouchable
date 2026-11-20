# Untouchable documentation

The documentation site, built with [Docusaurus](https://docusaurus.io/). It needs Node 20 or newer.

```bash
npm ci
```

```bash
npm start
```

`npm start` runs a live preview. To check the production build (it fails on broken links):

```bash
npm run build
```

Every push to `main` that changes `untouchable-docs/` publishes the site to GitHub Pages through the
**Deploy docs** workflow (`.github/workflows/deploy_docs.yml`). It can also be run by hand from the Actions tab.
