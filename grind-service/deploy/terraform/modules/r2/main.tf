# NEVER add an age-based lifecycle rule to the blob bucket: it can't tell an orphaned upload from a committed blob
# and would delete users' data. Orphaned uploads are removed by the worker's orphaned-blob sweep job.
# Private R2 bucket for encrypted blobs; CORS for PUT from the app only.
# Step 1b: inputs only. Resources are added before the first deploy, never applied without the owner's go-ahead.
