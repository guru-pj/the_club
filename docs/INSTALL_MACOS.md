# The Club — Installation Guide for macOS

This guide takes a Mac from nothing installed to The Club running locally at http://localhost:3000.
It works on Apple Silicon (M1–M4) and Intel Macs. Every Docker image the project uses has native `arm64`
builds, so Rosetta is not needed.

> For a production server, follow [`PRODUCTION.md`](PRODUCTION.md); production should run on Linux.

---

## 0. What you need

| Requirement | Minimum | Recommended |
|---|---|---|
| macOS | 13 Ventura | 14 Sonoma or newer |
| Chip | Apple Silicon or Intel | Apple Silicon |
| Memory (RAM) | 8 GB (Docker gets 6 GB) | 16 GB (Docker gets 8 GB) |
| Free disk | 20 GB | 40 GB |
| Internet | Needed for the first build (Maven, npm and Docker images, about 3 GB) | |

**Software installed by this guide:**

| Tool | Version | Why |
|---|---|---|
| Xcode Command Line Tools | latest | git, compilers used by Homebrew |
| Homebrew | latest | installs everything else |
| Temurin JDK | **21** | compiles the Java services (a JRE is not enough) |
| Maven | 3.9+ | builds the services |
| Docker Desktop (includes Compose v2) | 4.30+ | runs every container |
| jq | any | used by the smoke test |
| openssl | built-in LibreSSL works; OpenSSL 3 optional | generates keys and passwords |
| Node.js 22 + Chrome | optional | only for web-app development and browser tests |

---

## 1. Xcode Command Line Tools

Open **Terminal** (Applications → Utilities → Terminal) and run:

```bash
xcode-select --install
```

Click **Install** in the dialog and wait for it to finish. If it says the tools are already installed, continue.

## 2. Homebrew

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

**Apple Silicon only:** add Homebrew to your shell. The installer prints these lines at the end:

```bash
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
```

Check it:

```bash
brew --version
```

## 3. Java 21 (Temurin JDK)

```bash
brew install --cask temurin@21
```

Make Java 21 the default for every new terminal:

```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

Check both:

```bash
java -version     # openjdk version "21.x" ... Temurin
javac -version    # javac 21.x
```

> Other Java versions on the Mac are fine. `JAVA_HOME` decides which one Maven uses.

## 4. Maven, jq and OpenSSL

```bash
brew install maven jq openssl@3
```

Homebrew's Maven may pull in a newer OpenJDK as a dependency. That's fine: because `JAVA_HOME` points at Java 21,
Maven builds with 21. Confirm it:

```bash
mvn -v            # Apache Maven 3.9.x ... Java version: 21.x
jq --version
openssl version   # LibreSSL 3.x or OpenSSL 3.x — either works
```

## 5. Docker Desktop

1. Install it:
   ```bash
   brew install --cask docker
   ```
   Or download it from https://www.docker.com/products/docker-desktop/. Pick **Apple chip** or **Intel chip**
   to match **Apple menu → About This Mac**.
2. Open **Docker** from Applications, accept the terms and wait until the whale icon in the menu bar says
   **Docker Desktop is running**.
3. Give it enough resources: **Settings (⚙) → Resources → Advanced**:
   - **CPUs:** 4 or more
   - **Memory:** 6 GB minimum, 8 GB recommended
   - **Swap:** 1–2 GB
   - **Virtual disk limit:** 40 GB or more

   Click **Apply & restart**.
4. Leave file sharing at its default. The project must live under your home folder (`/Users/<you>/…`), which
   Docker Desktop shares automatically; the secrets are mounted from there.

Check it:

```bash
docker --version
docker compose version        # Docker Compose version v2.x
docker run --rm hello-world   # prints "Hello from Docker!"
```

> **Alternatives to Docker Desktop:** OrbStack (`brew install --cask orbstack`) or Colima
> (`brew install colima docker docker-compose && colima start --cpu 4 --memory 8 --disk 40`) also work,
> as long as `docker compose version` prints v2.

## 6. Optional: Node.js and Chrome (web-app development only)

Skip this step if you only want to run the app. The web app is built inside Docker.

```bash
brew install node@22
echo 'export PATH="$(brew --prefix node@22)/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
node -v           # v22.x
npm -v

