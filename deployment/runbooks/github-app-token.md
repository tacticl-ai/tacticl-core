# GitHub App Token Setup Runbook

One-time provisioning of the GitHub Personal Access Token used by the
conversational agent to create new repositories on the user's behalf via the
`<<<CREATE_REPO>>>` marker (handled by `ConversationService`,
implemented in `GitHubClient.createRepo`).

## 1. Generate a Personal Access Token

In your browser, while signed in to the GitHub account you want to own the
repos Tacticl creates:

https://github.com/settings/tokens/new

Set:

- **Note:** `tacticl-qa-app-token` (or `tacticl-prod-app-token`)
- **Expiration:** 90 days (rotate regularly)
- **Scopes:**
  - `repo` (full control of private repositories — required for create + commit + PR)
  - `admin:org` (only if you want Tacticl to be able to create repos inside
    GitHub orgs you administer; skip if you only want repos under your
    personal account)

Click **Generate token**. Copy the `ghp_...` value immediately — GitHub will
not show it again.

## 2. Store in Vault

Tacticl QA uses Vault context `tacticl-qa`; prod uses `tacticl`.

```bash
# QA
vault kv put secret/tacticl-qa/github \
  app-token="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"

# Prod (after QA verification)
vault kv put secret/tacticl/github \
  app-token="ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

`GitHubVaultConfig` reads this at startup when `tacticl.github.enabled=true`
and exposes it via `GitHubConfig.getAppToken()`. `ConversationService` passes
the token to `GitHubClient.createRepo` whenever the LLM emits the
`<<<CREATE_REPO:{...}>>>` marker.

The existing `github.client-id` / `github.client-secret` keys (used by the
older OAuth-app flow) are **not** affected by this change. They live at the
same Vault path and can coexist with `app-token`.

## 3. Verify the token works

From your laptop:

```bash
curl -H "Authorization: Bearer ghp_..." https://api.github.com/user
```

Expected: 200 OK with a JSON body listing your GitHub username. If you see
401, regenerate the token. If 403, you set the scopes wrong.

For org repo creation, also verify:

```bash
curl -H "Authorization: Bearer ghp_..." \
  https://api.github.com/orgs/<your-org>/members | jq '.[].login'
```

You should appear in the membership list with admin rights (otherwise repo
creation under that org will 403).

## 4. Restart the API container after Vault update

Vault loads at boot — set the secret **before** redeploying:

```bash
./scripts/deploy.sh qa
```

Or, if the container is already running and you just want to pick up the new
token without a redeploy:

```bash
ssh -p 443 platform-apps "docker restart tacticl-api-qa"
```

## 5. Confirm in logs

```bash
ssh -p 443 platform-apps 'docker logs tacticl-api-qa 2>&1 | grep -i "github.*vault"'
```

Expected:

```
INFO  io.strategiz.social.client.github.config.GitHubVaultConfig — Loaded GitHub app token from Vault
```

If you see `GitHub app token not found in Vault — repo creation via /CREATE_REPO marker will fail`, the Vault secret isn't set correctly.

## 6. Smoke-test the conversational flow

Once the token is loaded, in Telegram (linked, forum supergroup, `/init` complete):

1. Mention the bot: `@tacticl_qa_bot help me build a python CLI that converts markdown to PDF`
2. Engage for a turn or two.
3. The agent should propose a repo name + owner + visibility and ask for confirmation. Reply "yes".
4. The next agent turn should include the `<<<CREATE_REPO:{...}>>>` marker (the marker itself is stripped before sending to chat; you'll see a "Created https://github.com/owner/repo" line instead).
5. Verify on GitHub that the repo exists with an initial `main` branch (`auto_init=true`).

## 7. Rotation

PAT expires after 90 days. Rotate via:

```bash
# Generate new token at https://github.com/settings/tokens/new
vault kv put secret/tacticl-qa/github app-token="ghp_new_value"
ssh -p 443 platform-apps "docker restart tacticl-api-qa"
```

The old token can be revoked from https://github.com/settings/tokens after
rotation.

## Troubleshooting

- **Repo creation always fails with "unauthorized"** — token missing in Vault or token revoked. Check Vault, re-issue if needed.
- **Repo creation under org fails with 403** — token lacks `admin:org` scope, OR the GitHub account behind the token isn't an admin of that org. Either re-issue with the scope or use a different owner.
- **Repo creation under your personal account works but org fails** — same as above; the `admin:org` scope is independent of `repo`.
- **Conversation never asks about creating a repo** — the LLM may be classifying the work as non-code. Inspect the gathering system prompt; the LLM only triggers `CREATE_REPO` for CODE/DEVOPS work. Be explicit about "I want to build a Python script" rather than "I want to do X" if the classification is wrong.
