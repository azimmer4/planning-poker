FROM node:22-alpine
WORKDIR /app
COPY package.json ./
COPY src ./src
COPY seed ./seed
ENV PORT=8081
EXPOSE 8081
CMD ["node", "src/server.js"]
