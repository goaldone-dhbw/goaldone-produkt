#!/bin/bash
set -e

# Check if mprocs is installed
if ! command -v mprocs &> /dev/null; then
    echo "=========================================================="
    echo "Error: 'mprocs' is required to run the development setup."
    echo "Please install it using Homebrew:"
    echo ""
    echo "    brew install mprocs"
    echo ""
    echo "=========================================================="
    exit 1
fi

# 1. Source the .env file if it exists
if [ -f .env ]; then
    echo "Loading environment variables from .env file..."
    # Using export to make them available to sub-processes (like mprocs)
    export $(grep -v '^#' .env | xargs)
else
    echo "Warning: .env file not found. Ensure required Zitadel variables are set."
    sleep 2
fi

echo "Generate env.js for frontend..."
cd frontend
npm run setup-env
echo "Cleaning up old generated API sources..."
rm -r src/app/api
echo "Generating frontend API sources..."
npm run generate-api
cd ..

# 2. Run code generation sequentially first
echo "Generating backend sources..."
cd backend
./mvnw clean generate-sources
cd ..

# 3. Launch mprocs TUI
echo "Starting mprocs for backend and frontend..."
# mprocs will automatically load mprocs.yaml from the current directory
mprocs
