{{/* vim: set filetype=mustache: */}}

{{- define "theclub.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "theclub.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/* Common labels; pass (dict "root" $ "component" "user") */}}
{{- define "theclub.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version | replace "+" "_" }}
app.kubernetes.io/name: {{ include "theclub.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/version: {{ include "theclub.imageTag" .root | quote }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: the-club
{{- with .component }}
app.kubernetes.io/component: {{ . }}
{{- end }}
{{- end -}}

{{- define "theclub.selectorLabels" -}}
app.kubernetes.io/name: {{ include "theclub.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end -}}

{{/* Labels shared by every pod of the release (NetworkPolicy targets) */}}
{{- define "theclub.instanceSelector" -}}
app.kubernetes.io/name: {{ include "theclub.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "theclub.imageTag" -}}
{{- $tag := default .Chart.AppVersion .Values.global.imageTag -}}
{{- if and (eq $tag "latest") (not .Values.global.allowLatestTag) -}}
{{- fail "global.imageTag must be set to the image published by CI (e.g. --set global.imageTag=<git sha>); \"latest\" is not reproducible" -}}
{{- end -}}
{{- $tag -}}
{{- end -}}

{{/* (dict "root" $ "image" "user-service") -> registry/the-club-user-service:tag */}}
{{- define "theclub.image" -}}
{{- printf "%s/the-club-%s:%s" (trimSuffix "/" .root.Values.global.imageRegistry) .image (include "theclub.imageTag" .root) -}}
{{- end -}}

{{/* Kubernetes Service name of a workload: (dict "root" $ "component" "user") */}}
{{- define "theclub.serviceName" -}}
{{- printf "%s-%s" (include "theclub.fullname" .root) .component | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "theclub.secretName" -}}
{{- default (printf "%s-secrets" (include "theclub.fullname" .)) .Values.secrets.name -}}
{{- end -}}

{{- define "theclub.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "theclub.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "theclub.mysqlHost" -}}
{{- default (include "theclub.serviceName" (dict "root" . "component" "mysql")) .Values.databases.mysql.host -}}
{{- end -}}

{{- define "theclub.postgresHost" -}}
{{- default (include "theclub.serviceName" (dict "root" . "component" "postgres")) .Values.databases.postgres.host -}}
{{- end -}}

{{/* Fully qualified in-cluster URL of a service: (dict "root" $ "component" "user") */}}
{{- define "theclub.serviceUrl" -}}
{{- $svc := index .root.Values.services .component -}}
{{- printf "http://%s.%s.svc.cluster.local:%v" (include "theclub.serviceName" .) .root.Release.Namespace $svc.port -}}
{{- end -}}

{{/* The effective configuration of one service: serviceDefaults deep-merged under the service's own values */}}
{{- define "theclub.serviceValues" -}}
{{- $svc := index .root.Values.services .component -}}
{{- toYaml (mergeOverwrite (deepCopy .root.Values.serviceDefaults) (deepCopy $svc)) -}}
{{- end -}}

{{/* Restart pods when the shared configuration changes */}}
{{- define "theclub.configChecksum" -}}
{{- include (print .Template.BasePath "/configmap.yaml") . | sha256sum -}}
{{- end -}}

{{/*
  Every scalable workload as a list of dicts {component, port, replicaCount, autoscaling}: the enabled Spring
  services (merged with serviceDefaults) plus the web app. Used by the HPA, PDB and ServiceMonitor templates.
*/}}
{{- define "theclub.workloads" -}}
{{- $out := list -}}
{{- range $component, $raw := .Values.services -}}
{{- if $raw.enabled -}}
{{- $svc := fromYaml (include "theclub.serviceValues" (dict "root" $ "component" $component)) -}}
{{- $out = append $out (dict "component" $component "port" $svc.port "replicaCount" $svc.replicaCount "autoscaling" $svc.autoscaling "spring" true) -}}
{{- end -}}
{{- end -}}
{{- if .Values.webApp.enabled -}}
{{- $w := .Values.webApp -}}
{{- $out = append $out (dict "component" "web" "port" $w.port "replicaCount" $w.replicaCount "autoscaling" $w.autoscaling "spring" false) -}}
{{- end -}}
{{- toYaml (dict "items" $out) -}}
{{- end -}}
