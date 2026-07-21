FROM node:20-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY package.json ./
RUN npm install --production --fetch-retries=5 --fetch-retry-mintimeout=20000
COPY src ./src
EXPOSE 8082
CMD ["npm", "start"]
