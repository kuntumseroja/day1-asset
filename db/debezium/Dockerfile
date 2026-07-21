FROM curlimages/curl:8.10.1
COPY outbox-connector.json /config/outbox-connector.json
COPY register-connector.sh /register-connector.sh
RUN chmod +x /register-connector.sh
ENTRYPOINT ["/register-connector.sh"]
