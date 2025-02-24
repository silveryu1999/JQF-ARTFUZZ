#!/bin/bash

#export JVM_OPTS="$JVM_OPTS -Djqf.ei.TOTALLY_RANDOM=true"
#export JVM_OPTS="$JVM_OPTS -Djqf.getARTInput=true"
#export JVM_OPTS="$JVM_OPTS -Djqf.ei.FSCS_K=10"
#export JVM_OPTS="$JVM_OPTS -Djqf.ei.EXECUTED_INPUTS_TO_BE_IGNORE_PERCENTAGE=0"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=100000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=17"
../../bin/jqf-art -c .:$(../../scripts/classpath.sh):closure-compiler-v20180204.jar:commons-io-2.18.0.jar -l -b CompilerTest testWithGenerator jqf-zest/