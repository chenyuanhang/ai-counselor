FROM eclipse-temurin:8-jre

WORKDIR /app

ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=local \
    JAVA_OPTS="-Xms512m -Xmx1024m"

COPY target/ai-counselor-backend-1.0.0.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.timezone=$TZ -jar /app/app.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE"]
