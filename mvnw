#!/bin/sh
# Maven Wrapper para Unix/Linux/macOS
# Descarga Apache Maven 3.9.6 en ~/.mvn/wrapper si no está instalado.
#
# Uso: ./mvnw clean install

MAVEN_VERSION="3.9.6"
MAVEN_CACHE="$HOME/.mvn/wrapper"
MAVEN_HOME="$MAVEN_CACHE/apache-maven-$MAVEN_VERSION"
MAVEN_CMD="$MAVEN_HOME/bin/mvn"
DIST_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz"
DIST_TGZ="$MAVEN_CACHE/mvn-dist-$MAVEN_VERSION.tar.gz"

if [ ! -f "$MAVEN_CMD" ]; then
  echo "Descargando Apache Maven $MAVEN_VERSION en $MAVEN_HOME ..."
  mkdir -p "$MAVEN_CACHE"
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL "$DIST_URL" -o "$DIST_TGZ"
  elif command -v wget > /dev/null 2>&1; then
    wget -q "$DIST_URL" -O "$DIST_TGZ"
  else
    echo "ERROR: Se requiere curl o wget para descargar Maven."
    exit 1
  fi
  tar -xzf "$DIST_TGZ" -C "$MAVEN_CACHE"
  rm -f "$DIST_TGZ"
  chmod +x "$MAVEN_CMD"
  echo "Maven $MAVEN_VERSION descargado correctamente."
fi

exec "$MAVEN_CMD" "$@"
