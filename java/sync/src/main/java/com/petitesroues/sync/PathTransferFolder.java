package com.petitesroues.sync;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** A download folder on a file system ({@link TransferFolder}). */
public final class PathTransferFolder implements TransferFolder {
  private final Path root;

  public PathTransferFolder(Path root) {
    this.root = root;
  }

  private Path file(List<String> path) {
    Path file = root;
    for (String segment : path) file = file.resolve(segment);
    return file;
  }

  private Path part(List<String> path) {
    Path file = file(path);
    return file.resolveSibling(TransferFolder.partName(file.getFileName().toString()));
  }

  @Override
  public long size(List<String> path) throws IOException {
    Path file = file(path);
    return Files.isRegularFile(file) ? Files.size(file) : -1;
  }

  @Override
  public SeekableByteChannel open(List<String> path) throws IOException {
    return Files.newByteChannel(file(path));
  }

  @Override
  public long partSize(List<String> path) throws IOException {
    Path part = part(path);
    return Files.isRegularFile(part) ? Files.size(part) : -1;
  }

  @Override
  public OutputStream appendPart(List<String> path) throws IOException {
    Path part = part(path);
    Files.createDirectories(part.getParent());
    return Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  @Override
  public SeekableByteChannel openPart(List<String> path) throws IOException {
    return Files.newByteChannel(part(path));
  }

  @Override
  public void deletePart(List<String> path) throws IOException {
    Files.deleteIfExists(part(path));
  }

  @Override
  public void commit(List<String> path) throws IOException {
    Files.move(part(path), file(path), StandardCopyOption.REPLACE_EXISTING);
  }
}
