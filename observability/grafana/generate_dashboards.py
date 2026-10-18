#!/usr/bin/env python3
"""Generates the provisioned Grafana dashboards (observability/grafana/dashboards/*.json).

Edit this file and re-run `python3 observability/grafana/generate_dashboards.py` instead of hand-editing JSON.
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "dashboards"
PROM = {"type": "prometheus", "uid": "prometheus"}
LOKI = {"type": "loki", "uid": "loki"}


class Board:
    def __init__(self, uid, title, description, variables=()):
        self.uid, self.title, self.description = uid, title, description
        self.panels, self.y, self.next_id = [], 0, 1
        self.variables = list(variables)

    def row(self, title):
        if getattr(self, "x", 0) > 0:  # finish a half-filled line before starting the section
            self.x, self.y = 0, self.y + self.row_h
        self.panels.append({"type": "row", "title": title, "id": self._id(), "collapsed": False,
                            "gridPos": {"h": 1, "w": 24, "x": 0, "y": self.y}, "panels": []})
        self.y += 1
        self.x = 0

    def _id(self):
        self.next_id += 1
        return self.next_id

    def _place(self, w, h):
        if getattr(self, "x", 0) + w > 24:
            self.x, self.y = 0, self.y + self.row_h
        pos = {"h": h, "w": w, "x": getattr(self, "x", 0), "y": self.y}
        self.x = getattr(self, "x", 0) + w
        self.row_h = h
        if self.x >= 24:
            self.x, self.y = 0, self.y + h
        return pos

    def timeseries(self, title, targets, unit="short", w=12, h=8, description="", stack=False):
        self.panels.append({
            "type": "timeseries", "title": title, "id": self._id(), "datasource": PROM, "description": description,
            "gridPos": self._place(w, h),
            "fieldConfig": {"defaults": {"unit": unit, "custom": {
                "lineWidth": 1, "fillOpacity": 15 if stack else 5, "showPoints": "never",
                "stacking": {"mode": "normal" if stack else "none"}}}, "overrides": []},
            "options": {"legend": {"displayMode": "table", "placement": "bottom", "calcs": ["mean", "max", "lastNotNull"]},
                        "tooltip": {"mode": "multi", "sort": "desc"}},
            "targets": [{"datasource": PROM, "refId": chr(65 + i), "expr": expr, "legendFormat": legend}
                        for i, (expr, legend) in enumerate(targets)],
        })

    def stat(self, title, expr, unit="short", w=6, h=4, mappings=None, thresholds=None, legend="", description=""):
        self.panels.append({
            "type": "stat", "title": title, "id": self._id(), "datasource": PROM, "description": description,
            "gridPos": self._place(w, h),
            "fieldConfig": {"defaults": {
                "unit": unit, "mappings": mappings or [],
                "thresholds": {"mode": "absolute", "steps": thresholds or [{"color": "green", "value": None}]},
                "noValue": "0"}, "overrides": []},
            "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                        "colorMode": "background", "graphMode": "area", "textMode": "auto", "orientation": "auto"},
            "targets": [{"datasource": PROM, "refId": "A", "expr": expr, "legendFormat": legend, "instant": True}],
        })

    def logs(self, title, expr, w=24, h=10):
        self.panels.append({
            "type": "logs", "title": title, "id": self._id(), "datasource": LOKI, "gridPos": self._place(w, h),
            "options": {"showTime": True, "wrapLogMessage": True, "sortOrder": "Descending", "enableLogDetails": True},
            "targets": [{"datasource": LOKI, "refId": "A", "expr": expr}],
        })

    def write(self):
        board = {
            "uid": self.uid, "title": self.title, "description": self.description, "tags": ["the-club"],
            "timezone": "browser", "schemaVersion": 39, "version": 1, "editable": False,
            "time": {"from": "now-6h", "to": "now"}, "refresh": "30s", "graphTooltip": 1,
            "templating": {"list": self.variables}, "annotations": {"list": []}, "panels": self.panels,
            "links": [{"title": "The Club dashboards", "type": "dashboards", "tags": ["the-club"], "asDropdown": True}],
        }
        OUT.mkdir(parents=True, exist_ok=True)
        (OUT / f"{self.uid}.json").write_text(json.dumps(board, indent=2) + "\n")


def app_variable(include_all=True):
    return {"name": "application", "label": "Service", "type": "query", "datasource": PROM,
            "query": {"query": "label_values(http_server_requests_seconds_count, application)", "refId": "A"},
            "definition": "label_values(http_server_requests_seconds_count, application)",
            "includeAll": include_all, "multi": True, "current": {"text": "All", "value": "$__all"}, "refresh": 2,
            "sort": 1}


UP_MAPPINGS = [{"type": "value", "options": {"0": {"text": "DOWN", "color": "red"}, "1": {"text": "UP", "color": "green"}}}]
NO_ACTUATOR = 'uri!~"/actuator.*"'
APP = 'application=~"$application"'

# ---------------------------------------------------------------- Overview
o = Board("theclub-overview", "The Club — Overview",
          "Health, traffic, errors, latency and JVM/DB resources of every service.", [app_variable()])
o.row("Availability")
for svc in ["gateway-service", "user-service", "auth-service", "activity-service", "club-service", "event-service",
            "notification-service", "discovery-service"]:
    o.stat(svc, f'up{{job="theclub-services",service="{svc}"}}', w=3, h=3, mappings=UP_MAPPINGS,
           thresholds=[{"color": "red", "value": None}, {"color": "green", "value": 1}])
o.row("Traffic & errors")
o.timeseries("Requests per second", [(f'sum by (application) (rate(http_server_requests_seconds_count{{{APP},{NO_ACTUATOR}}}[$__rate_interval]))', "{{application}}")], unit="reqps")
o.timeseries("5xx error ratio", [(f'sum by (application) (rate(http_server_requests_seconds_count{{{APP},{NO_ACTUATOR},status=~"5.."}}[$__rate_interval])) / sum by (application) (rate(http_server_requests_seconds_count{{{APP},{NO_ACTUATOR}}}[$__rate_interval]))', "{{application}}")], unit="percentunit")
o.timeseries("Latency p50 / p95 / p99", [
    (f'histogram_quantile({q}, sum by (application, le) (rate(http_server_requests_seconds_bucket{{{APP},{NO_ACTUATOR}}}[$__rate_interval])))', f"{{{{application}}}} p{int(q * 100)}")
    for q in (0.5, 0.95, 0.99)], unit="s")
o.timeseries("Slowest endpoints (p95)", [(f'topk(8, histogram_quantile(0.95, sum by (application, method, uri, le) (rate(http_server_requests_seconds_bucket{{{APP},{NO_ACTUATOR}}}[$__rate_interval]))))', "{{application}} {{method}} {{uri}}")], unit="s")
o.timeseries("Responses by status", [(f'sum by (status) (rate(http_server_requests_seconds_count{{{APP},{NO_ACTUATOR}}}[$__rate_interval]))', "{{status}}")], unit="reqps", stack=True)
o.timeseries("Outbound calls between services (Feign/HTTP clients)", [(f'sum by (application, clientName) (rate(http_client_requests_seconds_count{{{APP}}}[$__rate_interval]))', "{{application}} → {{clientName}}")], unit="reqps")
o.row("JVM & database")
o.timeseries("Heap used", [(f'sum by (application) (jvm_memory_used_bytes{{{APP},area="heap"}})', "{{application}}")], unit="bytes")
o.timeseries("GC pause time per second", [(f'sum by (application) (rate(jvm_gc_pause_seconds_sum{{{APP}}}[$__rate_interval]))', "{{application}}")], unit="s")
o.timeseries("CPU usage", [(f'max by (application) (process_cpu_usage{{{APP}}})', "{{application}}")], unit="percentunit")
o.timeseries("DB connections (active / pending)", [
    (f'sum by (application) (hikaricp_connections_active{{{APP}}})', "{{application}} active"),
    (f'sum by (application) (hikaricp_connections_pending{{{APP}}})', "{{application}} pending")])
o.row("Logs")
o.logs("Errors and warnings (all services)", '{project=~".+", level=~"ERROR|WARN"}')
o.write()

# ---------------------------------------------------------------- Business
b = Board("theclub-business", "The Club — Business",
          "Product activity: accounts, clubs, events, payments, email and Strava. Counters are per selected interval.")
inc = "[$__rate_interval]"
b.row("Totals in the selected time range")
b.stat("Sign-ups", "sum(increase(theclub_users_registrations_total[$__range]))", w=4)
b.stat("Successful logins", 'sum(increase(theclub_auth_logins_total{outcome="success"}[$__range]))', w=4)
b.stat("Clubs created", "sum(increase(theclub_clubs_created_total[$__range]))", w=4)
b.stat("Event registrations", "sum(increase(theclub_events_registrations_total[$__range]))", w=4)
b.stat("Payments succeeded", 'sum(increase(theclub_payments_transitions_total{status="succeeded"}[$__range]))', w=4)
b.stat("Check-ins", "sum(increase(theclub_events_checkins_total[$__range]))", w=4)
b.row("Accounts")
b.timeseries("Logins by outcome", [(f"sum by (outcome) (increase(theclub_auth_logins_total{inc}))", "{{outcome}}")], stack=True)
b.timeseries("Sign-ups, verifications, password resets", [
    (f"sum(increase(theclub_users_registrations_total{inc}))", "sign-ups"),
    (f"sum(increase(theclub_users_email_verifications_total{inc}))", "emails verified"),
    (f"sum by (stage) (increase(theclub_users_password_resets_total{inc}))", "password reset {{stage}}"),
    (f"sum(increase(theclub_users_password_changes_total{inc}))", "password changes")])
b.timeseries("Token refreshes and logouts", [
    (f"sum by (outcome) (increase(theclub_auth_refreshes_total{inc}))", "refresh {{outcome}}"),
    (f"sum(increase(theclub_auth_logouts_total{inc}))", "logouts"),
    (f"sum(increase(theclub_auth_sessions_revoked_total{inc}))", "sessions revoked")])
b.row("Clubs")
b.timeseries("Clubs, joins and posts", [
    (f"sum(increase(theclub_clubs_created_total{inc}))", "clubs created"),
    (f"sum(increase(theclub_clubs_joins_total{inc}))", "members joined"),
    (f"sum by (type) (increase(theclub_clubs_posts_total{inc}))", "posts {{type}}")])
b.timeseries("Owner verification", [(f"sum by (action) (increase(theclub_clubs_owner_verifications_total{inc}))", "{{action}}")], stack=True)
b.row("Events & payments")
b.timeseries("Registrations by type", [(f"sum by (type) (increase(theclub_events_registrations_total{inc}))", "{{type}}")], stack=True)
b.timeseries("Payments by status", [(f"sum by (status) (increase(theclub_payments_transitions_total{inc}))", "{{status}}")], stack=True)
b.timeseries("Check-ins and points", [
    (f"sum(increase(theclub_events_checkins_total{inc}))", "check-ins"),
    (f"sum(increase(theclub_events_points_awarded_total{inc}))", "points awarded")])
b.timeseries("Payment reconciliation", [(f"sum by (outcome) (increase(theclub_payments_reconciled_total{inc}))", "{{outcome}}")])
b.row("Email")
b.timeseries("Emails sent and failed", [
    (f"sum by (template) (increase(theclub_emails_sent_total{inc}))", "sent {{template}}"),
    (f"sum by (reason) (increase(theclub_emails_failed_total{inc}))", "failed ({{reason}})")],
             description="notification-service: theclub_emails_sent_total{provider,template}, theclub_emails_failed_total{provider,reason}.")
b.timeseries("Email outbox pending", [("max(theclub_email_outbox_pending)", "pending")],
             description="Emails waiting to be sent or retried (theclub_email_outbox_pending).")
b.row("Strava")
b.timeseries("Strava API calls by outcome", [(f"sum by (outcome) (increase(theclub_strava_api_calls_total{inc}))", "{{outcome}}")], stack=True,
             description="activity-service: theclub_strava_api_calls_total{endpoint,outcome}.")
b.timeseries("Strava webhook events", [(f"sum by (outcome) (increase(theclub_strava_webhook_events_total{inc}))", "{{outcome}}")], stack=True)
b.timeseries("Strava rate-limit usage", [("max by (kind, window) (theclub_strava_ratelimit_usage / (theclub_strava_ratelimit_limit > 0))", "{{kind}} {{window}}")],
             unit="percentunit", description="Share of Strava's 15-minute and daily request quotas used (from Strava's response headers).")
b.write()

# ---------------------------------------------------------------- Gateway
g = Board("theclub-gateway", "The Club — Gateway", "Edge traffic: routes, upstream latency and rate limiting.")
GW = 'application="gateway-service"'
g.row("Edge")
g.timeseries("Requests per route", [("sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count[$__rate_interval]))", "{{routeId}}")], unit="reqps", stack=True)
g.timeseries("Upstream latency p95 per route", [("histogram_quantile(0.95, sum by (routeId, le) (rate(spring_cloud_gateway_requests_seconds_bucket[$__rate_interval])))", "{{routeId}}")], unit="s",
             description="Time from the gateway receiving a request to the service responding.")
g.timeseries("Responses by status (gateway)", [(f'sum by (status) (rate(http_server_requests_seconds_count{{{GW}}}[$__rate_interval]))', "{{status}}")], unit="reqps", stack=True)
g.timeseries("Rate-limited requests (429)", [("sum by (bucket) (increase(theclub_gateway_rate_limited_total[$__rate_interval]))", "{{bucket}}")],
             description="strict = login/register/password reset/verification; general = every API call.")
g.timeseries("Upstream errors by route", [('sum by (routeId, httpStatusCode) (rate(spring_cloud_gateway_requests_seconds_count{httpStatusCode=~"5.."}[$__rate_interval]))', "{{routeId}} {{httpStatusCode}}")], unit="reqps")
g.timeseries("Gateway heap and CPU", [(f'sum(jvm_memory_used_bytes{{{GW},area="heap"}})', "heap bytes")], unit="bytes")
g.write()
print("dashboards written to", OUT)
