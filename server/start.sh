#!/bin/bash

USER_DIR=""
PORT=8080
IP_ADDRESS=localhost

cmd="java -jar server-1.0-SNAPSHOT-jar-with-dependencies.jar -d $USER_DIR -p $PORT -h $IP_ADDRESS"
echo $cmd
exec $cmd
