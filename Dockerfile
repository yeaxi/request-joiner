FROM oracle/graalvm-ce:19.3.1-java11 as graalvm
RUN gu install native-image

COPY . /home/app/request-joiner
WORKDIR /home/app/request-joiner

RUN native-image --no-server --static -cp build/libs/request-joiner-*-all.jar

FROM scratch
EXPOSE 8080
COPY --from=graalvm /home/app/request-joiner/request-joiner /app/request-joiner
ENTRYPOINT ["/app/request-joiner", "-Djava.library.path=/app"]
