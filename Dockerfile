FROM docker.io/gzmidoo/node:22-alpine AS wecom-bridge

WORKDIR /opt/wecom-bot-bridge
COPY wecom-bot-bridge/package.json wecom-bot-bridge/package-lock.json ./
RUN npm ci --omit=dev
COPY wecom-bot-bridge/src ./src

FROM docker.io/gzmidoo/metersphere:alpine-openjdk21-jre

LABEL maintainer="FIT2CLOUD <support@fit2cloud.com>"

ARG MS_VERSION=dev
ARG DEPENDENCY=backend/app/target/dependency

COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app

COPY --from=wecom-bridge /usr/local/bin/node /usr/local/bin/node
COPY --from=wecom-bridge /opt/wecom-bot-bridge /opt/wecom-bot-bridge
COPY deploy/run-with-wecom-bridge.sh /deployments/run-with-wecom-bridge.sh

# 静态文件
COPY backend/app/src/main/resources/static /app/static
ADD frontend/public /app/static


ENV JAVA_CLASSPATH=/app:/opt/jmeter/lib/ext/*:/app/lib/*
ENV JAVA_MAIN_CLASS=io.metersphere.Application
ENV AB_OFF=true
ENV MS_VERSION=${MS_VERSION}
ENV JAVA_OPTIONS="-Dfile.encoding=utf-8 -Djava.awt.headless=true --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED"

RUN apk add --no-cache libstdc++ libgcc \
    && node --version \
    && echo -n "${MS_VERSION}" > /tmp/MS_VERSION \
    && sed -i 's/\r$//' /deployments/run-with-wecom-bridge.sh \
    && chmod 755 /deployments/run-with-wecom-bridge.sh

CMD ["/deployments/run-with-wecom-bridge.sh"]
