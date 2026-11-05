FROM node:24.22.2-bookworm-slim@sha256:a43f7fc3e52e87d12a905f46eea0275def84ca8f2981a5b53fed44d7fb398ee0
WORKDIR /app
COPY --chown=node:node server.mjs openapi.json ./
USER node
CMD ["node","server.mjs"]
