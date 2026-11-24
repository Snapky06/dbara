FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY src ./src
COPY lib ./lib
RUN mkdir -p bin && javac -cp "lib/*" -d bin src/bd/*.java src/api/*.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/bin ./bin
COPY --from=build /app/lib ./lib
COPY src/web ./src/web

EXPOSE 8080
CMD ["java", "-cp", "bin:lib/*", "api.ServidorApi"]