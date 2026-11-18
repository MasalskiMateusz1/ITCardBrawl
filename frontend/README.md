# ItCardBrawl — frontend

SPA w Angularze. W trybie deweloperskim dev server (`localhost:4200`) przekazuje żądania `/api` do backendu Spring Boot na `localhost:8080` (zob. `proxy.conf.json`).

## Uruchomienie lokalne

1. Backend — w katalogu głównym repozytorium:

   ```bash
   ./mvnw spring-boot:run
   ```

2. Frontend — w katalogu `frontend/`:

   ```bash
   npm install
   npm start
   ```

3. Otwórz `http://localhost:4200`.

## Logowanie — konfiguracja lokalna

Backend nie wystartuje bez danych klientów OAuth2 GitHuba i Google. Jednorazowo:

1. **GitHub** — utwórz OAuth App (GitHub → Settings → Developer settings → OAuth Apps) z adresem callbacku:
   `http://localhost:4200/login/oauth2/code/github`
2. **Google** — w Google Cloud Console skonfiguruj ekran zgody OAuth i utwórz OAuth client typu *Web application* z autoryzowanym adresem przekierowania:
   `http://localhost:4200/login/oauth2/code/google`
3. W katalogu głównym repozytorium skopiuj `.env.properties.example` do `.env.properties` (plik jest git-ignorowany) i uzupełnij `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. Zamiast pliku możesz ustawić zmienne środowiskowe o tych samych nazwach (mają wyższy priorytet).

Callbacki wskazują na `localhost:4200`, bo przeglądarka rozmawia wyłącznie z dev serverem, który przekazuje ruch do backendu.

## Testy

W katalogu `frontend/`:

```bash
npx ng test --watch=false
```
