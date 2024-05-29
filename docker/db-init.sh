#!/usr/bin/env bash

DSN=postgresql://alvarium:1234@database:5432

set -xeu

psql $DSN <<< "CREATE DATABASE database;" || true

cat << EOF | psql $DSN/database

DROP TABLE IF EXISTS alvarium_annotation;
DROP TABLE IF EXISTS data_info;

CREATE TABLE alvarium_annotation(
  id varchar PRIMARY KEY,
  tag varchar,
  type varchar,
  is_satisfied boolean,
  image_hash varchar,
  host varchar,
  timestamp varchar
);


CREATE TABLE data_info(
  image_hash varchar,
  label varchar
);


EOF
