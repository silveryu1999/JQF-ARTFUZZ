#!/bin/bash

export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_MUTATION=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_MUTATION_TIME_AND_SIZE=true" #M
export JVM_OPTS="$JVM_OPTS -Djqf.ei.USE_WEIGHT_DISTANCE=true" #W
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CUSTOM_ENERGY=false" #E
export JVM_OPTS="$JVM_OPTS -Djqf.ei.USE_HAMMING_DISTANCE=true"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.USE_CROSSOVER=false" #C
export JVM_OPTS="$JVM_OPTS -Djqf.ei.CROSSOVER_PROBABILITY=0"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.VALID_SEED_FIRST=true"
#export JVM_OPTS="$JVM_OPTS -Djqf.ei.TRIAL_LIMIT=200000"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.TIME_LIMIT=3h"
export JVM_OPTS="$JVM_OPTS -Xmx40g"
export JVM_OPTS="$JVM_OPTS -Djqf.ei.MAP_SIZE_POW=15"
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-1/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-2/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-3/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-4/
#../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-5/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-6/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-7/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-8/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-9/
../../bin/jqf-rank -c .:$(../../scripts/classpath.sh):$(./classpath.sh) -b ModelReaderTest testWithGenerator rank-MWC0-10/