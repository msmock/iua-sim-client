# Use a minimal Quarkus base image
FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0

# Set the working directory
WORKDIR /app

# Create the application directory
RUN mkdir -p /app && \
    chown 1001:root /app && \
    chmod g+rwX /app

# Copy the Quarkus native binary
COPY --chown=1001:root --chmod=0755 target/iua-sim-client-1.0.0-SNAPSHOT-runner /app/application

# Expose the HTTP port
EXPOSE 8080

# Command to run the application
CMD ["./application"]
