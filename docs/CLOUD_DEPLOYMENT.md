# Deploying The Club on any cloud

The Club has no dependency on a particular cloud. Every building block is a standard one that each cloud sells as a
managed service, and the Helm chart ships ready-made settings for the three big ones. This guide takes you from an
empty cloud account to a running installation on **AWS**, **Google Cloud** or **Azure**, and explains the settings
that make that possible so you can use them anywhere else (another cloud, your own data centre, a single VM).

- [What the platform needs](#what-the-platform-needs)
- [Pick a shape: one VM or Kubernetes](#pick-a-shape-one-vm-or-kubernetes)
- [Steps common to every cloud](#steps-common-to-every-cloud)
- [AWS](#aws)
- [Google Cloud](#google-cloud)
- [Azure](#azure)
- [Other clouds and your own servers](#other-clouds-and-your-own-servers)
- [Reference: uploaded files](#reference-uploaded-files)
- [Reference: Redis](#reference-redis)
- [Reference: off-site backups](#reference-off-site-backups)
- [Reference: regional defaults](#reference-regional-defaults)
- [Checking an installation](#checking-an-installation)

[KUBERNETES.md](KUBERNETES.md) is the general Helm chart guide and [PRODUCTION.md](PRODUCTION.md) the single-server
(Docker Compose) guide; this one adds what is specific to each cloud.

---

## What the platform needs

| Building block | Why | AWS | Google Cloud | Azure |
|---|---|---|---|---|
| Kubernetes | runs the services | EKS | GKE | AKS |
| MySQL 8 | user, auth, club, event, notification databases | RDS for MySQL | Cloud SQL for MySQL | Azure Database for MySQL (flexible server) |
| PostgreSQL 15 | activity database | RDS for PostgreSQL | Cloud SQL for PostgreSQL | Azure Database for PostgreSQL (flexible server) |
| Object storage | photos and logos (public), proof documents (private) | S3 | Cloud Storage | Blob Storage |
| Redis (optional, recommended) | rate limits and live chat shared by several replicas | ElastiCache (Redis OSS / Valkey) | Memorystore for Redis | Azure Cache for Redis / Azure Managed Redis |
| Secret manager | keys and passwords, synced by the External Secrets Operator | Secrets Manager | Secret Manager | Key Vault |
| Load balancer + TLS | HTTPS entry point | ALB + ACM certificate | GKE Ingress + Google-managed certificate | application routing (managed NGINX) + cert-manager |
| Workload identity | storage and secret access without stored keys | IRSA or EKS Pod Identity | Workload Identity | AKS workload identity |
| Container registry (optional) | GHCR works everywhere; mirror if you prefer | ECR | Artifact Registry | ACR |

Email (ZeptoMail or any SMTP server), payments (Razorpay) and Strava are external services that work the same on
every cloud.

**Cloud-specific code is limited to object storage.** It has three implementations: S3 (which also covers
Cloudflare R2, MinIO, SeaweedFS and any S3-compatible store), Google Cloud Storage and Azure Blob Storage. Everything
else uses plain protocols (JDBC, the Redis protocol, HTTP).

---

## Pick a shape: one VM or Kubernetes

| | One VM with Docker Compose | Kubernetes with the Helm chart |
|---|---|---|
| Good for | a first launch, a few thousand members | growth, high availability, rolling updates without downtime |
| Replicas | one of each service | as many as you like, autoscaled |
| Redis | not needed | needed once the gateway or club-service runs more than one replica |
| Databases | in containers on the VM (or managed) | managed (recommended) |
| Guide | [PRODUCTION.md](PRODUCTION.md), then [the VM section below](#other-clouds-and-your-own-servers) | this guide |

---

## Steps common to every cloud

Do these on your own machine, whichever cloud you use.

1. **Tools:** `kubectl`, `helm` (3.12+ or 4) and your cloud's CLI (`aws`, `gcloud` or `az`).
2. **Images:** CI publishes `ghcr.io/guru-pj/the-club-<service>:<git sha>` on every push to `main`. Use one commit
   SHA for every service (`--set global.imageTag=<sha>`). If the packages are private, create a pull secret, as
   described in [KUBERNETES.md](KUBERNETES.md#1-prerequisites). To use your own registry instead, copy the images
   (for example with `crane copy`) and set `global.imageRegistry`.
3. **Secrets:** generate them once with `./scripts/generate-secrets.sh`. They land in `secrets/`, one file per value.
   Add the third-party ones you use (`zeptomail-token`, `razorpay-key-secret`, `razorpay-webhook-secret`,
   `strava-client-secret`) as files in the same folder. Keep the folder out of git (it is ignored already).
4. **Secret names in the cloud's secret manager:** the chart's ExternalSecret reads each key from a secret named
   `the-club-` plus the key with dots replaced by dashes, for example:

   | File in `secrets/` | Secret in the secret manager |
   |---|---|
   | `theclub.jwt.private-key` | `the-club-theclub-jwt-private-key` |
   | `theclub.security.internal-api-key` | `the-club-theclub-security-internal-api-key` |
   | `db-user-password` | `the-club-db-user-password` |

   The required keys are listed under `secrets.external.keys` in
   [`values.yaml`](../deploy/helm/the-club/values.yaml). Add optional ones (Razorpay, ZeptoMail, `redis-password`,
   …) to `secrets.external.extraKeys`; every key listed must exist, or the sync stops. The per-cloud sections below
   upload them with one loop:
   ```bash
   KEYS="theclub.jwt.private-key theclub.jwt.public-key theclub.security.internal-api-key admin-password
         db-user-password db-auth-password db-activity-password db-club-password db-event-password
         db-notification-password strava-token-encryption-key zeptomail-webhook-token
         razorpay-key-secret razorpay-webhook-secret zeptomail-token"   # plus redis-password where used
   ```
5. **Values file:** copy `deploy/helm/the-club/values-prod.yaml` (app URL, admin email, payments, mail) and the file
   for your cloud (`values-aws.yaml`, `values-gcp.yaml` or `values-azure.yaml`). Replace every `CHANGE-ME` in both.
   Install with both files, the cloud one last:
   ```bash
   helm upgrade --install the-club deploy/helm/the-club -n the-club --create-namespace \
     -f my-values-prod.yaml -f my-values-<cloud>.yaml --set global.imageTag=<git sha> --wait --timeout 15m
   ```
6. **Databases:** create one database and one least-privilege user per service with the SQL in
   [KUBERNETES.md](KUBERNETES.md#2-databases-one-database-and-least-privilege-user-per-service), using the passwords
   from `secrets/db-*-password`. Flyway creates the tables on first start.
7. **External Secrets Operator** (once per cluster):
   ```bash
   helm repo add external-secrets https://charts.external-secrets.io
   helm upgrade --install external-secrets external-secrets/external-secrets -n external-secrets --create-namespace --wait
   ```
   Each cloud section creates the `ClusterSecretStore` it points at.

Names used below: namespace `the-club` and release `the-club`, so the chart's Kubernetes service account is
`the-club` and its full name is `system:serviceaccount:the-club:the-club`. Adjust the commands if you choose other
names.

---

## AWS

Ready-made values: [`values-aws.yaml`](../deploy/helm/the-club/values-aws.yaml).

```bash
export AWS_REGION=eu-west-1 CLUSTER=the-club
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
MEDIA=the-club-media-<unique> DOCS=the-club-documents-<unique>
```

**1. Cluster.** Create EKS (for example `eksctl create cluster --name $CLUSTER --region $AWS_REGION --with-oidc`) and
install the [AWS Load Balancer Controller](https://docs.aws.amazon.com/eks/latest/userguide/aws-load-balancer-controller.html).
NetworkPolicies need the VPC CNI's network policy agent (`enableNetworkPolicy: "true"` on the `vpc-cni` add-on) or
another enforcing CNI; without it they are accepted but not enforced.

**2. Buckets.** One public-read bucket for images and one private bucket for documents:
```bash
aws s3api create-bucket --bucket $MEDIA --create-bucket-configuration LocationConstraint=$AWS_REGION
aws s3api put-public-access-block --bucket $MEDIA --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=false,RestrictPublicBuckets=false
aws s3api put-bucket-policy --bucket $MEDIA --policy "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",
  \"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"arn:aws:s3:::$MEDIA/*\"}]}"
aws s3api create-bucket --bucket $DOCS --create-bucket-configuration LocationConstraint=$AWS_REGION   # stays fully private
```
Optionally put CloudFront in front of `$MEDIA` and use its domain as `config.media.publicBaseUrl`.

**3. Identity for the services (IRSA).** A role the chart's service account assumes:
```bash
cat > the-club-s3.json <<EOF
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:DeleteObject"],
  "Resource":["arn:aws:s3:::$MEDIA/*","arn:aws:s3:::$DOCS/*"]},
 {"Effect":"Allow","Action":["s3:ListBucket"],"Resource":["arn:aws:s3:::$MEDIA","arn:aws:s3:::$DOCS"]}]}
EOF
aws iam create-policy --policy-name the-club-s3 --policy-document file://the-club-s3.json
eksctl create iamserviceaccount --cluster $CLUSTER --namespace the-club --name the-club --role-name the-club \
  --role-only --attach-policy-arn arn:aws:iam::$ACCOUNT:policy/the-club-s3 --approve
```
Put the role ARN in `serviceAccount.annotations` (`eks.amazonaws.com/role-arn`). With **EKS Pod Identity** instead,
create the association (`aws eks create-pod-identity-association --cluster-name $CLUSTER --namespace the-club
--service-account the-club --role-arn <role>`) and delete the annotation line from your values file. Either way no
access key is stored: `config.media.accessKey` stays empty.

**4. Databases.** Create RDS for MySQL 8 and RDS for PostgreSQL 15 in the cluster's VPC (private subnets, not
publicly accessible), with a security group that admits the cluster's nodes on 3306 and 5432. Run the SQL from
[step 6](#steps-common-to-every-cloud) against them, then put their endpoints in `databases.mysql.host` and
`databases.postgres.host`. `sslMode=REQUIRED` / `sslmode=require` are already set. Turn on automated backups and
Multi-AZ; RDS's point-in-time recovery replaces the chart's own dump jobs.

**5. Redis.** Create an ElastiCache (Redis OSS or Valkey) replication group in the same VPC with in-transit
encryption on (`ssl: true`). If you also set an AUTH token, store it as `the-club-redis-password` and add
`redis-password` to `secrets.external.extraKeys`. Use the primary endpoint as `redis.host` and narrow `redis.cidrs` to
its subnets.

**6. Secrets.** Upload them to Secrets Manager:
```bash
for k in $KEYS; do aws secretsmanager create-secret --name "the-club-${k//./-}" --secret-string "file://secrets/$k"; done
```
Then give the External Secrets Operator read access: an IRSA role on its `external-secrets` service account with
`secretsmanager:GetSecretValue` and `secretsmanager:DescribeSecret` on
`arn:aws:secretsmanager:$AWS_REGION:$ACCOUNT:secret:the-club-*`. Then create the store:
```yaml
apiVersion: external-secrets.io/v1
kind: ClusterSecretStore
metadata: {name: aws-secrets-manager}
spec:
  provider:
    aws:
      service: SecretsManager
      region: eu-west-1
      auth:
        jwt:
          serviceAccountRef: {name: external-secrets, namespace: external-secrets}
```

**7. Certificate and DNS.** Request an ACM certificate for your host in the same region, and put its ARN in the
`alb.ingress.kubernetes.io/certificate-arn` annotation. After installing, point DNS at the ALB:
`kubectl -n the-club get ingress the-club -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'` (a CNAME, or a
Route 53 alias record). The ALB keeps idle connections for 120 s, which live chat needs. AWS WAF can be attached
with the commented annotation.

**8. Install** with the command from [step 5](#steps-common-to-every-cloud), then [check it](#checking-an-installation).

---

## Google Cloud

Ready-made values: [`values-gcp.yaml`](../deploy/helm/the-club/values-gcp.yaml).

```bash
export PROJECT=my-project REGION=asia-south1
MEDIA=the-club-media-<unique> DOCS=the-club-documents-<unique>
GSA=the-club@$PROJECT.iam.gserviceaccount.com
```

**1. Cluster.** GKE Autopilot (`gcloud container clusters create-auto the-club --region $REGION`) has Workload
Identity and NetworkPolicy enforcement built in. For a Standard cluster, add `--workload-pool=$PROJECT.svc.id.goog`
and `--enable-dataplane-v2`.

**2. Buckets.**
```bash
gcloud storage buckets create gs://$MEDIA --location=$REGION --uniform-bucket-level-access
gcloud storage buckets add-iam-policy-binding gs://$MEDIA --member=allUsers --role=roles/storage.objectViewer
gcloud storage buckets create gs://$DOCS --location=$REGION --uniform-bucket-level-access --public-access-prevention
```
`config.media.publicBaseUrl` is `https://storage.googleapis.com/$MEDIA`, or a Cloud CDN host in front of it.

**3. Identity for the services (Workload Identity).**
```bash
gcloud iam service-accounts create the-club
for b in $MEDIA $DOCS; do
  gcloud storage buckets add-iam-policy-binding gs://$b --member=serviceAccount:$GSA --role=roles/storage.objectUser
done
gcloud iam service-accounts add-iam-policy-binding $GSA --role=roles/iam.workloadIdentityUser \
  --member="serviceAccount:$PROJECT.svc.id.goog[the-club/the-club]"
```
`serviceAccount.annotations` carries `iam.gke.io/gcp-service-account: $GSA`. The pods get their tokens from the GKE
metadata server, which `networkPolicy.metadataServer` lets them reach.

**4. Databases.** Set up
[private services access](https://cloud.google.com/sql/docs/mysql/configure-private-services-access) on the VPC,
then:
```bash
gcloud sql instances create the-club-mysql --database-version=MYSQL_8_0 --region=$REGION --tier=db-custom-2-7680 \
  --network=default --no-assign-ip --ssl-mode=ENCRYPTED_ONLY --availability-type=REGIONAL --enable-bin-log
gcloud sql instances create the-club-postgres --database-version=POSTGRES_15 --region=$REGION --tier=db-custom-1-3840 \
  --network=default --no-assign-ip --ssl-mode=ENCRYPTED_ONLY --availability-type=REGIONAL --enable-point-in-time-recovery
```
Run the SQL from [step 6](#steps-common-to-every-cloud), and put the private IPs in `databases.*.host` and the
private services range in `networkPolicy.databaseCidrs`.

**5. Redis.**
```bash
gcloud redis instances create the-club --size=1 --region=$REGION --network=default --enable-auth --tier=standard
gcloud redis instances get-auth-string the-club --region=$REGION > secrets/redis-password
gcloud redis instances describe the-club --region=$REGION --format='value(host)'   # redis.host
```
With `--transit-encryption-mode=server-authentication`, use port 6378 and `ssl: true`.

**6. Secrets.**
```bash
for k in $KEYS redis-password; do gcloud secrets create "the-club-${k//./-}" --data-file="secrets/$k"; done
gcloud iam service-accounts create external-secrets
gcloud projects add-iam-policy-binding $PROJECT --role=roles/secretmanager.secretAccessor \
  --member=serviceAccount:external-secrets@$PROJECT.iam.gserviceaccount.com
gcloud iam service-accounts add-iam-policy-binding external-secrets@$PROJECT.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser --member="serviceAccount:$PROJECT.svc.id.goog[external-secrets/external-secrets]"
kubectl -n external-secrets annotate serviceaccount external-secrets \
  iam.gke.io/gcp-service-account=external-secrets@$PROJECT.iam.gserviceaccount.com
kubectl -n external-secrets rollout restart deploy external-secrets
```
```yaml
apiVersion: external-secrets.io/v1
kind: ClusterSecretStore
metadata: {name: gcp-secret-manager}
spec:
  provider:
    gcpsm: {projectID: my-project}
```

**7. Address, certificate and DNS.** Reserve the address the Ingress uses and point DNS at it before installing:
```bash
gcloud compute addresses create the-club-ip --global
gcloud compute addresses describe the-club-ip --global --format='value(address)'   # an A record for your host
```
`values-gcp.yaml` creates the Google-managed certificate, an HTTP→HTTPS redirect and a backend timeout of 900 s (the
default 30 s would end live chat streams every 30 s) through `extraObjects`. Put your host in the ManagedCertificate
there too. The certificate becomes active once DNS resolves to the address, which can take up to an hour.

**8. Install** with the command from [step 5](#steps-common-to-every-cloud), then [check it](#checking-an-installation).

---

## Azure

Ready-made values: [`values-azure.yaml`](../deploy/helm/the-club/values-azure.yaml).

```bash
RG=the-club LOC=centralindia ACCOUNT=theclubmedia<unique> KV=the-club-kv-<unique>
az group create -n $RG -l $LOC
```

**1. Cluster.** Workload identity, the OIDC issuer, the application routing add-on and NetworkPolicy enforcement:
```bash
az aks create -g $RG -n the-club --enable-oidc-issuer --enable-workload-identity --enable-app-routing \
  --network-plugin azure --network-policy azure --generate-ssh-keys
az aks get-credentials -g $RG -n the-club
```
Install [cert-manager](https://cert-manager.io/docs/installation/helm/) and a `ClusterIssuer` named
`letsencrypt-prod` whose HTTP-01 solver uses the ingress class `webapprouting.kubernetes.azure.com`.

**2. Storage.** One account with a public-read container for images and a private one for documents:
```bash
az storage account create -n $ACCOUNT -g $RG -l $LOC --sku Standard_ZRS --allow-blob-public-access true
az storage container create --account-name $ACCOUNT -n media --public-access blob --auth-mode login
az storage container create --account-name $ACCOUNT -n documents --public-access off --auth-mode login
```
`config.media.endpoint` is `https://$ACCOUNT.blob.core.windows.net`, and `publicBaseUrl` adds `/media` (or use an
Azure Front Door host).

**3. Identity for the services (workload identity).**
```bash
az identity create -g $RG -n the-club
CLIENT_ID=$(az identity show -g $RG -n the-club --query clientId -o tsv)
az role assignment create --assignee $CLIENT_ID --role "Storage Blob Data Contributor" \
  --scope $(az storage account show -n $ACCOUNT -g $RG --query id -o tsv)
az identity federated-credential create -g $RG --identity-name the-club --name the-club \
  --issuer $(az aks show -g $RG -n the-club --query oidcIssuerProfile.issuerUrl -o tsv) \
  --subject system:serviceaccount:the-club:the-club --audience api://AzureADTokenExchange
```
Put `$CLIENT_ID` in `serviceAccount.annotations` (`azure.workload.identity/client-id`); the
`azure.workload.identity/use` pod label is already in the values file. Without workload identity you can store the
account's connection string as `media-azure-connection-string` instead.

**4. Databases.** Azure Database for MySQL (flexible server, 8.0) and for PostgreSQL (flexible server, 15) with
private access in the cluster's VNet (or a peered one):
```bash
az mysql flexible-server create -g $RG -n the-club-mysql --version 8.0.21 --tier GeneralPurpose \
  --sku-name Standard_D2ds_v4 --high-availability ZoneRedundant --vnet the-club-vnet --subnet mysql
az postgres flexible-server create -g $RG -n the-club-postgres --version 15 --tier GeneralPurpose \
  --sku-name Standard_D2ds_v4 --high-availability ZoneRedundant --vnet the-club-vnet --subnet postgres
```
Both require TLS by default. Run the SQL from [step 6](#steps-common-to-every-cloud) as the server admin.

**5. Redis.** Azure Cache for Redis (`az redis create -g $RG -n the-club -l $LOC --sku Standard --vm-size c1`)
listens with TLS on port 6380, as set in the values file. Store its primary access key as
`the-club-redis-password`: `az redis list-keys -g $RG -n the-club --query primaryKey -o tsv > secrets/redis-password`.
Microsoft is retiring Azure Cache for Redis in favour of **Azure Managed Redis**. With Managed Redis, use port
10000 (TLS) and its access key the same way.

**6. Secrets.**
```bash
az keyvault create -g $RG -n $KV --enable-rbac-authorization true
az role assignment create --assignee $(az ad signed-in-user show --query id -o tsv) --role "Key Vault Secrets Officer" \
  --scope $(az keyvault show -n $KV --query id -o tsv)
for k in $KEYS redis-password; do az keyvault secret set --vault-name $KV -n "the-club-${k//./-}" --file "secrets/$k"; done
# the operator's identity
az identity create -g $RG -n external-secrets
ESO_ID=$(az identity show -g $RG -n external-secrets --query clientId -o tsv)
az role assignment create --assignee $ESO_ID --role "Key Vault Secrets User" --scope $(az keyvault show -n $KV --query id -o tsv)
az identity federated-credential create -g $RG --identity-name external-secrets --name external-secrets \
  --issuer $(az aks show -g $RG -n the-club --query oidcIssuerProfile.issuerUrl -o tsv) \
  --subject system:serviceaccount:external-secrets:external-secrets --audience api://AzureADTokenExchange
kubectl -n external-secrets annotate serviceaccount external-secrets azure.workload.identity/client-id=$ESO_ID
```
```yaml
apiVersion: external-secrets.io/v1
kind: ClusterSecretStore
metadata: {name: azure-key-vault}
spec:
  provider:
    azurekv:
      vaultUrl: https://the-club-kv-<unique>.vault.azure.net
      authType: WorkloadIdentity
      serviceAccountRef: {name: external-secrets, namespace: external-secrets}
```

**7. DNS.** After installing, point your host at the routing add-on's address:
`kubectl -n app-routing-system get svc nginx -o jsonpath='{.status.loadBalancer.ingress[0].ip}'`. cert-manager then
issues the certificate into `the-club-tls`. The values file raises the upload limit to 11 MB and turns off response
buffering, so uploads and live chat work.

**8. Install** with the command from [step 5](#steps-common-to-every-cloud), then [check it](#checking-an-installation).

---

## Other clouds and your own servers

**Another Kubernetes provider** (DigitalOcean, Linode, OVH, Scaleway, OpenShift, an on-premises cluster): start from
`values-prod.yaml`.
- **Storage:** most providers sell S3-compatible object storage. Use `provider: s3` with its `endpoint`, `region`,
  `accessKey` and the `media-s3-secret-key` secret. Cloudflare R2 works from anywhere. To run storage inside the
  cluster, point `mediaUpstream` at it.
- **Redis:** set `redis.enabled: true` to run the chart's own Redis (`redis.embedded`), or point `redis.host` at a
  managed one.
- **Secrets:** create the Secret with `kubectl create secret generic --from-file=secrets/`, or use any External
  Secrets provider (Vault, 1Password, Doppler, …) with `secrets.external`.

**A single VM on any cloud (Docker Compose).** Follow [PRODUCTION.md](PRODUCTION.md). In `.env`, the media variables
keep their S3 names for every provider:

| Storage | `.env` |
|---|---|
| Bundled SeaweedFS (default) | nothing to change |
| AWS S3 with the VM's instance role | `MEDIA_S3_ENDPOINT=` (empty), `MEDIA_S3_ACCESS_KEY=` (empty), `MEDIA_S3_REGION`, `MEDIA_S3_BUCKET`, `MEDIA_PRIVATE_BUCKET`, `MEDIA_S3_PATH_STYLE=false`, `MEDIA_PUBLIC_BASE_URL` |
| Any S3-compatible store with keys (R2, …) | endpoint, bucket and access key, plus the secret key in `secrets/media-s3-secret-key` ([PRODUCTION.md §6a](PRODUCTION.md#6a-uploaded-images)) |
| Google Cloud Storage with the VM's service account | `MEDIA_PROVIDER=gcs`, `MEDIA_S3_ENDPOINT=` (empty), `MEDIA_S3_BUCKET`, `MEDIA_PRIVATE_BUCKET`, `MEDIA_GCS_PROJECT_ID`, `MEDIA_PUBLIC_BASE_URL` |
| Azure Blob with the VM's managed identity | `MEDIA_PROVIDER=azure`, `MEDIA_S3_ENDPOINT=https://<account>.blob.core.windows.net`, `MEDIA_S3_BUCKET=media`, `MEDIA_PRIVATE_BUCKET=documents`, `MEDIA_PUBLIC_BASE_URL` |

On EC2, containers reach the instance role only when the metadata hop limit allows it:
`aws ec2 modify-instance-metadata-options --instance-id <id> --http-put-response-hop-limit 2 --http-tokens required`.
A single VM doesn't need Redis. Turn on the off-site backup copy described below.

---

## Reference: uploaded files

| Setting | Helm (`config.media.*`) | Environment | Notes |
|---|---|---|---|
| Provider | `provider` | `MEDIA_PROVIDER` | `s3` (default), `gcs`, `azure` |
| Endpoint | `endpoint` | `MEDIA_S3_ENDPOINT` | S3: empty for AWS, else the store's URL. GCS: empty (an emulator URL in tests). Azure: `https://<account>.blob.core.windows.net` |
| Images bucket | `bucket` | `MEDIA_S3_BUCKET` | publicly readable; an Azure container name |
| Documents bucket | `privateBucket` | `MEDIA_PRIVATE_BUCKET` | never public. Empty: documents go under `private/` in the images bucket |
| Region | `region` | `MEDIA_S3_REGION` | S3 only |
| Access key | `accessKey` + Secret `media-s3-secret-key` | `MEDIA_S3_ACCESS_KEY`, `MEDIA_S3_SECRET_KEY` | S3 only; empty means the cloud's own identity |
| Azure connection string | Secret `media-azure-connection-string` | `MEDIA_AZURE_CONNECTION_STRING` | Azure only; instead of workload identity |
| GCS project | `gcsProjectId` | `MEDIA_GCS_PROJECT_ID` | optional |
| Path-style URLs | `pathStyle` | `MEDIA_S3_PATH_STYLE` | `true` for SeaweedFS/MinIO |
| Public URL | `publicBaseUrl` | `MEDIA_PUBLIC_BASE_URL` | what stored image URLs start with: the bucket's public URL or a CDN |
| Size limit | `maxSize` | `MEDIA_MAX_SIZE` | 5 MB |

- **Startup log:** each storage service logs `Uploads go to <provider> bucket <name> (private documents: <name>)`
  at startup. If no bucket is configured, uploads are refused with a clear message (503) and everything else keeps
  working.
- **Documents bucket:** keep it separate in production. Without one, proof documents are protected only by their
  random names and the `/media/private/` block in nginx.
- **Missing buckets:** S3 and Azure create a missing bucket or container on the first upload, if the identity is
  allowed to. Create them yourself anyway, to control their access settings.

---

## Reference: Redis

One switch, `redis.enabled` in Helm (`REDIS_ENABLED`), turns on both uses:

| Use | Without Redis | With Redis |
|---|---|---|
| Gateway rate limits (`RATE_LIMIT_STORE`) | counted separately on each gateway replica, so the real limit is the setting × replicas | one count shared by all replicas |
| Live chat (`CHAT_FANOUT`) | messages written on another club-service replica arrive within ~2 s (the database is polled) | instant across replicas (pub/sub on `theclub:chat`); the database check stays on every 15 s as a safety net |

- **Connection settings:** `redis.host`, `redis.port`, `redis.ssl`, and the password from the `redis-password`
  secret (`REDIS_HOST`, `REDIS_PORT`, `REDIS_SSL`, `REDIS_PASSWORD`). With `redis.embedded.enabled` the chart runs a
  small Redis without persistence, which is enough because nothing in it needs to survive a restart.
- **If Redis is down:**
  - The rate limiter lets requests through (fail-open) rather than blocking everyone, and counts each error in the
    metric `theclub_gateway_rate_limit_errors_total`.
  - Chat falls back to delivering on the local replica, and the database check delivers the rest.
  - Both services report Redis in `/actuator/health` only when it is enabled.
- **Docker Compose:** no Redis by default. One replica of each service doesn't need it.

---

## Reference: off-site backups

The chart's nightly dumps (`backups.enabled`) and the Compose `db-backup-*` containers cover databases you run
yourself. Managed databases (RDS, Cloud SQL, Azure Database) have their own backups and point-in-time recovery, so
there they aren't needed. Dumps kept on the same disk as the database don't survive losing that disk, so copy them
off-site. A small rclone container copies new dumps every hour (`intervalSeconds`, default 3600) to any object
store. The bucket's lifecycle rules decide how long copies are kept there.

| Where | Turn it on |
|---|---|
| Helm | `backups.offsite.enabled: true`, `backups.offsite.target: "<rclone target>"`. Credentials come from the chart's service account (`env_auth=true`), or from Secret keys mapped in `backups.offsite.secretEnv` |
| Docker Compose | `BACKUP_OFFSITE_TARGET=<rclone target>` (plus `RCLONE_S3_ACCESS_KEY_ID` / `RCLONE_S3_SECRET_ACCESS_KEY` or `RCLONE_AZUREBLOB_KEY`) in `.env`, then `docker compose --profile offsite up -d backup-offsite` |

Targets (quote values that contain `:`):
```text
AWS S3:        :s3,provider=AWS,env_auth=true,region=eu-west-1:my-backups/the-club
Cloud Storage: :gcs,env_auth=true,bucket_policy_only=true:my-backups/the-club
Azure Blob:    :azureblob,account=mystorageacct,env_auth=true:backups/the-club
Cloudflare R2: :s3,provider=Cloudflare,endpoint='https://<account>.r2.cloudflarestorage.com':my-backups/the-club
```
- **Permissions:** the identity needs write and list access to the backup bucket. Give it no delete permission, and
  turn on object versioning or object lock, so a compromised cluster can't wipe the copies.
- **Checking:** the container logs `dumps copied off-site` (or `ERROR: …`) after each run, without printing the
  target. Restore from a copy now and then, following [PRODUCTION.md §7](PRODUCTION.md#7-backups-and-restore).

---

## Reference: regional defaults

| Setting | Helm | Environment | Effect |
|---|---|---|---|
| Time zone | `config.timeZone` | `APP_TIME_ZONE` | event times, schedules and timestamps (every JVM starts with this zone) |
| Currency | `config.events.defaultCurrency` | `EVENTS_DEFAULT_CURRENCY` | currency of new events (ISO 4217, e.g. `USD`); the web app reads it from `GET /api/v1/events/defaults` |

Existing events keep their currency. Razorpay settles in INR by default; for other currencies, turn on
international payments in your Razorpay account, or plug another provider into the `PaymentGateway` interface.

---

## Checking an installation

```bash
kubectl -n the-club get pods                                           # all Running and Ready
kubectl -n the-club get externalsecret                                 # SecretSynced = True
kubectl -n the-club logs deploy/the-club-user | grep "Uploads go to"   # the provider and buckets you expect
kubectl -n the-club port-forward svc/the-club-gateway 18080:8080 &
GATEWAY_URL=http://localhost:18080 STRAVA_WEBHOOK_VERIFY_TOKEN=<token> ADMIN_PASSWORD=<admin password> ./scripts/smoke-test.sh
```
Then, in the browser at your host:
1. Sign in as the admin and change the password.
2. Upload a profile photo. The image should load from your bucket's public URL.
3. Submit a club application with a document. The document's URL must not open without signing in.
4. With Redis and several club-service replicas, open a club chat in two browsers. Messages should appear instantly
   in both.

| Symptom | Likely cause |
|---|---|
| `ExternalSecret` not synced | a key listed in `keys`/`extraKeys` doesn't exist in the secret manager, or the operator's identity can't read it (`kubectl describe externalsecret`) |
| uploads answer 503 | no bucket configured; look for the startup line above |
| uploads answer 500, `AccessDenied` / `403` in the logs | the workload identity isn't wired: check the service account annotation, the role or IAM binding, and (on GKE) `networkPolicy.metadataServer` |
| images upload but don't display | `publicBaseUrl` is wrong, or the images bucket isn't publicly readable |
| live chat reconnects every 30–60 s | the load balancer's idle or backend timeout is too short (GKE BackendConfig, ALB idle timeout, NGINX read timeout) |
| `/actuator/health` shows `redis` DOWN, `theclub_gateway_rate_limit_errors_total` rising, or chat arriving only after ~15 s | Redis isn't reachable (pods stay Ready, because Redis isn't part of readiness): check `redis.host`, `redis.ssl`/port, `redis.cidrs` and the `redis-password` secret |
