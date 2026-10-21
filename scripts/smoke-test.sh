#!/bin/bash
# End-to-end smoke test of Phase 1 + Phase 2, entirely through the gateway.
# Requires: curl, jq, a running development stack (docker compose up -d) with PAYMENT_PROVIDER=mock and
# REQUIRE_EMAIL_VERIFICATION=false (the defaults in enviroinment_variables.env), and the bootstrap admin.
# Safe to re-run: every run uses fresh emails and a new club.
#   ./scripts/smoke-test.sh                      (admin password read from secrets/admin-password)
#   GATEWAY_URL=http://localhost:8080 ADMIN_EMAIL=... ADMIN_PASSWORD=... ./scripts/smoke-test.sh
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
G=${GATEWAY_URL:-http://localhost:8080}/api/v1
ADMIN_EMAIL=${ADMIN_EMAIL:-$(grep -sE '^ADMIN_EMAIL=' "$ROOT_DIR/.env" | tail -1 | cut -d= -f2-)}
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@theclub.local}
if [ -z "${ADMIN_PASSWORD:-}" ] && [ -f "$ROOT_DIR/secrets/admin-password" ]; then
  ADMIN_PASSWORD=$(cat "$ROOT_DIR/secrets/admin-password")
fi
ADMIN_PASSWORD=${ADMIN_PASSWORD:-change-me-admin}
VERIFY_TOKEN=${STRAVA_WEBHOOK_VERIFY_TOKEN:-$(grep -sE '^STRAVA_WEBHOOK_VERIFY_TOKEN=' "$ROOT_DIR/.env" | tail -1 | cut -d= -f2-)}
VERIFY_TOKEN=${VERIFY_TOKEN:-change-me-strava-verify-token}
# Portable across GNU (Linux) and BSD (macOS) date: unique run id and "now ± minutes" timestamps
RUN="$(date +%s)$$${RANDOM}"
at_minutes() { date -d "$1 minutes" +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -v"$1"M +%Y-%m-%dT%H:%M:%S; }
fail() { echo "FAIL: $*"; exit 1; }
req() { # method path token body -> prints body, sets CODE
  local out; out=$(curl -s -w '\n%{http_code}' -X "$1" "$G$2" -H 'Content-Type: application/json' ${3:+-H "Authorization: Bearer $3"} ${4:+-d "$4"})
  CODE=${out##*$'\n'}; BODY=${out%$'\n'*}; }
expect() { [ "$CODE" = "$1" ] || fail "$2 expected $1 got $CODE: $BODY"; echo "ok  $2 ($CODE)"; }
login() { req POST /auth/login "" "{\"email\":\"$1\",\"password\":\"$2\"}"; expect 200 "login $1" >&2; echo "$BODY" | jq -r .accessToken; }
NOW=$(date +%Y-%m-%dT%H:%M:%S)
START=$(at_minutes +60); OPENS=$(at_minutes -1)
MONTH=$(date +%Y-%m)

# --- Phase 1: signup/login
req POST /users/register "" '{"name":"Owner Olu","email":"owner-'"$RUN"'@example.com","password":"Str0ng-pass!"}'; expect 201 "register owner"
OWNER_ID=$(echo "$BODY" | jq -r .id)
req POST /users/register "" '{"name":"Member Mia","email":"mia-'"$RUN"'@example.com","password":"Str0ng-pass!"}'; expect 201 "register member"
MIA_ID=$(echo "$BODY" | jq -r .id)
req POST /auth/login "" '{"email":"mia-'"$RUN"'@example.com","password":"wrongpass1"}'; expect 401 "bad password rejected"
OT=$(login owner-$RUN@example.com Str0ng-pass!); MT=$(login mia-$RUN@example.com Str0ng-pass!); AT=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
req GET /users/me "$MT"; expect 200 "users/me"
req GET /users/me ""; expect 401 "no token rejected"

# --- owner verification -> CLUB_OWNER role
req POST /clubs "$OT" '{"clubName":"Chennai Runners '"$RUN"'","description":"Weekend running"}'; expect 403 "unverified user cannot create club"
req POST /owner-verification/apply "$OT" '{"clubName":"Chennai Runners '"$RUN"'","clubType":"RUNNING","city":"Chennai","proofFiles":["https://example.com/proof.jpg"]}'; expect 201 "apply for owner"
REQ_ID=$(echo "$BODY" | jq -r .requestId)
req POST /owner-verification/requests/$REQ_ID/approve "$MT" '{"createClub":false}'; expect 403 "non-admin cannot approve"
req POST /owner-verification/requests/$REQ_ID/approve "$AT" '{"createClub":false}'; expect 200 "admin approves"
req POST /auth/login "" '{"email":"owner-'"$RUN"'@example.com","password":"Str0ng-pass!"}'; RT=$(echo "$BODY" | jq -r .refreshToken)
req POST /auth/refresh "" "{\"refreshToken\":\"$RT\"}"; expect 200 "refresh picks up new role"
OT=$(echo "$BODY" | jq -r .accessToken)
req GET /auth/validate "$OT"; expect 200 "validate"; echo "$BODY" | jq -e '.roles | index("CLUB_OWNER")' >/dev/null || fail "role missing"

# --- create/join clubs
req POST /clubs "$OT" '{"clubName":"Chennai Runners '"$RUN"'","description":"Weekend running","city":"Chennai","clubType":"RUNNING"}'; expect 201 "create club"
CLUB=$(echo "$BODY" | jq -r .id)
req POST /clubs/$CLUB/join "$MT"; expect 201 "member joins"
req POST /clubs/$CLUB/join "$MT"; expect 200 "join idempotent"
req GET /clubs/$CLUB "$MT"; expect 200 "club details"; [ "$(echo "$BODY" | jq .memberCount)" = 2 ] || fail "memberCount"

# --- log activities + leaderboard
req POST /activities "$MT" "{\"title\":\"Morning Run\",\"type\":\"RUN\",\"distance\":10.5,\"duration\":3600,\"startTime\":\"$NOW\"}"; expect 201 "log activity mia"
req POST /activities "$OT" "{\"title\":\"Easy Run\",\"type\":\"RUN\",\"distance\":5.0,\"duration\":1800,\"startTime\":\"$NOW\"}"; expect 201 "log activity owner"
OWNER_ACT=$(echo "$BODY" | jq -r .id)
req POST /activities "$MT" '{"title":"Bad","type":"RUN","distance":-1,"duration":0,"startTime":"2020-01-01T00:00:00"}'; expect 400 "invalid activity rejected"
req GET "/clubs/$CLUB/leaderboard?metric=DISTANCE&period=MONTH" "$MT"; expect 200 "club leaderboard"
echo "$BODY" | jq -c '.entries[] | {rank,name,totalDistanceKm,sessions}'
[ "$(echo "$BODY" | jq -r '.entries[0].userId')" = "$MIA_ID" ] || fail "leaderboard order"

# --- club feed
req POST /clubs/$CLUB/posts "$MT" '{"title":"hi","content":"x"}'; expect 403 "member cannot post"
req POST /clubs/$CLUB/posts "$OT" "{\"title\":\"Linked\",\"content\":\"x\",\"activityId\":\"$OWNER_ACT\"}"; expect 400 "manual activity cannot be linked (Strava only)"
req POST /clubs/$CLUB/posts "$OT" '{"title":"Welcome!","content":"First run Sunday","photoUrls":["https://example.com/a.jpg"]}'; expect 201 "owner posts"
req GET /clubs/$CLUB/posts "$MT"; expect 200 "read feed"

# --- Phase 2: events (free)
req POST /events "$MT" "{\"clubId\":\"$CLUB\",\"title\":\"x\",\"date\":\"$START\"}"; expect 403 "member cannot create event"
req POST /events "$OT" "{\"clubId\":\"$CLUB\",\"title\":\"Sunday Long Run\",\"location\":\"Marina\",\"date\":\"$START\",\"registrationOpensAt\":\"$OPENS\",\"capacity\":50,\"points\":10}"; expect 201 "create free event"
EV=$(echo "$BODY" | jq -r .id)
req POST /events/$EV/register "$MT"; expect 201 "register free event"
REG=$(echo "$BODY" | jq -r .registrationId); echo "$BODY" | jq -e '.status=="CONFIRMED"' >/dev/null || fail "not confirmed"
req POST /events/$EV/register "$MT"; expect 409 "double registration"
req GET /events/registrations/$REG/ticket "$MT"; expect 200 "ticket with QR"
CODE_T=$(echo "$BODY" | jq -r .ticketCode); echo "$BODY" | jq -e '.qrCodeBase64|length>100' >/dev/null || fail "qr"
[ "$(curl -s -H "Authorization: Bearer $MT" $G/events/registrations/$REG/ticket.png | head -c 4 | tail -c 3)" = PNG ] && echo "ok  ticket.png" || fail "ticket.png"
req POST /events/$EV/check-in "$MT" "{\"ticketCode\":\"THECLUB-TICKET:$CODE_T\"}"; expect 403 "member cannot check in"
req POST /events/$EV/check-in "$OT" "{\"ticketCode\":\"THECLUB-TICKET:$CODE_T\"}"; expect 200 "owner scans QR"
req POST /events/$EV/check-in "$OT" "{\"ticketCode\":\"$CODE_T\"}"; expect 409 "double check-in"

# --- paid event with mock payment
req POST /events "$OT" "{\"clubId\":\"$CLUB\",\"title\":\"Paid 10K\",\"date\":\"$START\",\"registrationOpensAt\":\"$OPENS\",\"price\":499,\"currency\":\"INR\",\"points\":20}"; expect 201 "create paid event"
EV2=$(echo "$BODY" | jq -r .id)
req POST /events/$EV2/register "$MT"; expect 201 "register paid event"
PAY=$(echo "$BODY" | jq -r .payment.paymentId); echo "$BODY" | jq -e '.status=="PENDING_PAYMENT"' >/dev/null || fail "pending"
req POST /payments/$PAY/confirm "$MT" '{"providerPaymentId":"pay_1","signature":"forged"}'; expect 400 "forged signature rejected"
req POST /events/$EV2/register "$MT"; expect 201 "re-register after failed payment"
PAY=$(echo "$BODY" | jq -r .payment.paymentId)
req POST /payments/$PAY/mock-signature "$MT" '{"providerPaymentId":"pay_2"}'; expect 200 "mock signature"
SIG=$(echo "$BODY" | jq -r .signature)
req POST /payments/$PAY/confirm "$MT" "{\"providerPaymentId\":\"pay_2\",\"signature\":\"$SIG\"}"; expect 200 "payment confirmed"
req GET /events/me/registrations "$MT"; expect 200 "my registrations"
REG2=$(echo "$BODY" | jq -r --arg e "$EV2" '(.items? // .)[] | select(.eventId==$e) | .registrationId' | head -1)
req GET /events/registrations/$REG2/ticket "$MT"; expect 200 "paid ticket"; CODE2=$(echo "$BODY" | jq -r .ticketCode)
req POST /events/$EV2/check-in "$OT" "{\"ticketCode\":\"$CODE2\"}"; expect 200 "check-in paid event"

# --- event recap post, participation, monthly leaderboard, notifications
req POST /clubs/$CLUB/posts "$OT" "{\"type\":\"EVENT_RECAP\",\"title\":\"Recap\",\"content\":\"Great run\",\"eventId\":\"$EV\",\"photoUrls\":[\"https://example.com/r.jpg\"]}"; expect 201 "event recap post"
req GET /participation/me "$MT"; expect 200 "participation"; echo "$BODY" | jq -c .
[ "$(echo "$BODY" | jq .eventsAttended)" = 2 ] && [ "$(echo "$BODY" | jq .totalPoints)" = 30 ] || fail "points"
req GET "/leaderboards/monthly?month=$MONTH" "$MT"; expect 200 "monthly leaderboard"; echo "$BODY" | jq -c .
echo "$BODY" | jq -e --arg c "$CLUB" '.topClubs | map(.clubId) | index($c)' >/dev/null || fail "club missing from monthly leaderboard"
sleep 1
req GET /notifications/me "$MT"; expect 200 "notifications"; echo "$BODY" | jq -r '.items[].type' | sort | uniq -c

# --- strava webhook verification + internal blocked
# --- user journey: profile location, discovery, follow, upcoming feed, reviews, image upload
req PUT /users/$MIA_ID "$MT" '{"latitude":13.0500,"longitude":80.2824,"profileComplete":true}'; expect 200 "member saves location"
[ "$(echo "$BODY" | jq .profileComplete)" = true ] || fail "profile setup not marked complete"
req PUT /clubs/$CLUB "$OT" '{"latitude":13.0501,"longitude":80.2825,"address":"Lighthouse, Marina Beach","subscriptionBenefits":"Members-only long runs"}'; expect 200 "owner sets club location"
req GET "/clubs/nearby?lat=13.05&lng=80.2824&radiusKm=5" "$MT"; expect 200 "nearby clubs"
echo "$BODY" | jq -e --arg c "$CLUB" 'map(select(.id == $c and .distanceKm < 1)) | length == 1' >/dev/null || fail "club not listed nearby"
req GET "/clubs/popular?limit=5" "$MT"; expect 200 "popular clubs"
req POST /users/register "" '{"name":"Fan Farah","email":"fan-'"$RUN"'@example.com","password":"Str0ng-pass!"}'; expect 201 "register follower"
FT=$(login fan-$RUN@example.com Str0ng-pass!)
req POST /clubs/$CLUB/follow "$FT"; expect 201 "follow club"
req POST /clubs/$CLUB/follow "$FT"; expect 200 "follow idempotent"
req GET /clubs/$CLUB "$FT"; [ "$(echo "$BODY" | jq '.following and .followerCount == 1 and .myRole == null')" = true ] || fail "follow state"
echo "ok  follower is not a member"
req GET "/events/upcoming?lat=13.05&lng=80.2824" "$FT"; expect 200 "upcoming feed"
echo "$BODY" | jq -e --arg e "$EV2" 'map(select(.event.id == $e and (.sources | index("FOLLOWING")) and (.sources | index("NEARBY")))) | length == 1' >/dev/null || fail "followed nearby event missing from feed"
req PUT /clubs/$CLUB/reviews/me "$FT" '{"rating":1}'; expect 403 "follower cannot review"
req PUT /clubs/$CLUB/reviews/me "$MT" '{"rating":5,"comment":"Great pacers"}'; expect 200 "subscriber reviews"
req GET /clubs/$CLUB/reviews "$FT"; expect 200 "read reviews"
[ "$(echo "$BODY" | jq '.summary.count == 1 and .summary.average == 5')" = true ] || fail "review summary"
req DELETE /clubs/$CLUB/follow "$FT"; expect 204 "unfollow"
# A real PNG through the gateway, then read back through the web app's /media (SeaweedFS by default)
WEB=${WEB_URL:-http://localhost:${WEB_APP_PORT:-3000}}
PNG=$(mktemp); printf 'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGNk+M/AwMDAxMDAwMDAAAAMAQEBnSg7CgAAAABJRU5ErkJggg==' | base64 -d > "$PNG"
out=$(curl -s -w '\n%{http_code}' -X POST "$G/users/me/photo" -H "Authorization: Bearer $MT" -F "file=@$PNG;type=image/png"); CODE=${out##*$'\n'}; BODY=${out%$'\n'*}
rm -f "$PNG"
if [ "$CODE" = 503 ]; then
  echo "skip image upload: no image store configured ($(echo "$BODY" | jq -r .code))"
else
  expect 200 "upload profile picture"
  PIC=$(echo "$BODY" | jq -r .profilePicUrl)
  case "$PIC" in /media/profiles/*.png) ;; *) fail "unexpected picture URL $PIC" ;; esac
  [ "$(curl -s -o /dev/null -w '%{http_code} %{content_type}' "$WEB$PIC")" = "200 image/png" ] || fail "picture not served at $WEB$PIC"
  echo "ok  picture served from $PIC"
  [ "$(curl -s -o /dev/null -w '%{http_code}' -X PUT --data-binary x "$WEB/media/profiles/forged.png")" != 200 ] || fail "anonymous upload to /media accepted"
  echo "ok  /media is read-only"
fi

curl -s "$G/strava/webhook?hub.mode=subscribe&hub.verify_token=$VERIFY_TOKEN&hub.challenge=abc" | grep -q abc && echo "ok  strava webhook challenge" || fail "strava challenge"
[ "$(curl -s -o /dev/null -w '%{http_code}' ${GATEWAY_URL:-http://localhost:8080}/internal/users/by-email/x)" = 404 ] && echo "ok  /internal blocked at gateway" || fail "internal exposed"
echo "ALL PASSED"
