#!/usr/bin/env bash

set -xeu

eval $(minikube docker-env)


kubectl delete pod emitter-trusted&
kubectl delete pod emitter-untrusted&
kubectl delete pod storage&
kubectl delete deployments alvarium-workers-tpm&
kubectl delete deployments alvarium-workers-no-tpm&

docker build -t db-init:latest -f docker/db-init.dockerfile .&
docker build -t mosquitto-client:latest -f docker/mosquitto-client.dockerfile .&
docker build -t alvarium-worker:latest -f docker/scala-node.dockerfile --build-arg name=worker .&
docker build -t alvarium-emitter:latest -f docker/scala-node.dockerfile --build-arg name=emitter .&
docker build -t alvarium-storage:latest -f docker/scala-node.dockerfile --build-arg name=storage .&

wait

kubectl set image deployments/alvarium-workers-tpm alvarium-worker=alvarium-worker:latest || true
kubectl set image deployments/alvarium-workers-no-tpm alvarium-worker=alvarium-worker:latest || true

kubectl apply -f docker/kubernetes.yml

echo DONE.