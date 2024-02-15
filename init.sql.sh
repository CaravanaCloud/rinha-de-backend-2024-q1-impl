#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SQL_OUT="$DIR/rinhadebackend/src/main/resources/db/init.sql"

echo "-- SQL init" > $SQL_OUT
for i in `ls rinhadebackend/src/main/resources/db/migration/*.sql` ; do
  echo "-- $i" >> $SQL_OUT
  cat $i >> $SQL_OUT
done

echo "-- SQL init done" >> $SQL_OUT
cat $SQL_OUT
