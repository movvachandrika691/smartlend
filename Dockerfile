# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests=true

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S smartlend && adduser -S smartlend -G smartlend
USER smartlend
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
