package com.readquest.imports;

import java.io.*;
import java.nio.file.*;
import org.springframework.web.multipart.MultipartFile;

public record MemoryUpload(String filename, String type, byte[] bytes) implements MultipartFile {
  public String getName() {
    return "file";
  }

  public String getOriginalFilename() {
    return filename;
  }

  public String getContentType() {
    return type;
  }

  public boolean isEmpty() {
    return bytes.length == 0;
  }

  public long getSize() {
    return bytes.length;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public InputStream getInputStream() {
    return new ByteArrayInputStream(bytes);
  }

  public void transferTo(File file) throws IOException {
    Files.write(file.toPath(), bytes);
  }
}
