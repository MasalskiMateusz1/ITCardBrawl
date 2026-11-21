#!/usr/bin/env bash
# Deploys ItCardBrawl on the server (the script's directory, e.g. /opt/itcardbrawl).
#
# Usage:
#   ./deploy.sh <tag>     deploys the image ghcr.io/masalskimateusz1/itcardbrawl:<tag>
#   ./deploy.sh rollback  goes back to the tag from .previous_tag
#
# deploy <tag>:
#   1. Saves the current APP_TAG from .env in .previous_tag (before the swap). When <tag> equals
#      the current one (redeploying the same version), .previous_tag stays unchanged.
#   2. Sets APP_TAG=<tag> in .env, `docker compose pull app`, `docker compose up -d`
#      and waits up to 90 s for health=healthy of the app container.
#   3. Failure (pull - e.g. a nonexistent tag, up, or not healthy): restores the previous
#      APP_TAG and the previous content of .previous_tag, `up -d`, exits with code 1.
#      The first deploy (no APP_TAG in .env) has nothing to go back to - only an error, and the
#      APP_TAG entry is removed from .env so the failed tag does not become the "previous" one.
#   4. Success: removes local app images other than the current and previous ones, prunes dangling images.
#
# rollback:
#   Sets APP_TAG from .previous_tag, `up -d` (no pull - the image is in the local cache)
#   and waits for health. .previous_tag is NOT changed: a repeated rollback does not "go back
#   forward" to the version that was rolled back. When the previous version does not become healthy,
#   the script restores the tag from before the rollback and exits with code 1.
#
# Requires in the script's directory: compose.yaml, Caddyfile, app.env and .env with APP_DOMAIN.
set -euo pipefail

# A dropped SSH session (cancelled CI job, network) must not stop the script between swapping the tag
# and restoring it: we ignore HUP/PIPE, and log() does not end the script when stderr is gone.
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

# Current APP_TAG from .env (empty when the file or the entry is missing).
current_tag() {
	[[ -f $ENV_FILE ]] || return 0
	sed -n 's/^APP_TAG=//p' "$ENV_FILE" | tail -n 1
}

# Sets APP_TAG in .env (replaces the entry, or appends it when missing).
set_tag() {
	local tag=$1
	touch "$ENV_FILE"
	if grep -q '^APP_TAG=' "$ENV_FILE"; then
		sed -i "s/^APP_TAG=.*/APP_TAG=${tag}/" "$ENV_FILE"
	else
		echo "APP_TAG=${tag}" >>"$ENV_FILE"
	fi
}

# Docker image tag format; also protects sed in set_tag from special characters.
valid_tag() {
	[[ $1 =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]
}

# Waits up to HEALTH_TIMEOUT s for health=healthy of the app container.
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

# Removes local app images other than the current and previous tags; errors do not break the deployment.
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
		# First deploy: the failed tag must not become the "current" one for the next deployment.
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
