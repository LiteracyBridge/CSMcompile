#!/usr/bin/env bash

verbose=false
include=N
list=

function build() {
  src=$1
  base=${src%%.*}
  yaml=${base}.yaml
  csm=${base}.csm
  inc=${base}_csm.h

#  if [ ${verbose} ]; then
#    echo "Base: ${base}"
#    echo "yaml: ${yaml}"
#    echo " csm: ${csm}"
#    echo " inc: ${inc}"
#  fi

  $verbose && echo java -jar build/libs/CSMcompile-1.1-all.jar ${list} $yaml
  java -jar build/libs/CSMcompile-1.1-all.jar ${list} $yaml
  if [ "${include}" == "Y" ]; then
    $verbose && echo "xxd -i ${csm} > ${inc}"
    xxd -i ${csm} > ${inc}
  fi
}

for f in "$@"; do
  if [ "$f" == "--inc" ] ; then
    include=Y
  elif [ "$f" == "-i" ] ; then
    include=Y
  elif [ "$f" == "-v" ] ; then
    verbose=true
  elif [ "$f" == "--list" ] ; then
    list="--list"
  else
    build $f
  fi
done
