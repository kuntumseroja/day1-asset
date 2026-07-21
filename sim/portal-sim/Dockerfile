FROM node:20-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY portal-sim/package.json ./
RUN npm install --production --fetch-retries=5 --fetch-retry-mintimeout=20000
COPY portal-sim/src ./src
COPY scripted-day.json ./scripted-day.json
ENV SCRIPT_PATH=/app/scripted-day.json
EXPOSE 8083
CMD ["npm", "start"]
