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

## Testy

W katalogu `frontend/`:

```bash
npx ng test --watch=false
```
