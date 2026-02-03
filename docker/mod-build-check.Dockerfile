FROM gradle:8.14.3-jdk21
WORKDIR /workspace/aiplayer-mod
COPY aiplayer-mod/ /workspace/aiplayer-mod/
RUN gradle --no-daemon clean build