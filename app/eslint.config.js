// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require('eslint-config-expo/flat');

// No em dashes in anything the app shows (the owner, issue #80): strings, template text and JSX text. Comments are
// free. The server filters every AI-written word the same way (com.laa.safety.Dashes).
const EM_DASH = '[—―⸺⸻]';
const noEmDash = 'No em dashes in the app’s text: use a comma, a colon or a full stop (issue #80).';

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ['dist/*'],
  },
  {
    rules: {
      'no-restricted-syntax': [
        'error',
        { selector: `Literal[value=/${EM_DASH}/]`, message: noEmDash },
        { selector: `TemplateElement[value.raw=/${EM_DASH}/]`, message: noEmDash },
        { selector: `JSXText[value=/${EM_DASH}/]`, message: noEmDash },
      ],
    },
  },
]);
