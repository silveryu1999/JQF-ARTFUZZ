#!/bin/bash

scripts=(
"run-random.sh"
"run-zest.sh"
"run-rank-0.sh"
"run-rank-cm-0.sh"
"run-rank-25.sh"
"run-rank-cm-25.sh"
"run-rank-50.sh"
"run-rank-cm-50.sh"
"run-rank-75.sh"
"run-rank-cm-75.sh"
"run-rank-100.sh"
"run-rank-cm-100.sh"
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