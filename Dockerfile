# syntax=docker/dockerfile:1

# ============================================================
# STAGE 1: compilar y empaquetar el WAR (Maven + JDK 21)
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Capa de dependencias cacheable: solo se invalida si cambia el pom.xml
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src
# Ejecuta los tests unitarios y genera target/order-system.war y target/jdbc-driver/postgresql.jar
RUN mvn -B clean package

# ============================================================
# STAGE 2: WildFly 41 (Jakarta EE 11) + driver + datasource + WAR
# ============================================================
FROM quay.io/wildfly/wildfly:41.0.1.Final-jdk21

# Driver PostgreSQL como modulo de WildFly
COPY --chown=jboss:jboss docker/wildfly/modules/org/postgresql/main/module.xml ${JBOSS_HOME}/modules/org/postgresql/main/module.xml
COPY --from=build --chown=jboss:jboss /build/target/jdbc-driver/postgresql.jar ${JBOSS_HOME}/modules/org/postgresql/main/postgresql.jar

# Driver + datasource + context-root, aplicados sobre standalone.xml con el servidor embebido
COPY --chown=jboss:jboss docker/wildfly/configure-datasource.cli /tmp/configure-datasource.cli
RUN ${JBOSS_HOME}/bin/jboss-cli.sh --file=/tmp/configure-datasource.cli \
    && rm -rf ${JBOSS_HOME}/standalone/configuration/standalone_xml_history \
              ${JBOSS_HOME}/standalone/data ${JBOSS_HOME}/standalone/log ${JBOSS_HOME}/standalone/tmp

# Despliegue del WAR (deployment scanner)
COPY --from=build --chown=jboss:jboss /build/target/order-system.war ${JBOSS_HOME}/standalone/deployments/order-system.war

EXPOSE 8080
CMD ["/opt/jboss/wildfly/bin/standalone.sh", "-b", "0.0.0.0"]
