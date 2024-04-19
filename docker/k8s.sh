#!/usr/bin/env bash

set -eu

minikube start
minikube addons enable metrics-server
minikube dashboard > /dev/null &

minikube mount docker:/config > /dev/null &
minikube mount data/:/ML-data > /dev/null &