# Production Release Guide

## Deployment model

FinCore publishes immutable backend and web images to GitHub Container Registry
(GHCR). The production server only pulls those images and starts the Compose
stack. Database credentials and JWT secrets remain in the server-local
`.env.production` file and are never placed in a container image or GitHub
workflow file.

The release workflow is intentionally manual. CI validates every push and pull
request to `dev` or `main`; a maintainer must then start **Release production**
from the `main` branch and pass the protected `production` environment gate.

## One-time server preparation

1. Provision a Linux VM with Docker Engine and the Docker Compose plugin.
2. Clone this repository to a non-root deployment path, for example
   `/opt/fincore`.
3. Copy `.env.production.example` to `.env.production`, replace every
   placeholder, and restrict the file to its service account:

```sh
cd /opt/fincore
cp .env.production.example .env.production
chmod 600 .env.production
```

4. Configure HTTPS in a reverse proxy or load balancer in front of `APP_PORT`.
   Set `CORS_ALLOWED_ORIGINS` to the exact public `https://` origin.
5. In GitHub, create the protected environment named `production`. Add a
   required reviewer before allowing real releases.

## GitHub configuration

Create these repository or environment secrets:

| Secret | Purpose |
| --- | --- |
| `DEPLOY_HOST` | Host name or IP address of the Linux deployment server. |
| `DEPLOY_USER` | Non-root SSH account permitted to run Docker Compose. |
| `DEPLOY_SSH_PRIVATE_KEY` | Dedicated deployment key, without a passphrase. |
| `DEPLOY_KNOWN_HOSTS` | Exact `known_hosts` entry for the deployment server. |
| `DEPLOY_PATH` | Absolute directory containing the cloned repository, for example `/opt/fincore`. |

The built-in `GITHUB_TOKEN` is used to publish GHCR images. Configure package
visibility and read access so the deployment server can pull the images. For a
private package, authenticate the server once using a least-privilege GitHub
token with `read:packages`.

The Compose stack uses the PostgreSQL 18 volume layout and mounts persistent
storage at `/var/lib/postgresql`. Do not change it back to
`/var/lib/postgresql/data`; that path is for PostgreSQL 17 and earlier. A real
upgrade from an older PostgreSQL major version requires a database backup or
`pg_upgrade`, not only an image-tag change.

## Release and smoke checks

1. Merge reviewed changes from `dev` into `main`.
2. Open GitHub Actions, choose **Release production**, select `main`, and use
   the default image tag `stable` or an approved release tag.
3. Approve the protected environment. The workflow publishes both images,
   pulls them on the server, restarts the stack without rebuilding, and checks
   the public gateway and readiness endpoint.
4. Verify the browser login flow and inspect the service state:

```sh
cd /opt/fincore
docker compose --env-file .env.production -f docker-compose.production.yml ps
docker compose --env-file .env.production -f docker-compose.production.yml logs --tail=100 backend web
```

## Rollback

The published images are tagged by both the chosen release tag and the commit
SHA. To roll back, manually run the release workflow again with an earlier
approved tag, or on the server export the two earlier image references and run
`docker compose ... up -d --no-build`. Never roll back by editing financial
records or deleting the PostgreSQL volume.
