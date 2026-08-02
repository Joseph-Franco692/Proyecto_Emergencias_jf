FROM alpine:3.22
ARG PB_VERSION=0.39.9
RUN apk add --no-cache ca-certificates unzip wget \
    && wget -q "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_linux_amd64.zip" -O /tmp/pb.zip \
    && unzip /tmp/pb.zip -d /pb \
    && rm /tmp/pb.zip
WORKDIR /pb
COPY pb_migrations /pb/pb_migrations
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 8090
ENTRYPOINT ["/entrypoint.sh"]
