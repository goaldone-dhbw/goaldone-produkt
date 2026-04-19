#!/bin/sh
set -e

# Substitute runtime environment variables into env.js from template
envsubst < /usr/share/nginx/html/assets/env.js.template \
         > /usr/share/nginx/html/assets/env.js

# Substitute BACKEND_HOST into nginx config from template
envsubst '${BACKEND_HOST}' < /etc/nginx/templates/default.conf.template \
                            > /etc/nginx/conf.d/default.conf

# Start nginx in foreground mode
exec nginx -g 'daemon off;'
