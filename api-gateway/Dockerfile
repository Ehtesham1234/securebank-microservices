FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S securebank \
    && adduser -S securebank -G securebank
RUN apk add --no-cache curl
COPY --from=builder /app/target/*.jar app.jar
RUN chown securebank:securebank app.jar
USER securebank
EXPOSE 8090
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]