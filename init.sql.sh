#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SQL_OUT="$DIR/rinhadebackend/src/main/resources/db/init.sql"

printf "-- SQL init \n" > $SQL_OUT
for i in `ls rinhadebackend/src/main/resources/db/migration/*.sql` ; do
  printf "-- $i" >> $SQL_OUT
  cat $i >> $SQL_OUT
done

printf "\n-- SQL init done\n" >> $SQL_OUT
cp "$SQL_OUT" ./init.sql
cat $SQL_OUT
