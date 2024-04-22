#!/usr/bin/env bash

DSN=postgresql://alvarium:1234@database:5432

set -xeu

psql $DSN <<< "CREATE DATABASE database;" || true

cat << EOF | psql $DSN/database
CREATE TABLE IF NOT EXISTS alvarium_annotation(
  action_type varchar,
  id varchar PRIMARY KEY,
  key varchar,
  hash varchar,
  host varchar,
  tag varchar,
  layer varchar,
  kind varchar,
  signature varchar,
  is_satisfied boolean,
  timestamp timestamp
);

TRUNCATE TABLE alvarium_annotation
EOF

