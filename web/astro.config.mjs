import { defineConfig } from 'astro/config';

// https://astro.build/config
export default defineConfig({
  output: 'static',
  site: 'https://being-glad.github.io',
  base: process.env.NODE_ENV === 'production' ? '/Jugnu/' : '/',
  build: {
    format: 'file'
  }
});
