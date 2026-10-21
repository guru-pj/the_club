#!/bin/bash
# Fills a development stack with realistic sample data, through the public API only (the same paths the apps use):
# three verified clubs with complete profiles, published and draft events (free and paid), members who follow,
# subscribe, register, post, comment, review and chat, and one club application still waiting for review.
#
# Development only: needs REQUIRE_EMAIL_VERIFICATION=false and PAYMENT_PROVIDER=mock (the enviroinment_variables.env
# defaults), and the bootstrap admin. Safe to re-run: if the demo accounts exist it only prints them.
#   ./scripts/seed-demo.sh
#   GATEWAY_URL=http://localhost:8080 ADMIN_EMAIL=... ADMIN_PASSWORD=... ./scripts/seed-demo.sh
#   SEED_TAG=2 ./scripts/seed-demo.sh     another, separate set (accounts and club names get the tag)
set -u
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
G=${GATEWAY_URL:-http://localhost:8080}/api/v1
ADMIN_EMAIL=${ADMIN_EMAIL:-$(grep -sE '^ADMIN_EMAIL=' "$ROOT_DIR/.env" | tail -1 | cut -d= -f2-)}
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@theclub.local}
if [ -z "${ADMIN_PASSWORD:-}" ] && [ -f "$ROOT_DIR/secrets/admin-password" ]; then
  ADMIN_PASSWORD=$(cat "$ROOT_DIR/secrets/admin-password")
fi
ADMIN_PASSWORD=${ADMIN_PASSWORD:-change-me-admin}
PASSWORD='Demo-pass-2026!'
TAG=${SEED_TAG:-}
DOMAIN=demo${TAG:+-$TAG}.theclub.local
N1="Marina Morning Runners${TAG:+ $TAG}"
N2="Coastal Cycling Collective${TAG:+ $TAG}"
N3="Anna Nagar Strength Lab${TAG:+ $TAG}"
N4="Adyar Triathletes${TAG:+ $TAG}"

