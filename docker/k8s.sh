#!/usr/bin/env bash

set -eu

minikube start
minikube addons enable metrics-server
minikube dashboard > /dev/null &

minikube mount config:/config > /dev/null &
minikube mount data/:/ML-data > /dev/null &

mkdir -p database
minikube mount database/:/database > /dev/null &
minikube mount /home/intern01/jprofiler14/:/jprofiler > /dev/null &
minikube mount /home/intern01/.jprofiler14/:/.jprofiler > /dev/null &
