#!/bin/bash

#scripts=("run-zest.sh" "run-zest-slow-10240-2.sh" "run-zest-slow-10240-3.sh" "run-zest-slow-10240-4.sh" "run-zest-slow-10240-5.sh" "run-zest-slow-40960-1.sh" "run-zest-slow-40960-2.sh" "run-zest-slow-40960-3.sh" "run-zest-slow-40960-4.sh" "run-zest-slow-40960-5.sh" "run-art-slow-10240-1.sh" "run-art-slow-40960-1.sh")

scripts=(
# "run-art.sh"
# "run-random.sh"
# "run-zest.sh"
"run-rank-1.sh"
"run-rank-2.sh"
"run-rank-3.sh"
"run-rank-5.sh"
"run-rank-10.sh"
)

for script in "${scripts[@]}"; do
  echo "Running $script in a sub-shell..."

  (
    ./"$script"
  )

  if [ $? -eq 0 ]; then
    echo "$script executed successfully."
  else
    echo "$script failed to execute."
  fi
done

echo "All scripts have been executed."