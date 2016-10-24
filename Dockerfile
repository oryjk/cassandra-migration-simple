FROM java:8-jre
MAINTAINER Qi Yan <yanqi@betalpha.com>

ADD ./target/bar-db-migration.jar /app/
ADD ./target/classes/cassandra /app/cassandra
CMD ["java", "-Xmx768m", "-jar", "/app/bar-db-migration.jar"]

