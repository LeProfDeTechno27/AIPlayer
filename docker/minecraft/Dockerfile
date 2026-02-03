FROM itzg/minecraft-server:java21

USER root

COPY ["Ressource minecraft/mods/", "/opt/bootstrap/mods/"]
COPY server-config/server.properties /opt/bootstrap/server.properties
COPY docker/minecraft/init-copy-assets.sh /docker-entrypoint-initmc.d/10-copy-assets.sh

RUN chmod +x /docker-entrypoint-initmc.d/10-copy-assets.sh

USER 1000