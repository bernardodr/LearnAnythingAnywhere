# Can LAA offer "Sign in with ChatGPT"?

Research date: 2026-11-09. Primary sources only (OpenAI's own docs and GitHub repos). Each citation shows the day the page was read. **openai.com and help.openai.com refused automated reads (HTTP 403)**, so anything that exists only on those sites is listed under *Could not verify*. Nothing there is filled in from memory.

## Short answer

- **It exists, but we can't get in yet.** "Sign in with ChatGPT" is a real OpenID Connect sign-in run by OpenAI at `auth.openai.com`. Identity sign-in is "currently available to selected commercial partners through a limited trial", and new apps join a waitlist ([quickstart, 2026-11-09](https://developers.openai.com/siwc/quickstart); [request a client ID, 2026-11-09](https://developers.openai.com/siwc/request-client-id)).
- **Billing AI usage to the user's ChatGPT plan also exists**, for Plus and Pro users. The self-serve route is documented only for open-source or locally run apps that use a `127.0.0.1` callback. "Paid or remotely hosted" apps must join the waitlist first ([cookbook, 2026-11-09](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt)).
- **Codex's "Sign in with ChatGPT" and ChatGPT plugin/app OAuth are different things.** Neither is a route for LAA.
- **Conclusion:** we can't ship it today. If we want it, join the waitlist and keep a standard sign-in (Apple, Google or email) as the real path.

## 1. Is there a public sign-in provider third-party apps can use?

**Verified.** OpenAI documents "Sign in with ChatGPT" (SIWC) for websites, ChatGPT plugins and open-source apps ([SIWC home, 2026-11-09](https://developers.openai.com/siwc)). It uses **OAuth 2.0** (a standard way to let a user sign in to one service through another) with **OpenID Connect, or OIDC** (an identity layer on OAuth: you get back a signed "ID token" that says who the user is) and **PKCE** (a one-time secret that proves the app finishing the sign-in is the one that started it) ([website guide, 2026-11-09](https://developers.openai.com/siwc/website)).

- **Status and eligibility:** identity sign-in is a "limited trial" for "selected commercial partners". To join, "contact your OpenAI representative" ([website guide, 2026-11-09](https://developers.openai.com/siwc/website)), or fill in the interest form: "Sign in with ChatGPT is currently offered to a select group of commercial partners" ([request a client ID, 2026-11-09](https://developers.openai.com/siwc/request-client-id)). It is not GA (generally available) and not self-serve.
- **Registering an app:** OpenAI issues the client ID (typically `oaiapp_…`), plus "an exact registered callback URL for each environment". Both public clients (no secret) and confidential clients (`client_secret_basic`) are supported ([website guide, 2026-11-09](https://developers.openai.com/siwc/website)).
- **Endpoints**, from OpenAI's live discovery document ([openid-configuration, 2026-11-09](https://auth.openai.com/.well-known/openid-configuration)) and the guide:

| What | Value |
|---|---|
| Issuer | `https://auth.openai.com` |
| Discovery | `https://auth.openai.com/.well-known/openid-configuration` |
| Authorize | `https://auth.openai.com/api/accounts/authorize` |
| Token | `https://auth.openai.com/api/accounts/oauth/token` |
| Userinfo | `https://auth.openai.com/api/accounts/oauth/userinfo` (in discovery only; the guides never use it) |
| JWKS (the public keys that check token signatures) | `https://auth.openai.com/.well-known/jwks.json` |
| Revocation | `https://auth.openai.com/api/accounts/oauth/revoke` |

- **Protocol details** ([openid-configuration, 2026-11-09](https://auth.openai.com/.well-known/openid-configuration)): `response_types_supported: ["code"]`, `code_challenge_methods_supported: ["S256"]`, ID tokens signed `RS256`, token auth methods `client_secret_basic`, `client_secret_post` and `none`. The discovery document has no `registration_endpoint`.
- **Scopes:** identity sign-in sends `openid profile email`. Discovery also lists `offline_access` ([website guide, 2026-11-09](https://developers.openai.com/siwc/website)). The guide says the only artifact you need is the `id_token`: "Don't require an OpenAI `access_token` or `refresh_token`".
- **PKCE:** required, using S256. Generate a fresh `state`, PKCE verifier and `nonce` for every sign-in ([website guide, 2026-11-09](https://developers.openai.com/siwc/website)).
- **Redirect URIs, mobile and native apps:** the website guide shows only an `https://…/auth/openai/callback` URL, which must match exactly. **No page covers iOS, Android, custom URL schemes or universal links** (an exact search of the SIWC pages found nothing).
- **ID token claims** (sample token and table in the [website guide, 2026-11-09](https://developers.openai.com/siwc/website)): `iss`, `sub` ("identifies the OpenAI user to your app"), `aud`, `nonce`, `auth_time`, `email`, `email_verified`, `name`, `sid`, an `https://api.openai.com/auth` object holding `per_user_salt`, `iat`, `exp`, `jti` and `at_hash`. The sample shows `"email_verified": false`, so the email can be unverified. `picture` is mentioned in the `profile` scope description and read by the sample code, but it is not in the sample token. Discovery lists `picture` among the supported claims.
- **Rules for the app** ([quickstart, 2026-11-09](https://developers.openai.com/siwc/quickstart); [website guide, 2026-11-09](https://developers.openai.com/siwc/website)):
  - Label the button **Continue with ChatGPT**. Use one of four approved button formats ("Continue with" or "Sign in with" ChatGPT, black or white), with "approved OpenAI branding".
  - Place it with the other sign-in options and give it "comparable visual prominence".
  - Add the `data-oai-siwc-button` attribute to the button.
  - Key accounts on (issuer, client ID, `sub`). "An email match alone isn't proof of account ownership."
  - Never pass the ID token to browser JavaScript, and avoid storing raw ID tokens.
- **Required logout endpoint:** OpenAI sends OIDC Back-Channel Logout tokens (server-to-server sign-out messages) to a public HTTPS endpoint of yours. "Your app must validate the token and end the sessions it identifies" ([security notification, 2026-11-09](https://developers.openai.com/siwc/account-security-notification)).
- **DevKit:** OpenAI's DevKit is "for developers building open-source apps that run on a user's own machine". Its code is under a "Noncommercial License" ([devkit README, 2026-11-09](https://github.com/openai/sign-in-with-chatgpt-devkit)).

**Could not verify:**
- Regions.
- Review criteria and waitlist timelines.
- What the userinfo endpoint returns.
- Mobile redirect support.
- The legal terms: a "Sign in with ChatGPT Terms" page exists at `openai.com/policies/sign-in-with-chatgpt-terms/` but returned 403.

## 2. Can sign-in bill model usage to the user's ChatGPT plan?

**Verified: yes, as an optional extra permission called "ChatGPT plan usage".**

- **Who:** "Eligible ChatGPT Plus and Pro users". Plan usage "is available to all open-source partners and selected private clients" ([quickstart, 2026-11-09](https://developers.openai.com/siwc/quickstart)). Usage is "available for open-source tools and personal projects that run locally. If you're building a paid or remotely hosted app, join the waitlist … before offering it to users" ([cookbook, 2026-11-09](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt); [overview, 2026-11-09](https://developers.openai.com/siwc/token-sharing-open-source)).
- **How it works** ([registration and sign-in, 2026-11-09](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)):
  - The first sign-in uses `client_id=dynamic_agent_client`, and OpenAI issues a per-user `oaiapp_…` client. No client secret or API key is needed.
  - Scopes: `offline_access resource.invoke chatgpt.tokens.use.direct`, with `resource=https://api.openai.com/v1`.
  - The redirect must be an HTTP loopback such as `http://127.0.0.1:1455/auth/callback` ("Do not substitute with `localhost`").
  - "A valid ID token alone does not authorize ChatGPT plan usage", so check that `chatgpt.tokens.use.direct` was actually granted.
- **Terms of use:**
  - Requests use "usage included in the user's ChatGPT plan or available credits" ([cookbook, 2026-11-09](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt)).
  - Plus users share one five-hour limit across all such apps. That limit "does not apply for Pro users" ([accounts and sessions, 2026-11-09](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)).
  - At the limit, inference stops: "OpenAI does not silently switch the request to another billing path". App-specific limits can also apply. OpenAI "does not currently notify your tool when a user disconnects" ([errors and recovery, 2026-11-09](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery)).
  - Users manage usage at `chatgpt.com/settings/usage` ([UI/UX guidelines, 2026-11-09](https://developers.openai.com/siwc/ui-ux-guidelines)).
- **Tokens:** access tokens last one hour. Refresh tokens last 30 days and are replaced on each refresh ([token reference, 2026-11-09](https://developers.openai.com/siwc/token-sharing-open-source/token-reference)).
- **Preview limits** ([preview limitations, 2026-11-09](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)):
  - Responses API only, with `store: false` and `stream: true`.
  - Fields such as `temperature`, `max_output_tokens`, `metadata` and `prompt` are not supported.
  - Image generation, file search, Code Interpreter and hosted MCP are not supported.
- **Remote servers:** running on a remote machine means signing in locally and copying the credentials across. "Host-specific usage attribution and revocation … for transferred sessions are not yet available" ([self-hosted VMs, 2026-11-09](https://developers.openai.com/siwc/token-sharing-open-source/self-hosted-vms)).

**Could not verify:**
- Any price or fee charged to the developer (the docs are silent).
- Whether ChatGPT Go or Free plans qualify.
- Per-app weekly caps. Search snippets of a help-centre page suggest them, but the page returned 403.

## 3. Related but different things

**(a) Codex CLI and IDE "Sign in with ChatGPT".**
- **Verified:** Codex offers "Sign in with ChatGPT for subscription access" or an API key "for usage-based access". The browser returns credentials to a local callback (default `localhost:1455`). There is also a device-code flow (beta). Credentials are cached in `~/.codex/auth.json`: "Treat … like a password" ([Codex auth docs, 2026-11-09](https://learn.chatgpt.com/docs/auth); `developers.openai.com/codex/auth` redirects there).
- **Verified:** the source code shows Codex using issuer `https://auth.openai.com`, port 1455 and PKCE, with scopes `openid profile email offline_access api.connectors.read api.connectors.invoke` ([server.rs at d257beb, 2026-11-09](https://github.com/openai/codex/blob/d257beb16a02e8bc218619d8bbc5105064cb252c/codex-rs/login/src/server.rs)). Codex uses its own hard-coded Codex client ID ([manager.rs, 2026-11-09](https://github.com/openai/codex/blob/d257beb16a02e8bc218619d8bbc5105064cb252c/codex-rs/login/src/auth/manager.rs)). It is Codex's own login, not a program for other apps.
- **The route for other apps:** OpenAI's documented route is SIWC with their own client. Third-party apps can hand their own SIWC token to Codex app-server, and "No separate Codex sign-in is required" ([Codex app-server, 2026-11-09](https://developers.openai.com/siwc/token-sharing-open-source/codex-app-server)).
- **Could not verify:** whether reusing Codex's client ID is forbidden. The docs are silent, and the terms page is unreadable.

**(b) ChatGPT apps / plugins (formerly "Apps SDK"): the reverse direction.**
- `developers.openai.com/apps-sdk` now serves the "Plugins" docs ([plugins, 2026-11-09](https://developers.openai.com/apps-sdk)).
- **How OAuth works there:**
  - ChatGPT or Codex is the OAuth *client*, and **your** MCP server and authorization server sign the user in to **your** service, "conform[ing] to the MCP authorization spec" (OAuth 2.1).
  - Client registration uses Client ID Metadata Documents (preferred), dynamic registration or predefined clients.
  - PKCE S256 is required. The redirect URI is `https://chatgpt.com/connector_platform_oauth_redirect` ([plugin auth, 2026-11-09](https://developers.openai.com/plugins/build/auth)).
- **Nesting SIWC inside it:** SIWC can be nested inside this flow, but enabling it means contacting "your OpenAI representative on the partnerships team" ([SIWC in plugins, 2026-11-09](https://developers.openai.com/siwc/chatgpt-plugin)).
- Only relevant if LAA were built as a ChatGPT plugin.

**(c) OpenAI developer platform login.**
- **Verified:** developers create API keys in the dashboard at `platform.openai.com/api-keys` ([API quickstart, 2026-11-09](https://developers.openai.com/api/docs/quickstart)). API-key usage is billed "through your OpenAI Platform account at standard API rates" ([Codex auth docs, 2026-11-09](https://learn.chatgpt.com/docs/auth)).
- I found no OpenAI page describing a way for third-party apps to sign users in with their Platform account.
- **Could not verify:** whether ChatGPT and Platform logins are the same account. `platform.openai.com/login` and the help centre returned 403.

## 4. Practical conclusion for LAA

**We can't ship it today.** We have no client ID, and getting one means joining a waitlist for "a select group of commercial partners". Plan billing doesn't fit LAA either:
- It is self-serve only for open-source or locally run apps with a `127.0.0.1` callback.
- LAA is a hosted server plus a phone app.
- It would also mean a new provider path in `LlmGateway` with the preview's restricted request shape.

**If OpenAI approves us later (identity only), rough steps.** This is my sketch, not OpenAI's.
- **Spring Boot:**
  - Register an HTTPS callback for each environment, such as `/auth/openai/callback`.
  - Add a start route that creates a one-time transaction (`state`, `nonce`, PKCE S256, about 10-minute expiry) in a Flyway table and redirects to the authorize endpoint.
  - The callback checks `state`, exchanges the code (with the secret in `.env` only), and verifies the RS256 ID token against the JWKS (`iss`, `aud`, `exp`, `nonce`, `sub`).
  - Map (issuer, client ID, `sub`) to a user, then issue our existing HS256 JWT.
  - Add the back-channel logout endpoint.
  - The repo already has `spring-boot-starter-oauth2-resource-server`, which includes a JWKS-based JWT decoder, so likely no new dependency.
- **Expo:**
  - Add an approved "Continue with ChatGPT" button.
  - Open the server start route with `WebBrowser.openAuthSessionAsync` (`expo-web-browser` is installed).
  - Have the server redirect back to `laa://…` (the `laa` scheme is already in `app.json`) with a one-time code, which the app swaps for our JWT.
  - On web, use a normal redirect.
  - This keeps OpenAI's callback a plain HTTPS server URL and avoids the undocumented mobile redirect question.

**Realistic alternatives.** These are outside the OpenAI question and were not researched here:
- **Sign in with Apple:** standard OIDC. Check Apple's rules for iOS apps that offer third-party sign-in.
- **Sign in with Google:** standard OIDC. The server flow would be the same as sketched above.
- **Email magic link or passkeys:** replaces the dev-only email login without depending on any provider's programme.

## Could not verify (summary)

- **Pages that returned 403:** the help-centre articles *Sign in with ChatGPT* and *Using your ChatGPT plan in other apps and sites* (user eligibility including Go, per-app weekly caps, what users see shared); ChatGPT release-notes rollout dates; the *Sign in with ChatGPT Terms*; the interest form; any openai.com announcement post; `platform.openai.com/login`.
- **Topics OpenAI's docs are silent on:** regions; review criteria and timelines; mobile or custom-scheme redirects; what userinfo returns; whether `picture` is in the ID token; developer-side pricing for plan usage; whether reusing Codex's client ID is allowed; whether ChatGPT and Platform logins share one account.

## Sources (all read 2026-11-09)

- SIWC (identity): https://developers.openai.com/siwc · /siwc/quickstart · /siwc/request-client-id · /siwc/website · /siwc/chatgpt-plugin · /siwc/account-security-notification · /siwc/ui-ux-guidelines (all on developers.openai.com)
- SIWC plan usage, under https://developers.openai.com/siwc/token-sharing-open-source: the overview page, plus `/sign-in`, `/profiles-and-sessions`, `/codex-app-server`, `/self-hosted-vms`, `/token-reference`, `/errors-and-recovery`, `/preview-limitations`
- Cookbook, SIWC in your open-source app: https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt
- OIDC discovery document: https://auth.openai.com/.well-known/openid-configuration
- DevKit repo: https://github.com/openai/sign-in-with-chatgpt-devkit (latest commit 0a36fef, 2026-10-07)
- Codex auth docs: https://learn.chatgpt.com/docs/auth (redirected from https://developers.openai.com/codex/auth)
- Codex source, login: https://github.com/openai/codex/blob/d257beb16a02e8bc218619d8bbc5105064cb252c/codex-rs/login/src/server.rs and `.../auth/manager.rs`
- Plugins / Apps SDK: https://developers.openai.com/apps-sdk and https://developers.openai.com/plugins/build/auth
- API quickstart: https://developers.openai.com/api/docs/quickstart
- Tried but returned 403: https://help.openai.com/en/articles/20001410-sign-in-with-chatgpt, https://help.openai.com/en/articles/20001542-using-your-chatgpt-plan-in-other-apps-and-sites, https://openai.com/policies/sign-in-with-chatgpt-terms/, https://openai.com/form/sign-in-with-chatgpt-interest/, https://platform.openai.com/login
