#!/usr/bin/env bash

set -xeu

eval $(minikube docker-env)

docker build -t alvarium-worker:latest -f docker/scala-node.dockerfile --build-arg name=worker .&
A=$!
docker build -t alvarium-master:latest -f docker/scala-node.dockerfile --build-arg name=master .&
B=$!
docker build -t db-init:latest -f docker/db-init.dockerfile .&
C=$!
docker build -t mosquitto-client:latest -f docker/mosquitto-client.dockerfile .&

wait $! $A $B $C

kubectl set image deployments/alvarium-workers-tpm alvarium-worker=alvarium-worker:latest || true
kubectl set image deployments/alvarium-workers-no-tpm alvarium-worker=alvarium-worker:latest || true
kubectl delete pod master || true
kubectl apply -f docker/kubernetes.yml

echo DONE.