package com.petitesroues.library;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Opens the operating system's own file or folder dialog on this computer and returns the chosen
 * path. The app is local, so the server can show the dialog on the desktop the browser is on; a web
 * page can't learn a picked file's path itself. Uses zenity or kdialog on Linux, AppleScript on
 * macOS and Windows Forms through PowerShell on Windows.
 */
@Component
public class SystemFileDialog {
  private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);

  /** Lets the person choose one existing file; empty when they cancel. */
  public Optional<Path> chooseFile(String title, List<String> extensions) throws IOException {
    return choose(title, extensions, false);
  }

  /** Lets the person choose one existing folder; empty when they cancel. */
  public Optional<Path> chooseFolder(String title) throws IOException {
    return choose(title, List.of(), true);
  }

  private Optional<Path> choose(String title, List<String> extensions, boolean folder)
      throws IOException {
    var command = command(title, extensions, folder);
    var process =
        new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    try {
      process.waitFor();
    } catch (InterruptedException interrupted) {
      process.destroy();
      Thread.currentThread().interrupt();
      throw new IOException("The file dialog was interrupted", interrupted);
    }
    String path = output.strip();
    // Every tool exits nonzero, or prints nothing, when the person cancels.
    if (process.exitValue() != 0 || path.isEmpty()) return Optional.empty();
    return Optional.of(Path.of(path));
  }

  private static List<String> command(String title, List<String> extensions, boolean folder)
      throws IOException {
    if (OS.contains("win")) {
      String script =
          folder
              ? "$d=New-Object System.Windows.Forms.FolderBrowserDialog;$d.Description='%s';"
                      .formatted(quoted(title))
                  + "if($d.ShowDialog() -eq 'OK'){$d.SelectedPath}"
              : "$d=New-Object System.Windows.Forms.OpenFileDialog;$d.Title='%s';$d.Filter='%s';"
                      .formatted(quoted(title), windowsFilter(extensions))
                  + "if($d.ShowDialog() -eq 'OK'){$d.FileName}";
      return List.of(
          "powershell",
          "-NoProfile",
          "-STA",
          "-Command",
          "Add-Type -AssemblyName System.Windows.Forms;" + script);
    }
    if (OS.contains("mac")) {
      String script =
          "POSIX path of (choose %s with prompt \"%s\")"
              .formatted(folder ? "folder" : "file", title.replace("\"", "'"));
      return List.of("osascript", "-e", script);
    }
    if (installed("zenity")) {
      var command = new ArrayList<>(List.of("zenity", "--file-selection", "--title=" + title));
      if (folder) command.add("--directory");
      else
        command.add(
            "--file-filter=" + String.join(" ", extensions.stream().map(e -> "*." + e).toList()));
      return command;
    }
    if (installed("kdialog")) {
      String home = System.getProperty("user.home");
      return folder
          ? List.of("kdialog", "--title", title, "--getexistingdirectory", home)
          : List.of(
              "kdialog",
              "--title",
              title,
              "--getopenfilename",
              home,
              String.join(" ", extensions.stream().map(e -> "*." + e).toList()));
    }
    throw new IOException(
        "No system file dialog is available. Install zenity, or type the path instead.");
  }

  private static boolean installed(String tool) {
    String path = System.getenv("PATH");
    if (path == null) return false;
    for (String directory : path.split(File.pathSeparator))
      if (Files.isExecutable(Path.of(directory, tool))) return true;
    return false;
  }

  private static String quoted(String text) {
    return text.replace("'", "''");
  }

  private static String windowsFilter(List<String> extensions) {
    String patterns = String.join(";", extensions.stream().map(e -> "*." + e).toList());
    return "Supported files|" + patterns + "|All files|*.*";
  }
}
