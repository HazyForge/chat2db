# HazyForge internal deployment

This fork adds an internal HazyForge container and Kubernetes deployment for
Chat2DB Community. These modifications are not an upstream Chat2DB release.
The upstream branding, copyright notices, and `LicenseRef-Chat2DB` license are
retained. The built container is private and is only for HazyForge internal use.

## Security boundary

Chat2DB Community is a single-user, local-first application. It has no accounts,
authorization, or tenant isolation. The Anvil Primaris deployment therefore
keeps Chat2DB on a private ClusterIP and permits ingress only from the exact
Chat2DB proxy workload in the shared `oauth2-proxy` namespace. The public route,
ZITADEL session enforcement, immutable subject allowlist, and auth-header
stripping are owned by that central platform deployment.

The hosted origin is an exact, validated HTTPS value. Wildcard CORS origins,
wildcard OIDC redirect URIs, direct publication of port 10825, and domain-wide
identity allowlists are not supported by this deployment.

## Durable state and secrets

- The single replica writes to the retained `chat2db` PVC on `z400`.
- Azure Key Vault secret `chat2db-community-encryption-key` is projected by
  External Secrets as `/run/secrets/chat2db-community-encryption.key`.
- The encryption key and PVC are one recovery unit. Never rotate or delete the
  key independently: existing datasource passwords and AI API keys would become
  unreadable.
- The private GHCR pull credential comes from the existing
  `anvil-primaris-ghcr-read-pat` Key Vault secret.
- The dedicated ZITADEL client ID and secret, oauth2-proxy cookie key, and exact
  allowed subject are projected from Azure Key Vault by External Secrets.

## Managed PostgreSQL datasource

The chart can reconcile one PostgreSQL datasource at application startup with
`datasourceBootstrap.enabled`. Non-secret connection identity is supplied in
Helm values. The password and CA certificate are read from files in a dedicated
existing Kubernetes Secret; they must never be placed directly in values.

The datasource is owned by an explicit management key stored with its local
record. Reconciliation never adopts an unmarked datasource, never treats an
alias as ownership, and refuses an alias collision rather than changing a
user-created connection. New and changed configuration is connection-tested
before it is persisted, and the password uses the same AES-GCM encryption key
as manually created Community datasources.

The PostgreSQL account is the write-safety boundary. Give it only `CONNECT`,
schema `USAGE`, and the required `SELECT` grants; Chat2DB does not impose a
query-level read-only policy. Because this cluster has no Secret-reload
controller, rotate the mounted credential together with a declarative pod
rollout so startup reconciliation applies the new password.

## Image

`.github/workflows/hazyforge-image.yml` builds the frontend and backend from
this fork, smoke-tests the runtime, and publishes a private multi-architecture
image at `ghcr.io/hazyforge/chat2db`. Production values must pin the resulting
immutable manifest digest rather than `main` after the first build.
