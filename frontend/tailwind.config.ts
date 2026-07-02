import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef7ff',
          100: '#d9edff',
          500: '#2f7df6',
          600: '#1a63e0',
          700: '#154fbd',
        },
      },
    },
  },
  plugins: [],
};

export default config;
