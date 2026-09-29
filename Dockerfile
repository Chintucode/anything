# Builds Anything into one small image: the web app is compiled, dropped inside the
# Spring Boot jar, and served from the same URL as the API.
#
#   docker build -t anything .
#   docker run -p 8080:8080 -e DATABASE_URL='postgresql://...' anything

# ---- 1. The web app ------------------------------------------------------------
FROM node:22-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

# ---- 2. The server, with the web app inside it ----------------------------------
FROM maven:3.9-eclipse-temurin-21 AS server
WORKDIR /server
COPY server/pom.xml ./
# Dependencies first, in their own layer, so a code change doesn't re-download them.
RUN mvn -q -B dependency:go-offline
COPY server/src ./src
COPY --from=web /web/dist ./src/main/resources/static
# Tests run on your machine before you push (./mvnw test), not here.
RUN mvn -q -B -DskipTests package && cp target/anything-server-*.jar /app.jar

# ---- 3. What actually runs --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=server /app.jar app.jar
USER app

# The free server has 512 MB and a sliver of a CPU. These settings trade a little
# top speed for a faster start and a smaller memory footprint.
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=Asia/Kolkata \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
