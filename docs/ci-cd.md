# CI / CD Setup

The pipeline is defined in [`.github/workflows/ci-cd.yml`](../.github/workflows/ci-cd.yml).

## What it does

| Trigger | Jobs |
|---------|------|
| Push or PR to **any** branch | **Build & Test** — compiles the project and runs the full test suite (`./mvnw test`) |
| Push to **`main`** (tests must pass) | **Deploy to Cloud Run** — builds the Docker image, pushes it to Artifact Registry, and deploys to the existing Cloud Run service |

---

## Required configuration

### GitHub repository variables
*(Settings → Secrets and variables → **Variables**)*

| Name | Example | Purpose |
|------|---------|---------|
| `GCP_PROJECT_ID` | `my-gcp-project-123` | GCP project that hosts Cloud Run |
| `GCP_REGION` | `us-central1` | Region of the Cloud Run service **and** Artifact Registry repo |
| `CLOUD_RUN_SERVICE` | `ai-scheduler-backend` | Name of the Cloud Run service to update |
| `AR_REPOSITORY` | `ai-scheduler` | Artifact Registry Docker repository name (defaults to `ai-scheduler` if unset) |

### GitHub repository secrets
*(Settings → Secrets and variables → **Secrets**)*

| Name | Purpose |
|------|---------|
| `WIF_PROVIDER` | Workload Identity Provider resource name (see setup below) |
| `WIF_SERVICE_ACCOUNT` | Service account email that Cloud Build / Cloud Run will act as |

> **Alternative (simpler, but less secure):** if you prefer a service-account JSON key instead
> of Workload Identity Federation, set a secret called `GCP_SA_KEY` containing the key JSON, and
> replace the `workload_identity_provider` / `service_account` inputs in the workflow's
> *"Authenticate to Google Cloud"* step with `credentials_json: ${{ secrets.GCP_SA_KEY }}`.

---

## One-time GCP setup

### 1 — Enable APIs
```bash
gcloud services enable \
  artifactregistry.googleapis.com \
  run.googleapis.com \
  iamcredentials.googleapis.com \
  --project "$PROJECT_ID"
```

### 2 — Create an Artifact Registry Docker repository (if it doesn't exist)
```bash
gcloud artifacts repositories create ai-scheduler \
  --repository-format docker \
  --location "$REGION" \
  --project "$PROJECT_ID"
```

### 3 — Create a service account for deployments
```bash
gcloud iam service-accounts create github-actions-deployer \
  --display-name "GitHub Actions deployer" \
  --project "$PROJECT_ID"

SA_EMAIL="github-actions-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

# Allow pushing images
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/artifactregistry.writer"

# Allow deploying to Cloud Run
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/run.developer"

# Allow the SA to act as itself when Cloud Run deploys
gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
  --member "serviceAccount:${SA_EMAIL}" \
  --role "roles/iam.serviceAccountUser" \
  --project "$PROJECT_ID"
```

### 4 — Set up Workload Identity Federation (recommended)
```bash
REPO="UcaQuant/AI_SCHEDULER-back_end"

# Create the pool
gcloud iam workload-identity-pools create "github-pool" \
  --location global \
  --project "$PROJECT_ID"

# Create the OIDC provider
gcloud iam workload-identity-pools providers create-oidc "github-provider" \
  --location global \
  --workload-identity-pool "github-pool" \
  --issuer-uri "https://token.actions.githubusercontent.com" \
  --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --project "$PROJECT_ID"

# Allow the specific GitHub repo to impersonate the SA
POOL_ID=$(gcloud iam workload-identity-pools describe github-pool \
  --location global \
  --project "$PROJECT_ID" \
  --format "value(name)")

gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
  --role "roles/iam.workloadIdentityUser" \
  --member "principalSet://iam.googleapis.com/${POOL_ID}/attribute.repository/${REPO}" \
  --project "$PROJECT_ID"

# Print the values to save as GitHub secrets
echo "WIF_PROVIDER  = ${POOL_ID}/providers/github-provider"
echo "WIF_SERVICE_ACCOUNT = ${SA_EMAIL}"
```

Save the two printed values as the GitHub secrets `WIF_PROVIDER` and `WIF_SERVICE_ACCOUNT`.
