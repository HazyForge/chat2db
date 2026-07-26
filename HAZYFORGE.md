# HazyForge internal deployment

This fork adds an internal HazyForge container and Kubernetes deployment for
Chat2DB Community. These modifications are not an upstream Chat2DB release.
The upstream branding, copyright notices, and `LicenseRef-Chat2DB` license are
retained. The built container is private and is only for HazyForge internal use.

## Security boundary

Chat2DB Community is a single-user, local-first application. It has no accounts,
authorization, or tenant isolation. The Anvil Primaris deployment therefore has
no public route and applies a default-deny ingress policy. Do not add an Ingress,
Gateway API route, load balancer, or shared-user proxy without first changing
the application security model and reviewing the upstream license.

Access it over the Kubernetes API from a trusted administrator workstation:

```bash
kubectl --context anvil-admin-anvil-primaris \
  --namespace chat2db port-forward service/chat2db 10825:10825
```

Then open `http://127.0.0.1:10825`.

## Durable state and secrets

- The single replica writes to the retained `chat2db` PVC on `z400`.
- Azure Key Vault secret `chat2db-community-encryption-key` is projected by
  External Secrets as `/run/secrets/chat2db-community-encryption.key`.
- The encryption key and PVC are one recovery unit. Never rotate or delete the
  key independently: existing datasource passwords and AI API keys would become
  unreadable.
- The private GHCR pull credential comes from the existing
  `anvil-primaris-ghcr-read-pat` Key Vault secret.

## Image

`.github/workflows/hazyforge-image.yml` builds the frontend and backend from
this fork, smoke-tests the runtime, and publishes a private multi-architecture
image at `ghcr.io/hazyforge/chat2db`. Production values must pin the resulting
immutable manifest digest rather than `main` after the first build.
