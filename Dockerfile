FROM openjdk:24-jdk-slim

WORKDIR /app

COPY . .

RUN ./gradlew build

CMD ["java", "-jar", "build/libs/pcpartsscraper-0.0.1-SNAPSHOT.jar"]