import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const repoUrl = 'https://github.com/Charlieras262/untouchable';

const config: Config = {
  title: 'Untouchable',
  tagline: 'Seal documents in Java and detect any change to their content — even after another tool rewrites the file.',
  favicon: 'img/logo.svg',

  // Future flags, see https://docusaurus.io/docs/api/docusaurus-config#future
  future: {
    v4: true, // Improve compatibility with the upcoming Docusaurus v4
  },

  // GitHub Pages defaults for this repository. Change url/baseUrl if the site is hosted elsewhere.
  url: 'https://charlieras262.github.io',
  baseUrl: '/untouchable/',
  organizationName: 'Charlieras262',
  projectName: 'untouchable',
  trailingSlash: false,

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          editUrl: `${repoUrl}/tree/main/untouchable-docs/`,
          versions: {
            current: {
              label: '0.1.0-SNAPSHOT',
            },
          },
        },
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          editUrl: `${repoUrl}/tree/main/untouchable-docs/`,
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    announcementBar: {
      id: 'preview-0-1-0',
      content:
        'Untouchable 0.1.0 is a preview: PDF only, not yet on Maven Central. <a href="/untouchable/docs/getting-started/installation">See how to install it</a>.',
      isCloseable: true,
    },
    navbar: {
      title: 'Untouchable',
      logo: {
        alt: 'Untouchable logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Docs',
        },
        {to: '/blog', label: 'Releases', position: 'left'},
        {
          type: 'docsVersionDropdown',
          position: 'right',
        },
        {
          href: repoUrl,
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Introduction', to: '/docs/intro'},
            {label: 'Installation', to: '/docs/getting-started/installation'},
            {label: 'Signing with Acrobat', to: '/docs/guides/pdf-signatures'},
          ],
        },
        {
          title: 'Project',
          items: [
            {label: 'GitHub', href: repoUrl},
            {label: 'Issues', href: `${repoUrl}/issues`},
            {label: 'Specification', href: `${repoUrl}/blob/main/SPEC.md`},
          ],
        },
        {
          title: 'More',
          items: [
            {label: 'Releases', to: '/blog'},
            {label: 'Changelog', to: '/docs/changelog'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Carlos Ramos. Licensed under Apache 2.0. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java', 'bash', 'json', 'groovy', 'kotlin', 'yaml'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
