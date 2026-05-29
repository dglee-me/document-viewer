import js from "@eslint/js";
import pluginVue from "eslint-plugin-vue";
import tseslint from "typescript-eslint";
import prettier from "eslint-config-prettier";
import pluginPrettier from "eslint-plugin-prettier";

export default tseslint.config(
    { ignores: ["dist", "node_modules", "*.min.js", "public"] },

    js.configs.recommended,
    ...tseslint.configs.recommended,
    ...pluginVue.configs["flat/recommended"],

    {
        files: ["**/*.vue", "**/*.ts", "**/*.js"],
        plugins: { prettier: pluginPrettier },
        languageOptions: {
            parserOptions: {
                parser: tseslint.parser,
                extraFileExtensions: [".vue"],
                sourceType: "module",
            },
        },
        rules: {
            ...prettier.rules,
            "prettier/prettier": "error",

            // TypeScript compiler handles these
            "no-undef": "off",

            "@typescript-eslint/no-explicit-any": "warn",
            "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],

            "vue/multi-word-component-names": "off",
            "vue/block-lang": "off",
            // TypeScript optional props don't need defaults
            "vue/require-default-prop": "off",
        },
    },
);