fail() { echo "FAIL: $*" >&2; exit 1; }
req() { # method path token body -> sets BODY and CODE
  local out; out=$(curl -s -w '\n%{http_code}' -X "$1" "$G$2" -H 'Content-Type: application/json' ${3:+-H "Authorization: Bearer $3"} ${4:+-d "$4"})
  CODE=${out##*$'\n'}; BODY=${out%$'\n'*}; }
must() { [ "$CODE" = "$1" ] || fail "$2: expected $1, got $CODE: $BODY"; }
at() { date -d "$1" +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -v"$2" +%Y-%m-%dT%H:%M:%S; }
token() { req POST /auth/login "" "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}"; must 200 "log in as $1"; echo "$BODY" | jq -r .accessToken; }
signup() { # name email clubOwner -> user id
  req POST /users/register "" "{\"name\":\"$1\",\"email\":\"$2\",\"password\":\"$PASSWORD\",\"clubOwner\":$3}"; must 201 "register $2"
  local id; id=$(echo "$BODY" | jq -r .id)
  local t; t=$(token "$2")
  req PUT "/users/$id" "$t" '{"profileComplete":true}'; must 200 "finish profile setup for $2"
  echo "$id"
}

command -v jq >/dev/null || fail "jq is required"
req POST /auth/login "" "{\"email\":\"anika@$DOMAIN\",\"password\":\"$PASSWORD\"}"
if [ "$CODE" = 200 ]; then
  echo "Demo data is already there."
else
  [ "$CODE" = 401 ] || fail "unexpected answer from login ($CODE): $BODY. Is the stack up, with REQUIRE_EMAIL_VERIFICATION=false?"
  req POST /auth/login "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}"; must 200 "admin login"
  AT=$(echo "$BODY" | jq -r .accessToken)

  echo "• club owners sign up and apply, one application per club"
  ANIKA=$(signup "Anika Raman" "anika@$DOMAIN" true)
  VIKRAM=$(signup "Vikram Iyer" "vikram@$DOMAIN" true)
  ZARA=$(signup "Zara Thomas" "zara@$DOMAIN" true)
  AT_ANIKA=$(token "anika@$DOMAIN"); AT_VIKRAM=$(token "vikram@$DOMAIN"); AT_ZARA=$(token "zara@$DOMAIN")

  apply() { # token json -> request id
    req POST /owner-verification/apply "$1" "$2"; must 201 "apply"; echo "$BODY" | jq -r .requestId; }
  R1=$(apply "$AT_ANIKA" '{"clubName":"'"$N1"'","clubType":"RUNNING","city":"Chennai",
    "address":"Lighthouse, Marina Beach","latitude":13.0405,"longitude":80.2797,"contactEmail":"hello@marinarunners.in",
    "contactPhone":"+91 98400 11223","website":"https://marinarunners.in",
    "socialLinks":{"instagram":"https://instagram.com/marinamorningrunners","strava":"https://www.strava.com/clubs/marina-morning-runners"},
    "description":"Running together on the Marina since 2017: easy 5k loops on weekdays and a long run every Sunday."}')
  R2=$(apply "$AT_VIKRAM" '{"clubName":"'"$N2"'","clubType":"CYCLING","city":"Chennai",
    "address":"Elliot'"'"'s Beach, Besant Nagar","latitude":12.9990,"longitude":80.2707,"contactEmail":"ride@coastalcycling.in",
    "socialLinks":{"instagram":"https://instagram.com/coastalcyclingcollective","facebook":"https://facebook.com/coastalcycling"},
    "description":"Weekend rides down the East Coast Road for every level, from first 30 km to 200 km brevets."}')
  R3=$(apply "$AT_ZARA" '{"clubName":"'"$N3"'","clubType":"FITNESS","city":"Chennai",
    "address":"2nd Avenue, Anna Nagar","latitude":13.0850,"longitude":80.2101,"contactPhone":"+91 98840 55667",
    "website":"https://strengthlab.example","description":"Small-group strength and conditioning, three mornings a week."}')
  # Vikram also claims a second club; it stays waiting for review
  apply "$AT_VIKRAM" '{"clubName":"'"$N4"'","clubType":"TRIATHLON","city":"Chennai","contactEmail":"tri@adyar.example",
    "description":"Swim, bike, run training for sprint and Olympic distance."}' >/dev/null

  echo "• the admin team verifies the first three"
  req POST "/owner-verification/requests/$R2/request-info" "$AT" '{"message":"Please add the club Facebook page or another public link."}'; must 200 "ask for changes"
  req POST "/owner-verification/requests/$R2/resubmit" "$AT_VIKRAM" '{"note":"Facebook page is in the social links."}'; must 200 "resubmit"
  approve() { req POST "/owner-verification/requests/$1/approve" "$AT" '{}'; must 200 "approve"; echo "$BODY" | jq -r .createdClubId; }
  C1=$(approve "$R1"); C2=$(approve "$R2"); C3=$(approve "$R3")
  AT_ANIKA=$(token "anika@$DOMAIN"); AT_VIKRAM=$(token "vikram@$DOMAIN"); AT_ZARA=$(token "zara@$DOMAIN")

  echo "• owners complete their club profiles"
  req PUT "/clubs/$C1" "$AT_ANIKA" '{"rules":"Reflective kit and a light before sunrise.\nRun on the left; give way to walkers.\nNobody is left behind on the long run.","subscriptionBenefits":"Pacers on every long run, members-only track sessions and club kit at cost."}'; must 200 "profile 1"
  req PUT "/clubs/$C2" "$AT_VIKRAM" '{"rules":"Helmets always. Lights front and rear. Ride two abreast at most, single file on ECR.","subscriptionBenefits":"Route files, a sweep rider on every ride and workshop discounts."}'; must 200 "profile 2"
  req PUT "/clubs/$C3" "$AT_ZARA" '{"subscriptionsEnabled":true,"rules":"Bring a towel and a water bottle. Re-rack your weights.","subscriptionBenefits":"Monthly technique clinic and a programme for the weeks in between."}'; must 200 "profile 3"

  echo "• events: published, paid and a draft"
  event() { # token json -> event id
    req POST /events "$1" "$2"; must 201 "create event"; echo "$BODY" | jq -r .id; }
  E1=$(event "$AT_ANIKA" "{\"clubId\":\"$C1\",\"title\":\"Sunday Long Run: 21k\",\"location\":\"Lighthouse, Marina Beach\",\"date\":\"$(at '+4 days 05:30' +4d)\",\"capacity\":60,\"points\":20,\"rules\":\"Pace groups at 5:30, 6:15 and 7:00 min/km. Water at 7k and 14k.\",\"description\":\"Out to Besant Nagar and back along the beach road.\"}")
  E2=$(event "$AT_ANIKA" "{\"clubId\":\"$C1\",\"title\":\"Track Tuesday: 8 x 400 m\",\"location\":\"Nehru Stadium track\",\"date\":\"$(at '+2 days 18:30' +2d)\",\"capacity\":25,\"subscribersOnly\":true,\"description\":\"Intervals for subscribers.\"}")
  E3=$(event "$AT_VIKRAM" "{\"clubId\":\"$C2\",\"title\":\"ECR 100 km Brevet\",\"location\":\"Elliot's Beach, Besant Nagar\",\"date\":\"$(at '+9 days 05:00' +9d)\",\"capacity\":40,\"price\":299,\"currency\":\"INR\",\"points\":40,\"rules\":\"Helmet, two bottles, spare tube. Cut-off 6h40.\"}")
  event "$AT_VIKRAM" "{\"clubId\":\"$C2\",\"title\":\"Beginners' Saturday Spin\",\"location\":\"Besant Nagar\",\"date\":\"$(at '+12 days 06:00' +12d)\",\"publish\":false}" >/dev/null
  E5=$(event "$AT_ZARA" "{\"clubId\":\"$C3\",\"title\":\"Deadlift Technique Clinic\",\"location\":\"Strength Lab, Anna Nagar\",\"date\":\"$(at '+6 days 07:00' +6d)\",\"capacity\":12,\"price\":499,\"currency\":\"INR\"}")

  echo "• members join in"
  MEERA=$(signup "Meera Krishnan" "meera@$DOMAIN" false)
  ARJUN=$(signup "Arjun Menon" "arjun@$DOMAIN" false)
  FATIMA=$(signup "Fatima Sheikh" "fatima@$DOMAIN" false)
  AT_MEERA=$(token "meera@$DOMAIN"); AT_ARJUN=$(token "arjun@$DOMAIN"); AT_FATIMA=$(token "fatima@$DOMAIN")
  req PUT "/users/$MEERA" "$AT_MEERA" '{"city":"Chennai","bio":"Half-marathoner chasing sub-2.","latitude":13.0418,"longitude":80.2341}'; must 200 "Meera's profile"
  for t in "$AT_MEERA" "$AT_ARJUN"; do req POST "/clubs/$C1/join" "$t"; must 201 "subscribe to club 1"; done
  req POST "/clubs/$C2/join" "$AT_ARJUN"; must 201 "subscribe to club 2"
  req POST "/clubs/$C1/follow" "$AT_FATIMA"; must 201 "follow club 1"
  req POST "/clubs/$C3/follow" "$AT_MEERA"; must 201 "follow club 3"

  req POST "/events/$E1/register" "$AT_MEERA"; must 201 "register for the long run"
  req POST "/events/$E1/register" "$AT_FATIMA"; must 201 "register for the long run"
  req POST "/events/$E2/register" "$AT_ARJUN"; must 201 "register for track Tuesday"
  req POST "/events/$E3/register" "$AT_ARJUN"; must 201 "start paying for the brevet"
  PAY=$(echo "$BODY" | jq -r .payment.paymentId)
  req POST "/payments/$PAY/mock-signature" "$AT_ARJUN" '{"providerPaymentId":"pay_demo_1"}'; must 200 "mock payment"
  SIG=$(echo "$BODY" | jq -r .signature)
  req POST "/payments/$PAY/confirm" "$AT_ARJUN" "{\"providerPaymentId\":\"pay_demo_1\",\"signature\":\"$SIG\"}"; must 200 "confirm payment"

  echo "• posts, likes, comments, reviews and chat"
  req POST "/clubs/$C1/posts" "$AT_ANIKA" '{"title":"New route for Sunday","content":"We are trying the Besant Nagar loop this week: flat, shaded and 21k on the dot."}'; must 201 "owner post"
  P1=$(echo "$BODY" | jq -r .id)
  req POST "/clubs/$C1/posts" "$AT_MEERA" '{"title":"First 10k under the hour!","content":"Thanks to the 6:00 pace group for dragging me along."}'; must 201 "member post"
  P2=$(echo "$BODY" | jq -r .id)
  req PUT "/clubs/$C1/posts/$P2/like" "$AT_ANIKA"; req PUT "/clubs/$C1/posts/$P2/like" "$AT_ARJUN"; req PUT "/clubs/$C1/posts/$P1/like" "$AT_FATIMA"
  req POST "/clubs/$C1/posts/$P2/comments" "$AT_ANIKA" '{"content":"Brilliant, Meera! Sub-55 next?"}'; must 201 "comment"
  req POST "/clubs/$C1/posts/$P1/comments" "$AT_FATIMA" '{"content":"Is there water at the turnaround?"}'; must 201 "comment"
  req POST "/clubs/$C2/posts/reshare" "$AT_VIKRAM" "{\"eventId\":\"$E1\",\"comment\":\"Running friends: worth it if you want a change from the saddle.\"}"; must 201 "reshare"
  req PUT "/clubs/$C1/reviews/me" "$AT_MEERA" '{"rating":5,"comment":"Friendly pacers and a great Sunday route."}'; must 200 "review"
  req PUT "/clubs/$C1/reviews/me" "$AT_ARJUN" '{"rating":4,"comment":"Well organised. Track sessions fill up fast."}'; must 200 "review"
  req GET "/chat/clubs/$C1" "$AT_ANIKA"; must 200 "open the club chat"
  CHAT=$(echo "$BODY" | jq -r .id)
  req POST "/chat/conversations/$CHAT/messages" "$AT_ANIKA" '{"body":"Morning all! Long run meets at the lighthouse, 5:30 sharp."}'; must 201 "chat"
  req POST "/chat/conversations/$CHAT/messages" "$AT_MEERA" '{"body":"See you there. Anyone parking near the aquarium?"}'; must 201 "chat"
  req POST "/chat/conversations/$CHAT/messages" "$AT_ARJUN" '{"body":"I can give a lift from Mylapore."}'; must 201 "chat"
fi

cat <<EOF

Demo accounts (password: $PASSWORD)
  Club owners   anika@$DOMAIN   $N1 (running)
                vikram@$DOMAIN  $N2 (cycling) + an application for $N4 waiting for review
                zara@$DOMAIN    $N3 (fitness)
  Members       meera@$DOMAIN   subscribed to $N1, registered for the Sunday long run
                arjun@$DOMAIN   subscribed to two clubs, paid for the ECR brevet
                fatima@$DOMAIN  follows $N1
  Admin         $ADMIN_EMAIL (Owner applications shows the triathlon club's application)
EOF
