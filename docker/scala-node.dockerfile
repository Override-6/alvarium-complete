FROM openjdk:22 as builder
WORKDIR /alvarium


ADD mill build.sc ./
ARG name
RUN ./mill $name.resolvedIvyDeps

ADD lib lib
ADD alvarium-node alvarium-node

ADD $name $name
RUN ./mill $name.assembly

ADD res res
ADD config config

FROM eclipse-temurin:22-alpine as runtime
WORKDIR /alvarium
ARG name

COPY --from=builder /alvarium/out/$name/assembly.dest/out.jar .
COPY --from=builder /alvarium/config config
COPY --from=builder /alvarium/res res
ENTRYPOINT java -Dmule.security.model=custom -Drtf.allow.TLSv1.2 -jar out.jar