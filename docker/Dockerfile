# Fabric mod dev container for Minecraft 26.3 (Java 25 / Gradle 9.8.2 / Loom 1.19.4)
#
# Everything the toolchain needs lives inside this image:
#   * Eclipse Temurin JDK 25  (Minecraft 26.3 requires Java 25)
#   * Gradle 9.8.2            (the version the official template pins)
#   * Xvfb + Mesa + noVNC     (so runClient can be played/observed on a headless VM)
FROM eclipse-temurin:25-jdk-noble

ARG UID=1002
ARG GID=1003
ARG GRADLE_VERSION=9.8.2

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LANGUAGE=en_US:en \
    GRADLE_USER_HOME=/home/dev/.gradle \
    PATH=/opt/gradle/bin:$PATH \
    DISPLAY=:99 \
    LIBGL_ALWAYS_SOFTWARE=1 \
    MESA_LOADER_DRIVER_OVERRIDE=llvmpipe \
    XDG_RUNTIME_DIR=/tmp/runtime-dev \
    VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json

RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl unzip zip git procps less vim-tiny \
      # --- software OpenGL stack (Mesa llvmpipe) ---
      libgl1 libglx-mesa0 libgl1-mesa-dri libegl1 libglu1-mesa mesa-utils \
      # --- software Vulkan stack (Mesa lavapipe) - MC 26.x renders via renderpearl ---
      libvulkan1 mesa-vulkan-drivers vulkan-tools \
      # --- X11 / window manager bits the LWJGL GLFW backend links against ---
      libx11-6 libxext6 libxrender1 libxrandr2 libxi6 libxcursor1 \
      libxinerama1 libxxf86vm1 libxtst6 libxfixes3 libxkbcommon0 \
      libasound2t64 \
      fonts-dejavu-core fontconfig \
      # --- headless display + remote viewing ---
      xvfb x11vnc novnc websockify scrot \
    && rm -rf /var/lib/apt/lists/*

# Gradle distribution (wrapper also exists in the project; this gives us a
# `gradle` binary for bootstrapping/IDE-free use).
RUN curl -fsSL -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
 && unzip -q /tmp/gradle.zip -d /opt \
 && ln -s "/opt/gradle-${GRADLE_VERSION}" /opt/gradle \
 && rm -f /tmp/gradle.zip \
 && gradle --version

# Non-root user whose uid/gid match the host user, so files created in the
# bind-mounted project stay owned by you.
RUN groupadd -g "${GID}" dev \
 && useradd -m -u "${UID}" -g "${GID}" -s /bin/bash dev \
 && mkdir -p /workspace /home/dev/.gradle /tmp/runtime-dev \
 && chmod 700 /tmp/runtime-dev \
 && chown -R "${UID}:${GID}" /workspace /home/dev /tmp/runtime-dev

COPY start-client.sh /usr/local/bin/start-client.sh
RUN chmod +x /usr/local/bin/start-client.sh

USER dev
WORKDIR /workspace

# Default: idle container you can `docker compose exec` into.
CMD ["sleep", "infinity"]
