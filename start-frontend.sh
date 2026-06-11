#!/bin/bash
set -e

NODE=/usr/share/cursor/resources/app/resources/helpers/node
FRONTEND_DIR="$(dirname "$0")/frontend"

cd "$FRONTEND_DIR"
echo "Starting frontend at http://localhost:3000"
"$NODE" node_modules/.bin/next dev
