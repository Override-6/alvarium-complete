#!/usr/bin/env bash

set -eu

minikube start
minikube addons enable metrics-server
minikube dashboard > /dev/null &

minikube mount config:/config > /dev/null &
minikube mount data/:/ML-data > /dev/null &
