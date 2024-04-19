#!/usr/bin/env bash

set -xeu

eval $(minikube docker-env)

docker build -t alvarium-worker:latest -f docker/scala-node.dockerfile --build-arg name=worker .&
A=$!
docker build -t alvarium-master:latest -f docker/scala-node.dockerfile --build-arg name=master .&

wait $! $A

kubectl set image deployments/alvarium-workers alvarium-worker=alvarium-worker:latest
kubectl delete pod master || true
kubectl apply -f docker/kubernetes.yml

echo DONE.