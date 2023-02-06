#!/bin/bash
overcommit_memory=$(sysctl vm.overcommit_memory)
overcommit_ratio=$(sysctl vm.overcommit_ratio)
echo "changing from $overcommit_memory to "
sysctl -w vm.overcommit_memory=2 

echo "changing from $overcommit_ratio to "
sysctl -w vm.overcommit_ratio=75

#choom -p $PID --adjust -1000