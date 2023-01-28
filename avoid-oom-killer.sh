#!/bin/bash
sysctl -w vm.overcommit_memory=2 
sysctl -w vm.overcommit_ratio=75

#choom -p $PID --adjust -1000