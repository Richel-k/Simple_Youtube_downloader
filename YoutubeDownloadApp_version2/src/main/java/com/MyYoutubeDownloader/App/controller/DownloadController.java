package com.MyYoutubeDownloader.App.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
@CrossOrigin("*")
public class DownloadController {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ResourceLoader resourceLoader;

    private static final Pattern PROGRESS_PATTERN = Pattern.compile(
            "\\[download\\]\\s+([0-9.]+)%\\s+of\\s+([^ ]+)\\s+at\\s+([^ ]+)\\s+ETA\\s+([^ ]+)");

    // Injection du ResourceLoader par constructeur
    public DownloadController(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Utilitaire pour localiser yt-dlp dans le projet et s'assurer qu'il est
     * exécutable
     */
    // private String getCustomYtDlpPath() throws Exception {
    //     Resource resource = resourceLoader.getResource("classpath:bin/yt-dlp");
    //     File ytDlpFile = resource.getFile();

    //     // Sécurité Linux : s'assurer que le fichier est bien considéré comme un
    //     // exécutable par le système
    //     if (!ytDlpFile.canExecute()) {
    //         ytDlpFile.setExecutable(true);
    //     }

    //     return ytDlpFile.getAbsolutePath();
    // }

    private String getCustomYtDlpPath() throws Exception {
        // 1. On définit un emplacement temporaire sur le serveur Linux de Render
        File tempYtDlp = new File("/tmp/yt-dlp");

        // 2. Si le fichier n'a pas encore été extrait, on le copie depuis le JAR
        if (!tempYtDlp.exists()) {
            Resource resource = resourceLoader.getResource("classpath:bin/yt-dlp");
            try (java.io.InputStream is = resource.getInputStream()) {
                java.nio.file.Files.copy(is, tempYtDlp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 3. On force les droits d'exécution (chmod +x) pour Render
        if (!tempYtDlp.canExecute()) {
            tempYtDlp.setExecutable(true);
        }

        // 4. On retourne le chemin du fichier prêt à l'emploi
        return tempYtDlp.getAbsolutePath();
    }

    /**
     * FONCTION 1 : Récupérer la liste des formats (-F)
     */
    @GetMapping("/formats")
    public ResponseEntity<String> getAvailableFormats(@RequestParam String url) {
        StringBuilder output = new StringBuilder();
        try {
            String ytDlpPath = getCustomYtDlpPath(); // Récupère le chemin interne automatiquement

            ProcessBuilder builder = new ProcessBuilder(ytDlpPath,"--cookies", "./cookies.txt", "-F", url);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return ResponseEntity.ok(output.toString());
            } else {
                return ResponseEntity.status(500).body("Erreur yt-dlp lors de la récupération des formats.\n" + output);
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur serveur : " + e.getMessage());
        }
    }

    /**
     * FONCTION 2 : Télécharger l'ID spécifique (-f)
     */
    @GetMapping(value = "/download", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter downloadSpecificFormat(@RequestParam String url,
            @RequestParam String path,
            @RequestParam String formatId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        executor.execute(() -> {
            try {
                String ytDlpPath = getCustomYtDlpPath(); // Récupère le chemin interne automatiquement

                List<String> command = new ArrayList<>();
                command.add(ytDlpPath); // Utilisation du binaire embarqué
                command.add("--cookies");
                command.add("./cookies.txt");
                command.add("-f");
                command.add(formatId);
                command.add("-P");
                command.add(path);
                command.add("--newline");
                command.add(url);

                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(true);
                Process process = builder.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = PROGRESS_PATTERN.matcher(line);
                        if (matcher.find()) {
                            String percent = matcher.group(1);
                            String totalSize = matcher.group(2);
                            String speed = matcher.group(3);
                            String eta = matcher.group(4);

                            String jsonResponse = String.format(
                                    "{\"percent\":\"%s\", \"totalSize\":\"%s\", \"speed\":\"%s\", \"eta\":\"%s\"}",
                                    percent, totalSize, speed, eta);
                            emitter.send(SseEmitter.event().name("progress").data(jsonResponse));
                        } else {
                            emitter.send(SseEmitter.event().name("status").data(line));
                        }
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    emitter.send(SseEmitter.event().name("complete").data("Téléchargement terminé avec succès !"));
                } else {
                    emitter.send(
                            SseEmitter.event().name("error").data("Le processus yt-dlp s'est arrêté prématurément."));
                }
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}

/** Richel Kembou Fosso. .  .  */
