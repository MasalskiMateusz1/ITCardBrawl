# Produkcja — runbook

Ręczna, odtwarzalna instrukcja postawienia serwera produkcyjnego ItCardBrawl. Architektura: jeden VPS z Docker Compose — kontener `app` (Spring Boot z SPA w jarze, obraz z GHCR) za Caddy (TLS z Let's Encrypt). Pliki w tym katalogu:

| Plik | Rola |
| --- | --- |
| `compose.yaml` | usługi `app` (bez publikowanych portów) i `caddy` (80/443) |
| `Caddyfile` | `{$APP_DOMAIN}` → `reverse_proxy app:8080` |
| `deploy.sh` | wdrożenie tagu z automatycznym powrotem do poprzedniej wersji; `rollback` |
| `logrotate-docker` | rotacja logów kontenerów: codziennie, 30 dni (instalowany ręcznie w `/etc/logrotate.d/`) |

Na serwerze leżą w `/opt/itcardbrawl` razem z plikami spoza repo: `app.env` (sekrety), `.env` (`APP_DOMAIN`, `APP_TAG`) i `.previous_tag` (stan `deploy.sh`). Te trzy są git-ignorowane i nigdy nie trafiają do repo.

## 1. VPS

1. Wybierz VPS z **Ubuntu LTS** (np. 24.04) i **co najmniej 1 GB RAM** (JVM w kontenerze; obraz nie jest budowany na serwerze). Zapisz publiczny adres IP.
2. Zaloguj się jako `root` (albo użytkownik z `sudo`) i zaktualizuj system:

   ```bash
   apt update && apt -y full-upgrade
   apt -y install unattended-upgrades
   dpkg-reconfigure -plow unattended-upgrades
   ```

3. Firewall — tylko SSH, HTTP, HTTPS (HTTP/3 to UDP 443):

   ```bash
   ufw allow 22/tcp
   ufw allow 80/tcp
   ufw allow 443/tcp
   ufw allow 443/udp
   ufw enable
   ```

   Uwaga: porty publikowane przez Dockera omijają reguły `ufw` — dlatego `compose.yaml` publikuje wyłącznie porty Caddy, a `app` jest dostępny tylko w sieci compose.

4. Docker Engine + compose plugin z oficjalnego repozytorium Dockera (https://docs.docker.com/engine/install/ubuntu/):

   ```bash
   apt -y install ca-certificates curl
   install -m 0755 -d /etc/apt/keyrings
   curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
   echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
   apt update && apt -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
   docker compose version
   ```

   Rotacja logów kontenerów — codziennie, trzymane 30 dni wstecz. Docker sam rotuje logi tylko po rozmiarze, więc robi to `logrotate` hosta (plik `logrotate-docker` z tego katalogu; `copytruncate`, bo Docker trzyma plik logu otwarty):

   ```bash
   # skopiuj deploy/logrotate-docker na serwer, potem jako root:
   install -m 644 logrotate-docker /etc/logrotate.d/docker-containers
   logrotate --debug /etc/logrotate.d/docker-containers   # sprawdzenie skladni (na sucho)
   ```

   `logrotate.timer` w Ubuntu uruchamia rotację raz dziennie.

5. Użytkownik `deploy` (logowanie wyłącznie kluczem SSH, w grupie `docker`). Na swoim komputerze wygeneruj osobny klucz do wdrożeń (bez hasła — użyje go GitHub Actions):

   ```bash
   ssh-keygen -t ed25519 -f ~/.ssh/itcardbrawl_deploy -N "" -C "itcardbrawl-deploy"
   ```

   Na serwerze:

   ```bash
   adduser --disabled-password --gecos "" deploy
   usermod -aG docker deploy
   install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
   # wklej zawartosc ~/.ssh/itcardbrawl_deploy.pub:
   nano /home/deploy/.ssh/authorized_keys
   chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
   ```

   Sprawdź `ssh -i ~/.ssh/itcardbrawl_deploy deploy@<IP>`, potem wyłącz logowanie hasłem w `/etc/ssh/sshd_config` (`PasswordAuthentication no`) i `systemctl restart ssh`.

   Grupa `docker` daje uprawnienia równoważne `root` — klucz `deploy` chroń jak klucz administratora.

6. Katalog aplikacji:

   ```bash
   install -d -m 750 -o deploy -g deploy /opt/itcardbrawl
   ```

## 2. Domena

U rejestratora / w DNS domeny dodaj **rekord A** wskazujący na IP serwera (np. `gra.example.com → 203.0.113.10`; rekord AAAA tylko, jeśli serwer ma działający IPv6). Sprawdź propagację: `dig +short gra.example.com`. Caddy uzyska certyfikat dopiero, gdy domena wskazuje na serwer, a porty 80/443 są otwarte.

## 3. Produkcyjne aplikacje OAuth

Produkcja ma **osobne** aplikacje OAuth (aplikacje deweloperskie z `.env.properties` mają callbacki `localhost:4200` i zostają bez zmian).

- **GitHub**: Settings → Developer settings → OAuth Apps → New OAuth App.
  - Homepage URL: `https://<domena>/`
  - Authorization callback URL: `https://<domena>/login/oauth2/code/github`
  - Zapisz Client ID, wygeneruj Client secret.
- **Google**: Google Cloud Console → APIs & Services → Credentials → Create credentials → OAuth client ID → Web application.
  - Authorized redirect URIs: `https://<domena>/login/oauth2/code/google`
  - Na ekranie zgody OAuth (OAuth consent screen) dodaj domenę do autoryzowanych domen; w trybie „Testing” zaloguje się tylko lista użytkowników testowych.
  - Zapisz Client ID i Client secret.

## 4. Konfiguracja na serwerze

Jako `deploy` w `/opt/itcardbrawl`:

```bash
cd /opt/itcardbrawl
cat > app.env <<'EOF'
SPRING_PROFILES_ACTIVE=prod
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
EOF
chmod 600 app.env

echo "APP_DOMAIN=gra.example.com" > .env
```

- `app.env` — zmienne środowiskowe kontenera `app`; profil `prod` włącza obsługę `X-Forwarded-*` i cookie `Secure` (`src/main/resources/application-prod.properties`).
- `.env` — zmienne podstawiane w `compose.yaml`: `APP_DOMAIN` ustawiasz ręcznie, `APP_TAG` dopisuje i zmienia `deploy.sh` (nie edytuj go ręcznie poza awarią).

Skopiuj pliki z repo (z katalogu głównego repozytorium na swoim komputerze; w fazie 5 robi to CI):

```bash
scp -i ~/.ssh/itcardbrawl_deploy deploy/compose.yaml deploy/Caddyfile deploy/deploy.sh deploy@<IP>:/opt/itcardbrawl/
ssh -i ~/.ssh/itcardbrawl_deploy deploy@<IP> chmod +x /opt/itcardbrawl/deploy.sh
```

## 5. Sekrety i zmienne GitHuba (dla CD)

Repo → Settings → Environments → **New environment `production`** (opcjonalnie: ogranicz do gałęzi `main`). W tym environment dodaj:

| Rodzaj | Nazwa | Wartość |
| --- | --- | --- |
| secret | `DEPLOY_HOST` | IP albo nazwa serwera |
| secret | `DEPLOY_USER` | `deploy` |
| secret | `DEPLOY_SSH_KEY` | zawartość prywatnego klucza `~/.ssh/itcardbrawl_deploy` |
| secret | `DEPLOY_KNOWN_HOSTS` | wynik `ssh-keyscan -t ed25519 <IP>` (zweryfikuj odcisk z `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` na serwerze) |
| variable | `APP_DOMAIN` | np. `gra.example.com` (smoke test w CI) |

Logowanie serwera do GHCR odbywa się w CI tokenem `GITHUB_TOKEN` bieżącego joba (przez stdin SSH) — na serwerze nie zapisujesz długowiecznego tokenu. Po pierwszym wypchnięciu obrazu sprawdź w ustawieniach pakietu na GitHubie (Packages → `itcardbrawl` → Package settings), że repozytorium `ITCardBrawl` ma do niego dostęp.

## 6. Wdrożenie, rollback, logi

### Pierwszy, ręczny deploy

Obraz trzeba najpierw wypchnąć do GHCR (na swoim komputerze; token klasyczny z zakresem `write:packages`):

```bash
echo "<TOKEN>" | docker login ghcr.io -u <login-github> --password-stdin
docker build -t ghcr.io/masalskimateusz1/itcardbrawl:<tag> .
docker push ghcr.io/masalskimateusz1/itcardbrawl:<tag>
```

Na serwerze (pakiet prywatny wymaga logowania; token z zakresem `read:packages`, po deployu wyloguj się):

```bash
cd /opt/itcardbrawl
echo "<TOKEN>" | docker login ghcr.io -u <login-github> --password-stdin
./deploy.sh <tag>
docker logout ghcr.io
curl -s https://<domena>/api/ping   # {"status":"ok"}
```

### Jak działa `deploy.sh`

- `./deploy.sh <tag>` (w CD: `<tag>` = SHA commita) — zapisuje bieżący `APP_TAG` w `.previous_tag`, ustawia nowy tag w `.env`, `docker compose pull app`, `docker compose up -d` i czeka do 90 s, aż kontener `app` będzie `healthy` (healthcheck obrazu na `/api/ping`). Jeśli pull (np. nieistniejący tag), start albo healthcheck zawiedzie — wraca do poprzedniego tagu i kończy się błędem. Pierwszy deploy nie ma do czego wracać: kończy się tylko błędem. Po sukcesie usuwa lokalne obrazy aplikacji poza bieżącym i poprzednim.
- `./deploy.sh rollback` — wraca do tagu z `.previous_tag` (obraz z lokalnego cache, bez logowania do GHCR) i czeka na zdrowie. `.previous_tag` się nie zmienia, więc drugi `rollback` nie wraca do wycofanej wersji. Historia ma jeden poziom: starszych wersji szukaj w GHCR i wdrażaj je przez `./deploy.sh <sha>`.

### Logi i stan

```bash
cd /opt/itcardbrawl
docker compose ps
docker compose logs -f app
docker compose logs -f caddy
cat .env .previous_tag
```

### Zmiana Caddyfile

Po skopiowaniu nowego `Caddyfile` przeładuj Caddy bez restartu kontenera:

```bash
docker compose exec -w /etc/caddy caddy caddy reload
```

CD (`.github/workflows/ci.yml`) robi ten reload po każdym udanym wdrożeniu.

### Aktualizacja Caddy

`deploy.sh` pobiera wyłącznie obraz `app`, więc Caddy (publiczny terminator TLS) aktualizujesz ręcznie. `compose.yaml` przypina linię minor (`caddy:2.11`):

- **poprawki w tej samej linii** (np. 2.11.x) — co jakiś czas, np. raz w miesiącu albo po ogłoszeniu bezpieczeństwa (https://github.com/caddyserver/caddy/releases):

  ```bash
  cd /opt/itcardbrawl
  docker compose pull caddy && docker compose up -d caddy
  docker compose logs --tail 20 caddy
  ```

- **nowa linia minor** (np. 2.12) — zmień tag w `deploy/compose.yaml` w repo i zmerguj do `main`; CD skopiuje plik na serwer, a `docker compose up -d` w `deploy.sh` pobierze brakujący obraz i odtworzy kontener Caddy (krótka przerwa).

Certyfikaty leżą w wolumenie `caddy_data`, więc odtworzenie kontenera ich nie traci.

## 7. Opcjonalnie: ochrona gałęzi `main`

Repo → Settings → Branches (albo Rules → Rulesets) → reguła dla `main`: „Require a pull request before merging” i „Require status checks to pass” z wymaganym checkiem **`test`** (job z `.github/workflows/ci.yml`). Dzięki temu na produkcję trafia tylko kod z zielonymi testami.
