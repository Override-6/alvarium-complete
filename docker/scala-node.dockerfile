FROM openjdk:22 as builder
WORKDIR /alvarium


ADD mill build.sc ./
RUN ./mill resolve _

ADD lib lib
ADD alvarium-node alvarium-node

ARG name
ADD $name $name

RUN ./mill $name.assembly

ADD res res
ADD config config
ADD config-dir-checksum.txt ./
ADD config-file-checksum.txt ./

FROM eclipse-temurin:22-alpine as runtime
WORKDIR /alvarium
ARG name

COPY --from=builder /alvarium/out/$name/assembly.dest/out.jar .
COPY --from=builder /alvarium/config config
COPY --from=builder /alvarium/config-file-checksum.txt .
COPY --from=builder /alvarium/config-dir-checksum.txt .
COPY --from=builder /alvarium/res res
ENTRYPOINT java -jar out.jar