FROM openjdk:22 as builder
WORKDIR /alvarium


ADD mill build.sc ./
ARG name
RUN ./mill $name.prepareOffline

ADD lib lib
ADD alvarium-node alvarium-node

ADD $name $name
RUN ./mill $name.assembly

ADD res res
ADD config config

FROM ubuntu:latest as runtime
WORKDIR /alvarium
ARG name

RUN apt update
RUN apt install -y openjdk-21-jdk

COPY --from=builder /alvarium/out/$name/assembly.dest/out.jar .
COPY --from=builder /alvarium/config config
COPY --from=builder /alvarium/res res
#ENTRYPOINT java -agentpath:/jprofiler/bin/linux-x64/libjprofilerti.so=port=8849 -jar out.jar
ENTRYPOINT java -jar out.jar
