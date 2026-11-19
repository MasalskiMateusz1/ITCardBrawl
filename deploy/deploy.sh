#!/usr/bin/env bash
# Wdrozenie ItCardBrawl na serwerze (katalog skryptu, np. /opt/itcardbrawl).
#
# Uzycie:
#   ./deploy.sh <tag>     wdraza obraz ghcr.io/masalskimateusz1/itcardbrawl:<tag>
#   ./deploy.sh rollback  wraca do tagu z .previous_tag
#
# deploy <tag>:
#   1. Zapamietuje biezacy APP_TAG z .env w .previous_tag (przed podmiana). Gdy <tag> jest
#      rowny biezacemu (ponowny deploy tej samej wersji), .previous_tag zostaje bez zmian.
#   2. Ustawia APP_TAG=<tag> w .env, `docker compose pull app`, `docker compose up -d`
#      i czeka do 90 s na health=healthy kontenera app.
#   3. Porazka (pull - np. nieistniejacy tag, up albo brak zdrowia): przywraca poprzedni
#      APP_TAG i poprzednia zawartosc .previous_tag, `up -d`, konczy sie kodem 1.
#      Pierwszy deploy (brak APP_TAG w .env) nie ma do czego wracac - tylko blad, a wpis
#      APP_TAG jest usuwany z .env, zeby nieudany tag nie stal sie "poprzednim".
#   4. Sukces: usuwa lokalne obrazy aplikacji poza biezacym i poprzednim, czysci obrazy wiszace.
#
# rollback:
#   Ustawia APP_TAG z .previous_tag, `up -d` (bez pull - obraz jest w lokalnym cache)
#   i czeka na zdrowie. .previous_tag NIE jest zmieniany: ponowny rollback nie "wraca do
#   przodu" na wersje, z ktorej sie wycofano. Gdy poprzednia wersja nie stanie sie zdrowa,
#   skrypt przywraca tag sprzed rollbacku i konczy sie kodem 1.
#
# Wymaga w katalogu skryptu: compose.yaml, Caddyfile, app.env i .env z APP_DOMAIN.
set -euo pipefail

# Zerwane SSH (anulowany job CI, siec) nie moze przerwac skryptu miedzy podmiana tagu a jej
# przywroceniem: ignorujemy HUP/PIPE, a log() nie konczy skryptu, gdy stderr juz nie istnieje.
trap '' HUP PIPE

cd "$(dirname "$(readlink -f "$0")")"

readonly IMAGE=ghcr.io/masalskimateusz1/itcardbrawl
readonly ENV_FILE=.env
readonly PREV_FILE=.previous_tag
readonly HEALTH_TIMEOUT=90

log() { echo "[deploy] $*" >&2 || true; }
die() { log "BLAD: $*"; exit 1; }

usage() {
	echo "Uzycie: $0 <tag> | rollback" >&2
	exit 2
}

# Biezacy APP_TAG z .env (pusty, gdy brak pliku albo wpisu).
current_tag() {
	[[ -f $ENV_FILE ]] || return 0
	sed -n 's/^APP_TAG=//p' "$ENV_FILE" | tail -n 1
}

# Ustawia APP_TAG w .env (podmienia wpis albo dopisuje, gdy go nie ma).
set_tag() {
	local tag=$1
	touch "$ENV_FILE"
	if grep -q '^APP_TAG=' "$ENV_FILE"; then
		sed -i "s/^APP_TAG=.*/APP_TAG=${tag}/" "$ENV_FILE"
	else
		echo "APP_TAG=${tag}" >>"$ENV_FILE"
	fi
}

# Format tagu obrazu Dockera; chroni tez sed w set_tag przed znakami specjalnymi.
valid_tag() {
	[[ $1 =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]
}

# Czeka do HEALTH_TIMEOUT s na health=healthy kontenera app.
wait_healthy() {
	local deadline=$((SECONDS + HEALTH_TIMEOUT)) cid status=""
	log "Czekam do ${HEALTH_TIMEOUT} s na zdrowie kontenera app..."
	while ((SECONDS < deadline)); do
		cid=$(docker compose ps -q app 2>/dev/null || true)
		if [[ -n $cid ]]; then
			status=$(docker inspect -f '{{.State.Health.Status}}' "$cid" 2>/dev/null || true)
			case $status in
			healthy)
				log "app: healthy"
				return 0
				;;
			unhealthy)
				break
				;;
			esac
		fi
		sleep 3
	done
	log "app nie jest zdrowy (status: ${status:-brak kontenera}). Ostatnie logi:"
	docker compose logs --tail 50 app >&2 || true
	return 1
}

