#!/usr/bin/env bash

set -eu

minikube start
minikube addons enable metrics-server
minikube dashboard &

minikube mount config:/config > /dev/null &
minikube mount "$(readlink data || echo data)"/:/ML-data > /dev/null &

mkdir -p database
minikube mount database/:/database > /dev/null &
minikube mount ~/jprofiler14/:/jprofiler > /dev/null &
minikube mount ~/.jprofiler14/:/.jprofiler > /dev/null &
