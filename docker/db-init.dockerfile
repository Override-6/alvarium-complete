FROM jbergknoff/postgresql-client

RUN apk add bash
COPY --chmod=777 docker/db-init.sh .

ENTRYPOINT ./db-init.sh