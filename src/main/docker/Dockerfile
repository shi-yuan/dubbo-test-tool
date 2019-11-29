FROM openjdk:8-jdk-alpine

ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS \
    JAVA_OPTS="-Xmx512m -Xms256m"

RUN adduser -D -s /bin/sh dubbo
WORKDIR /home/dubbo

ADD entrypoint.sh entrypoint.sh
RUN chmod 755 entrypoint.sh && chown dubbo:dubbo entrypoint.sh
USER dubbo

ENTRYPOINT ["./entrypoint.sh"]

EXPOSE 9999

ADD *.jar app.jar
