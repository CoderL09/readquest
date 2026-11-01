package com.readquest.dictionary;

import com.fasterxml.jackson.databind.*;
import com.readquest.exception.ApiException;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarFile;
import org.springframework.stereotype.Component;

@Component
public class DictionaryProcesses {
  private final ObjectMapper json;
  private final ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();
  private final LinkedHashMap<Path, Client> clients = new LinkedHashMap<>(4, .75f, true);

  public DictionaryProcesses(ObjectMapper json) {
    this.json = json;
  }

  public synchronized void validate(Path path) {
    client(path);
  }

  public synchronized JsonNode lookup(Path path, List<String> candidates) {
    try {
      return client(path).request(candidates);
    } catch (RuntimeException error) {
      invalidate(path);
      throw error;
    }
  }

  private Client client(Path path) {
    Client existing = clients.get(path);
    if (existing != null && existing.process.isAlive()) return existing;
    invalidate(path);
    if (clients.size() >= 4) invalidate(clients.keySet().iterator().next());
    var created = new Client(path);
    clients.put(path, created);
    return created;
  }

  public synchronized void invalidate(Path path) {
    var client = clients.remove(path);
    if (client != null) client.close();
  }

  @PreDestroy
  public synchronized void close() {
    clients.values().forEach(Client::close);
    clients.clear();
    readers.shutdownNow();
  }

  private class Client {
    final Process process;
    final Reader input;
    final Writer output;

    Client(Path file) {
      try {
        String cp =
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var args =
            new ArrayList<String>(
                List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xms16m",
                    "-Xmx192m",
                    "-XX:MaxDirectMemorySize=32m",
                    "-XX:+ExitOnOutOfMemoryError",
                    "-Dfile.encoding=UTF-8"));
        boolean boot = false;
        if (!cp.contains(File.pathSeparator) && cp.endsWith(".jar")) {
          try (var jar = new JarFile(cp)) {
            boot =
                jar.getEntry("BOOT-INF/classes/com/readquest/ReadQuestApplication.class") != null;
          }
        }
        if (boot) args.add("-Dloader.main=com.readquest.dictionary.DictionaryWorker");
        args.addAll(
            List.of(
                "-cp",
                cp,
                boot
                    ? "org.springframework.boot.loader.launch.PropertiesLauncher"
                    : DictionaryWorker.class.getName(),
                file.toString()));
        process = new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        input = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
        output = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
      } catch (Exception error) {
        throw new ApiException(503, "Dictionary worker could not start");
      }
      try {
        if (!response(12).path("ready").asBoolean())
          throw new ApiException(422, "Invalid or unsupported MDX dictionary");
      } catch (RuntimeException error) {
        close();
        throw error;
      }
    }

    JsonNode request(List<String> words) {
      try {
        output.write(json.writeValueAsString(words) + "\n");
        output.flush();
        return response(4);
      } catch (IOException error) {
        throw new ApiException(503, "Dictionary worker stopped");
      }
    }

    JsonNode response(int seconds) {
      Future<JsonNode> future =
          readers.submit(() -> {
            int total = 0;
            for (int lines = 0; lines < 100; lines++) {
              var line = new StringBuilder();
              int ch;
              while ((ch = input.read()) != -1 && ch != '\n') {
                if (++total > 100000) throw new IOException("Oversized dictionary response");
                line.append((char) ch);
              }
              if (ch == -1) throw new IOException("Worker closed");
              if (line.toString().startsWith("RQDICT:")) return json.readTree(line.substring(7));
            }
            throw new IOException("Invalid worker response");
          });
      try {
        JsonNode value = future.get(seconds, TimeUnit.SECONDS);
        if (value.has("error"))
          throw new ApiException(422, "MDX dictionary could not be opened or queried");
        return value;
      } catch (TimeoutException error) {
        close();
        future.cancel(true);
        throw new ApiException(422, "MDX dictionary processing timed out");
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        close();
        future.cancel(true);
        throw new ApiException(503, "Dictionary request interrupted");
      } catch (ExecutionException error) {
        close();
        throw new ApiException(422, "Invalid or unsupported MDX dictionary");
      }
    }

    void close() {
      process.destroyForcibly();
    }
  }
}
