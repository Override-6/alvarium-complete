FROM alpine:latest

RUN apk add mosquitto-clients jq

ENTRYPOINT mosquitto_sub -v -t alvarium-topic -h mosquitto-server | cut -d " " -f2 | jq -r '.content |= @base64d' | jq -r '.content |= fromjson'
