import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        ink: {
          950: "#080c14",
          900: "#0d1320",
          800: "#131b2e",
          700: "#1c2740",
          600: "#263352",
          400: "#4a5d7a",
          300: "#6b7fa0",
          200: "#8fa3c4",
          100: "#c4d0e6",
          50:  "#eef2f9",
        },
        accent: "#3b82f6",
      },
      fontFamily: {
        mono: ["'JetBrains Mono'", "ui-monospace", "SFMono-Regular", "monospace"],
      },
      animation: {
        "pulse-slow": "pulse 2.5s cubic-bezier(0.4,0,0.6,1) infinite",
      },
    },
  },
  plugins: [],
};

export default config;
