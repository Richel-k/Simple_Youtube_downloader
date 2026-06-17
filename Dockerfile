# Étape 1 : Build de l'application avec Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
COPY . .
# AJOUTEZ CETTE LIGNE (Remplacez "LeNomDeVotreSousDossier" par le vrai nom du dossier)
WORKDIR /Simple_Youtube_downloader

RUN mvn clean package -DskipTests

# Étape 2 : Environnement d'exécution
FROM eclipse-temurin:17-jdk-jammy

# On installe UNIQUEMENT ffmpeg sur le serveur Render (obligatoire pour les fusions d'ID)
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

# Copie du fichier .jar (qui contient votre dossier resources/bin/yt-dlp à l'intérieur)
COPY --from=build /target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
