FROM openjdk:8u121-alpine

MAINTAINER Yogev Mets <yogev.metzuyanim@logz.io>

RUN apk add --no-cache --update bash curl vim

ADD target/jmx2logzio-1.0.9-javaagent.jar /jmx2logzio.jar
ADD slf4j-simple-1.7.15.jar /slf4j-simple-1.7.15.jar
# Default Start
CMD java -cp jmx2logzio.jar:slf4j-simple-1.7.15.jar io.logz.jmx2logzio.Jmx2LogzioJolokia application.conf