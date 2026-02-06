FROM eclipse-temurin:21-jre-jammy

RUN useradd -u 1000 -m minecraft

WORKDIR /data

COPY ["Ressource minecraft/neoforge-21.1.172/", "/bootstrap/neoforge/"]
COPY ["Ressource minecraft/mods/", "/bootstrap/mods/"]
COPY ["server-config/", "/bootstrap/server-config/"]
COPY ["docker/minecraft/entrypoint.sh", "/entrypoint.sh"]

RUN chmod +x /entrypoint.sh

ENV DATA_DIR=/data \
    BOOTSTRAP_MODS_DIR=/bootstrap/mods \
    BOOTSTRAP_NEOFORGE_DIR=/bootstrap/neoforge \
    BOOTSTRAP_CONFIG_DIR=/bootstrap/server-config


ENTRYPOINT ["/entrypoint.sh"]
