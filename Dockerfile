FROM eclipse-temurin:25-jdk
WORKDIR /app
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw -DskipTests package
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/target/smort-0.0.1-SNAPSHOT.jar"]