# Usuwa lokalne obrazy aplikacji poza biezacym i poprzednim tagiem; bledy nie psuja wdrozenia.
prune_images() {
	local keep_current=$1 keep_previous=$2 tag
	while read -r tag; do
		[[ $tag == "<none>" || $tag == "$keep_current" || $tag == "$keep_previous" ]] && continue
		docker image rm "${IMAGE}:${tag}" >/dev/null 2>&1 || true
	done < <(docker image ls "$IMAGE" --format '{{.Tag}}')
	docker image prune -f >/dev/null 2>&1 || true
}

preflight() {
	[[ -f compose.yaml ]] || die "brak compose.yaml w $(pwd)"
	[[ -f app.env ]] || die "brak app.env w $(pwd) (zob. deploy/README.md)"
	grep -q '^APP_DOMAIN=.' "$ENV_FILE" 2>/dev/null || die "brak APP_DOMAIN w $(pwd)/${ENV_FILE}"
}

deploy() {
	local new=$1 current old_prev=""
	valid_tag "$new" || die "niepoprawny tag: '$new'"
	current=$(current_tag)
	[[ -f $PREV_FILE ]] && old_prev=$(<"$PREV_FILE")

	if [[ -n $current && $current != "$new" ]]; then
		echo "$current" >"$PREV_FILE"
	fi
	set_tag "$new"
	log "Wdrazam ${IMAGE}:${new} (poprzednio: ${current:-brak})"

	if docker compose pull app && docker compose up -d && wait_healthy; then
		log "Wdrozono ${new}"
		prune_images "$new" "$(cat "$PREV_FILE" 2>/dev/null || true)"
		return 0
	fi

	if [[ -z $current ]]; then
		# Pierwszy deploy: nieudany tag nie moze zostac "biezacym" dla nastepnego wdrozenia.
		sed -i '/^APP_TAG=/d' "$ENV_FILE"
		die "wdrozenie ${new} nieudane, brak poprzedniej wersji do przywrocenia"
	fi
	if [[ $current == "$new" ]]; then
		die "ponowne wdrozenie ${new} nieudane, brak innej wersji do przywrocenia"
	fi
	log "Wdrozenie ${new} nieudane - przywracam ${current}"
	set_tag "$current"
	if [[ -n $old_prev ]]; then
		echo "$old_prev" >"$PREV_FILE"
	else
		rm -f "$PREV_FILE"
	fi
	if docker compose up -d && wait_healthy; then
		die "wdrozenie ${new} nieudane, przywrocono ${current}"
	fi
	die "wdrozenie ${new} nieudane, a przywrocona wersja ${current} tez nie jest zdrowa"
}

rollback() {
	local prev current
	[[ -s $PREV_FILE ]] || die "brak ${PREV_FILE} - nie ma do czego wracac"
	prev=$(<"$PREV_FILE")
	valid_tag "$prev" || die "niepoprawny tag w ${PREV_FILE}: '$prev'"
	current=$(current_tag)
	log "Rollback: ${current:-brak} -> ${prev}"
	set_tag "$prev"

	if docker compose up -d && wait_healthy; then
		log "Przywrocono ${prev}"
		return 0
	fi

	if [[ -z $current || $current == "$prev" ]]; then
		die "rollback do ${prev} nieudany"
	fi
	log "Rollback do ${prev} nieudany - wracam do ${current}"
	set_tag "$current"
	docker compose up -d || true
	die "rollback do ${prev} nieudany, przywrocono ${current}"
}

main() {
	[[ $# -eq 1 && $1 != -h && $1 != --help ]] || usage
	preflight
	case $1 in
	rollback) rollback ;;
	*) deploy "$1" ;;
	esac
}

main "$@"
