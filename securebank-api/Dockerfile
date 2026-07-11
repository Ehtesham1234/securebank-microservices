# ── STAGE 1: BUILD ──────────────────────────────────────────────
# We need a full JDK to compile Java code
# alpine = a tiny Linux distribution (much smaller than Ubuntu)
FROM eclipse-temurin:25-jdk-alpine AS builder

# Set working directory inside the container
# All subsequent commands run from here
WORKDIR /app

# Copy Maven wrapper files FIRST (before source code)
# Why: Docker caches each line. If pom.xml hasn't changed,
# Docker skips re-downloading all dependencies on the next build.
# This makes rebuilds after code changes MUCH faster.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Make mvnw executable (it's a shell script)
RUN chmod +x mvnw

# Download all dependencies into the container
# -B = batch mode (no progress bars, cleaner logs)
RUN ./mvnw dependency:go-offline -B

# Now copy your actual source code
# (Done AFTER dependency download for better caching)
COPY src src

# Build the JAR, skip tests (tests run in CI separately)
RUN ./mvnw package -DskipTests -B

# ── STAGE 2: RUNTIME ────────────────────────────────────────────
# We only need JRE (Java Runtime) to RUN — not full JDK to compile
# This stage creates a MUCH smaller final image (~200MB vs ~600MB)
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# Create a non-root user for security
# If someone hacks your app, they get "securebank" user access,
# NOT root access to the entire machine
RUN addgroup -S securebank && adduser -S securebank -G securebank

# Copy ONLY the compiled JAR from the builder stage
# The build tools, source code, etc. are NOT included here
COPY --from=builder /app/target/*.jar app.jar

# Create uploads directory owned by our non-root user
RUN mkdir -p uploads && chown securebank:securebank uploads

# Switch to non-root user
USER securebank

# Tell Docker this container listens on port 8081
# (This is documentation — doesn't actually open the port)
EXPOSE 8081

# Command to run when the container starts
# -Djava.security.egd speeds up UUID/token generation in Linux containers
ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]