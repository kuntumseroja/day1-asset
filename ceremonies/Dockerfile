# Ceremonies dry-run container — SoftHSM2 + pkcs11-tool + ssss + pandoc
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    softhsm2 \
    opensc \
    libengine-pkcs11-openssl1.1 \
    ssss \
    openssl \
    pandoc \
    texlive-latex-base \
    make \
    bash \
    ca-certificates \
  && rm -rf /var/lib/apt/lists/*

ENV PKCS11_MODULE=/usr/lib/softhsm/libsofthsm2.so
ENV SOFTHSM2_CONF=/ceremony/softhsm/softhsm2.conf
ENV CEREMONY_ID=KC-01

WORKDIR /ceremony
COPY . /ceremony/

RUN chmod +x scripts/*.sh scripts/lib/common.sh

CMD ["make", "dry-run"]
