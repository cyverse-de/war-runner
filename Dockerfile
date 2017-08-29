FROM maven:alpine

RUN apk add --no-cache git tini

ENV CONSUL_TEMPLATE_BASE=https://releases.hashicorp.com/consul-template
ENV CONSUL_TEMPLATE_VERSION=0.16.0
ENV CONSUL_TEMPLATE_SHA256SUM=064b0b492bb7ca3663811d297436a4bbf3226de706d2b76adade7021cd22e156
ENV CONSUL_TEMPLATE_FILE=consul-template_${CONSUL_TEMPLATE_VERSION}_linux_amd64.zip

ADD ${CONSUL_TEMPLATE_BASE}/${CONSUL_TEMPLATE_VERSION}/${CONSUL_TEMPLATE_FILE} .

RUN echo "${CONSUL_TEMPLATE_SHA256SUM}  ${CONSUL_TEMPLATE_FILE}" | sha256sum -c - \
    && unzip ${CONSUL_TEMPLATE_FILE} \
    && mkdir -p /usr/local/bin \
    && mv consul-template /usr/local/bin/consul-template

COPY . /opt/war-runner
RUN cd /opt/war-runner \
    && mvn clean package \
    && cp target/war-runner.jar .

WORKDIR /usr/src/app
