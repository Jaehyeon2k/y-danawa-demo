/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{vue,ts}"] ,
  theme: {
    extend: {
      colors: {
        brand: {
          green: "#2ECC71",
          blue: "#3498DB",
          orange: "#E67E22",
        },
      },
    },
  },
  plugins: [],
};
