#!/usr/bin/env bash

cd "$(dirname "$0")" || exit
psql -U postgres -f maidendb.sql
