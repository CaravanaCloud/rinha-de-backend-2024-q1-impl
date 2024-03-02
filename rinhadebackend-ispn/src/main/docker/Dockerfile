####
# docker build -f rinhadebackend/src/main/docker/Dockerfile --no-cache --progress=plain -t caravanacloud/rinhadebackend:latest rinhadebackend
# docker run -i --rm -p 9999:9990 --network=host --env-file ./.env.local caravanacloud/rinhadebackend:latest
# docker login --username=caravanacloud
# docker push caravanacloud/rinhadebackend:latest
###

### BUILD STAGE ###
FROM fedora:39 as build

# Root level
USER root
ENV PATH="/usr/bin:${PATH}"
RUN bash -c "dnf install -y zip unzip gcc glibc-devel zlib-devel libstdc++-static"


# Docker
# RUN bash -c "dnf -y install dnf-plugins-core"
# RUN bash -c "dnf -y config-manager --add-repo https://download.docker.com/linux/fedora/docker-ce.repo"
# RUN bash -c "dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin"
# sudo systemctl start docker

## Create User
ARG USERNAME=container-user
ARG USER_UID=1000
ARG USER_GID=$USER_UID
RUN groupadd --gid $USER_GID $USERNAME 
RUN useradd --uid $USER_UID --gid $USER_GID -m $USERNAME -d "/home/$USERNAME"

# User level
USER $USERNAME

## Setup JDK
ARG JAVA_SDK="21.0.2-graal"
RUN curl -s "https://get.sdkman.io" | bash 
RUN bash -c ". $HOME/.sdkman/bin/sdkman-init.sh \
    && sdk install java $JAVA_SDK \
    && sdk default java $JAVA_SDK \
    && sdk install quarkus \
    && sdk install maven \
    "

## Copy source code
RUN mkdir -p "/home/$USERNAME/quarkus-app"
WORKDIR "/home/$USERNAME/quarkus-app"
COPY --chown=$USERNAME . .

## Build
ARG MVN_XOPTS="-Dnative -fn -B -ntp -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"
RUN bash -c ". $HOME/.sdkman/bin/sdkman-init.sh \
    && mvn $MVN_XOPTS install"

### RUNTIME STAGE ###
# TODO FROM scratch
# TODO FROM ghcr.io/graalvm/native-image-community:21
FROM fedora:39

# Create user
ARG USERNAME=container-user
ARG USER_UID=1000
ARG USER_GID=$USER_UID
RUN groupadd --gid $USER_GID $USERNAME 
RUN useradd --uid $USER_UID --gid $USER_GID -m $USERNAME -d "/home/$USERNAME"
USER $USERNAME

# Copy binary
RUN mkdir -p "/home/$USERNAME/quarkus-app"
WORKDIR "/home/$USERNAME/quarkus-app"

ARG CP_FROM="/home/container-user/quarkus-app/target/*-runner"
ARG CP_TO="/home/container-user/quarkus-app/"
RUN mkdir -p "$CP_TO"
COPY --from=build --chown=$USERNAME $CP_FROM $CP_TO

# Container Settings
EXPOSE 9990
ENTRYPOINT /home/container-user/quarkus-app/rinhadebackend-0.0.1-runner
