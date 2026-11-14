import { defineConfig } from 'astro/config';

// When deploying to GitHub Pages, use subpath '/Jugnu/'
// When deploying to Vercel or localhost, use root '/'
const isGitHubPages = Boolean(process.env.GITHUB_ACTIONS && !process.env.VERCEL);

export default defineConfig({
  output: 'static',
  site: isGitHubPages ? 'https://being-glad.github.io' : 'https://jugnu-music.vercel.app',
  base: isGitHubPages ? '/Jugnu/' : '/',
  build: {
    format: 'file'
  }
});