brew install --cask google-chrome   # for `npm run e2e`; detected automatically on macOS
```

## 7. Check everything at once

```bash
git --version && java -version && javac -version && mvn -v \
  && docker compose version && jq --version && openssl version
```

Every command must succeed. `javac` must report 21, and `mvn -v` must report `Java version: 21`.

---

## 8. Get the code and start The Club

```bash
cd ~
git clone https://github.com/guru-pj/the_club.git
cd the_club
./project-setup.sh up
```

The first run takes 5–15 minutes. It:

1. Creates `.env` from `enviroinment_variables.env`.
2. Generates `secrets/`: the token signing keys, database and service passwords, and the admin password, which
   is printed once.
3. Builds every service with Maven.
4. Builds the Docker images and starts all containers, then streams the logs. Press **Ctrl+C** to stop watching;
   the app keeps running.

If you see `Permission denied`, run `chmod +x project-setup.sh scripts/*.sh` and try again.

## 9. Open the app

Give it a minute after the containers start, then open:

| URL | What |
|---|---|
| http://localhost:3000 | **The Club web app** |
| http://localhost:8025 | Mailpit: every email the app sends (tickets, password resets) |
| http://localhost:8761 | Eureka: all 7 services should be listed |

Log in as the admin with `admin@theclub.local` and this password:

```bash
cat secrets/admin-password
```

The [User Guide in the README](../README.md#-user-guide) walks through the admin, club owner and member flows.
Optionally, run the automated end-to-end check:

```bash
./scripts/smoke-test.sh          # ends with: ALL PASSED
```

## 10. Everyday commands

```bash
docker compose ps                         # status of every container
docker compose logs -f event-service      # follow one service's log
docker compose stop                       # pause everything (data kept)
docker compose up -d                      # start again
docker compose down                       # remove containers (data kept in volumes)
docker compose down -v                    # remove containers AND databases (fresh start)

# after `git pull`
mvn clean install -DskipTests && docker compose up -d --build
```

---

## Troubleshooting on macOS

| Problem | Fix |
|---|---|
| `xcrun: error: invalid active developer path` | Run `xcode-select --install`. This is common after a macOS upgrade. |
| `brew: command not found` (Apple Silicon) | Add Homebrew to your shell (step 2), then open a new terminal. |
| `release version 21 not supported` or `mvn -v` shows another Java | `JAVA_HOME` isn't set to 21. Redo step 3 and open a new terminal. |
| `Cannot connect to the Docker daemon` | Docker Desktop isn't running. Open it and wait for "running". |
| Containers restart or exit with code 137 | Docker is out of memory. Raise it to 8 GB (step 5) and run `docker compose up -d`. |
| `port is already allocated` | Something on the Mac already uses that port, often a Homebrew MySQL or PostgreSQL. Stop it with `brew services stop mysql` / `brew services stop postgresql@15`, or change the port in `.env`. |
| `mounts denied` / secrets not found | Keep the project under `/Users/<you>/` and run commands from the project folder. |
| `no matching manifest for linux/arm64` | Shouldn't happen, since every image is multi-arch. Update Docker Desktop. As a last resort, add `platform: linux/amd64` to that service in `docker-compose.override.yml`. |
| `Permission denied` on a script | `chmod +x project-setup.sh scripts/*.sh` |
| Very slow first build | Maven and Docker download about 3 GB the first time; later builds are fast. |
| Login fails right after a fresh start | The services are still starting. Wait for `docker compose ps` to show every service as `healthy`. |

## Uninstall

```bash
cd ~/the_club && docker compose down -v      # remove containers, databases and volumes
cd ~ && rm -rf the_club                      # remove the code and secrets
# optionally: brew uninstall --cask docker temurin@21 && brew uninstall maven jq openssl@3 node@22
```
