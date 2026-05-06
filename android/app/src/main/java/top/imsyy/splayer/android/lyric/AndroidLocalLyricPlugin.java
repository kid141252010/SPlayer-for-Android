package top.imsyy.splayer.android.lyric;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import androidx.activity.result.ActivityResult;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "AndroidLocalLyric")
public class AndroidLocalLyricPlugin extends Plugin {
  private static final Pattern AMLL_META_PATTERN =
      Pattern.compile("<\\s*amll:meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
  private static final Pattern FILENAME_ID_PATTERN = Pattern.compile("(\\d+)");
  private static final String[] LYRIC_EXTENSIONS = {".ttml", ".lrc"};
  private static final String[] SIDECAR_EXTENSIONS = {".ttml", ".yrc", ".lrc"};
  private static final int READ_FLAGS =
      Intent.FLAG_GRANT_READ_URI_PERMISSION
          | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
          | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override
  protected void handleOnDestroy() {
    executor.shutdownNow();
  }

  @PluginMethod
  public void pickLyricDirectory(PluginCall call) {
    if (getActivity() == null) {
      call.reject("Activity unavailable");
      return;
    }

    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    intent.addFlags(READ_FLAGS);
    startActivityForResult(call, intent, "onPickLyricDirectoryResult");
  }

  @ActivityCallback
  private void onPickLyricDirectoryResult(@Nullable PluginCall call, ActivityResult result) {
    if (call == null) return;

    Intent data = result.getData();
    Uri uri = data == null ? null : data.getData();
    if (uri == null) {
      JSObject cancelled = new JSObject();
      cancelled.put("cancelled", true);
      call.resolve(cancelled);
      return;
    }

    try {
      int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
      if (flags == 0) flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
      getContext().getContentResolver().takePersistableUriPermission(uri, flags);
    } catch (SecurityException error) {
      call.reject("LYRIC_DIRECTORY_PERMISSION_FAILED", error);
      return;
    }

    DocumentFile directory = DocumentFile.fromTreeUri(getContext(), uri);
    JSObject response = new JSObject();
    response.put("cancelled", false);
    response.put("uri", uri.toString());
    response.put("name", safeName(directory, uri));
    call.resolve(response);
  }

  @PluginMethod
  public void scanLyricDirectories(PluginCall call) {
    JSArray directories = call.getArray("directories");
    if (directories == null) {
      call.reject("directories is required");
      return;
    }

    executor.execute(
        () -> {
          ScanAccumulator accumulator = new ScanAccumulator();
          for (int i = 0; i < directories.length(); i++) {
            try {
              JSONObject item = directories.getJSONObject(i);
              String uriText = item.optString("uri", "");
              if (uriText.isEmpty()) {
                accumulator.addFailure("", "", "EMPTY_DIRECTORY_URI", "");
                continue;
              }

              Uri uri = Uri.parse(uriText);
              DirectoryInfo directoryInfo = new DirectoryInfo(uriText, item.optString("name", ""));
              DocumentFile directory = DocumentFile.fromTreeUri(getContext(), uri);
              if (directory == null || !directory.exists() || !directory.canRead()) {
                accumulator.addFailure(uriText, directoryInfo.name, "DIRECTORY_UNREADABLE", uriText);
                continue;
              }

              scanDirectory(directory, directoryInfo, accumulator);
            } catch (JSONException error) {
              accumulator.addFailure("", "", "INVALID_DIRECTORY_PAYLOAD", "");
            } catch (SecurityException error) {
              accumulator.addFailure("", "", "DIRECTORY_PERMISSION_EXPIRED", "");
            }
          }
          call.resolve(accumulator.toJSObject());
        });
  }

  @PluginMethod
  public void readLyricFile(PluginCall call) {
    String uriText = call.getString("uri", "");
    if (uriText.isEmpty()) {
      call.reject("uri is required");
      return;
    }

    executor.execute(
        () -> {
          try {
            JSObject response = new JSObject();
            response.put("content", readText(Uri.parse(uriText)));
            call.resolve(response);
          } catch (SecurityException error) {
            call.reject("LYRIC_FILE_PERMISSION_EXPIRED", error);
          } catch (Exception error) {
            call.reject("LYRIC_FILE_READ_FAILED", error);
          }
        });
  }

  @PluginMethod
  public void findSidecarLyric(PluginCall call) {
    String audioPath = call.getString("audioPath", "");
    if (audioPath.isEmpty()) {
      call.reject("audioPath is required");
      return;
    }

    executor.execute(
        () -> {
          try {
            String baseName = audioPath;
            int lastSlash = baseName.lastIndexOf('/');
            String parentPath = lastSlash > 0 ? baseName.substring(0, lastSlash) : "";
            baseName = baseName.substring(lastSlash + 1);
            int dotIdx = baseName.lastIndexOf('.');
            if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);

            File parentDir = new File(parentPath);
            if (parentDir.exists() && parentDir.canRead()) {
              for (String ext : SIDECAR_EXTENSIONS) {
                File lyricFile = new File(parentDir, baseName + ext);
                if (lyricFile.exists() && lyricFile.canRead()) {
                  String content = readFileText(lyricFile);
                  String format = ext.substring(1);
                  JSObject response = new JSObject();
                  response.put("content", content);
                  response.put("format", format);
                  call.resolve(response);
                  return;
                }
              }
            }

            JSObject empty = new JSObject();
            empty.put("content", "");
            call.resolve(empty);
          } catch (Exception error) {
            JSObject empty = new JSObject();
            empty.put("content", "");
            call.resolve(empty);
          }
        });
  }

  private void scanDirectory(
      DocumentFile directory, DirectoryInfo directoryInfo, ScanAccumulator accumulator) {
    DocumentFile[] children;
    try {
      children = directory.listFiles();
    } catch (SecurityException error) {
      accumulator.addFailure(
          directory.getUri().toString(), directory.getName(), "DIRECTORY_PERMISSION_EXPIRED",
          directoryInfo.uri);
      return;
    }

    for (DocumentFile child : children) {
      if (child.isDirectory()) {
        scanDirectory(child, directoryInfo, accumulator);
        continue;
      }

      if (!child.isFile()) continue;
      String name = child.getName();
      if (name == null || !isLyricFile(name)) continue;

      accumulator.totalFiles++;
      String fileUri = child.getUri().toString();
      String format = getFormatFromName(name);

      try {
        if ("ttml".equals(format)) {
          String content = readText(child.getUri());
          String ncmMusicId = extractNcmMusicId(content);
          if (ncmMusicId != null && !ncmMusicId.isEmpty()) {
            accumulator.matchedFiles++;
            accumulator.putIndex(
                ncmMusicId,
                new AndroidLyricIndexEntry(
                    fileUri, name, Math.max(child.lastModified(), 0L), directoryInfo.uri, format));
          }
          // 文件名匹配（TTML 也可通过文件名 ID 匹配）
          String filenameId = extractFilenameId(name);
          if (filenameId != null && !filenameId.isEmpty()) {
            accumulator.matchedFiles++;
            accumulator.putIndex(
                filenameId,
                new AndroidLyricIndexEntry(
                    fileUri, name, Math.max(child.lastModified(), 0L), directoryInfo.uri, format));
          }
        } else {
          // LRC 仅通过文件名 ID 匹配
          String filenameId = extractFilenameId(name);
          if (filenameId != null && !filenameId.isEmpty()) {
            accumulator.matchedFiles++;
            accumulator.putIndex(
                filenameId,
                new AndroidLyricIndexEntry(
                    fileUri, name, Math.max(child.lastModified(), 0L), directoryInfo.uri, format));
          }
        }
      } catch (SecurityException error) {
        accumulator.failedFiles++;
        accumulator.addFailure(fileUri, name, "FILE_PERMISSION_EXPIRED", directoryInfo.uri);
      } catch (Exception error) {
        accumulator.failedFiles++;
        accumulator.addFailure(fileUri, name, "FILE_READ_FAILED", directoryInfo.uri);
      }
    }
  }

  private boolean isLyricFile(String name) {
    String lower = name.toLowerCase();
    for (String ext : LYRIC_EXTENSIONS) {
      if (lower.endsWith(ext)) return true;
    }
    return false;
  }

  private String getFormatFromName(String name) {
    String lower = name.toLowerCase();
    if (lower.endsWith(".ttml")) return "ttml";
    if (lower.endsWith(".lrc")) return "lrc";
    return "lrc";
  }

  @Nullable
  private String extractFilenameId(String fileName) {
    String baseName = fileName;
    int dotIdx = baseName.lastIndexOf('.');
    if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);

    // 匹配 "123456" 或 "SongName.123456" 格式
    String[] parts = baseName.split("\\.");
    String lastPart = parts[parts.length - 1];
    if (lastPart.matches("\\d+") && lastPart.length() >= 2) {
      return lastPart;
    }

    // 如果整个文件名就是数字
    if (baseName.matches("\\d+") && baseName.length() >= 2) {
      return baseName;
    }

    return null;
  }

  private String readText(Uri uri) throws IOException {
    ContentResolver resolver = getContext().getContentResolver();
    try (InputStream input = resolver.openInputStream(uri)) {
      if (input == null) throw new IOException("Input stream unavailable");
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1) {
          builder.append(buffer, 0, read);
        }
        return builder.toString();
      }
    }
  }

  private String readFileText(File file) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
      StringBuilder builder = new StringBuilder();
      char[] buffer = new char[8192];
      int read;
      while ((read = reader.read(buffer)) != -1) {
        builder.append(buffer, 0, read);
      }
      return builder.toString();
    }
  }

  @Nullable
  private String extractNcmMusicId(String ttml) {
    Matcher metaMatcher = AMLL_META_PATTERN.matcher(ttml);
    while (metaMatcher.find()) {
      String tag = metaMatcher.group();
      String key = readAttribute(tag, "key");
      if (!"ncmMusicId".equals(key)) continue;
      String value = readAttribute(tag, "value");
      return value == null ? null : value.trim();
    }
    return null;
  }

  @Nullable
  private String readAttribute(String tag, String name) {
    Pattern attrPattern =
        Pattern.compile("\\b" + Pattern.quote(name) + "\\s*=\\s*(['\"])(.*?)\\1");
    Matcher matcher = attrPattern.matcher(tag);
    if (!matcher.find()) return null;
    return matcher.group(2);
  }

  private String safeName(@Nullable DocumentFile file, Uri uri) {
    String name = file == null ? null : file.getName();
    if (name != null && !name.isEmpty()) return name;
    String lastPath = uri.getLastPathSegment();
    return lastPath == null || lastPath.isEmpty() ? uri.toString() : lastPath;
  }

  private static class DirectoryInfo {
    final String uri;
    final String name;

    DirectoryInfo(String uri, String name) {
      this.uri = uri;
      this.name = name;
    }
  }

  private static class AndroidLyricIndexEntry {
    final String uri;
    final String name;
    final long lastModified;
    final String directoryUri;
    final String format;

    AndroidLyricIndexEntry(String uri, String name, long lastModified, String directoryUri, String format) {
      this.uri = uri;
      this.name = name;
      this.lastModified = lastModified;
      this.directoryUri = directoryUri;
      this.format = format;
    }

    JSObject toJSObject() {
      JSObject object = new JSObject();
      object.put("uri", uri);
      object.put("name", name);
      object.put("lastModified", lastModified);
      object.put("directoryUri", directoryUri);
      object.put("format", format);
      return object;
    }
  }

  private static class ScanAccumulator {
    int totalFiles = 0;
    int matchedFiles = 0;
    int duplicateIds = 0;
    int failedFiles = 0;
    final JSObject indexMap = new JSObject();
    final JSArray failures = new JSArray();

    void putIndex(String id, AndroidLyricIndexEntry entry) {
      JSONObject existing = indexMap.optJSONObject(id);
      if (existing != null) {
        duplicateIds++;
        long oldLastModified = existing.optLong("lastModified", 0L);
        // TTML 优先于 LRC，时间戳相同时 TTML 胜出
        String oldFormat = existing.optString("format", "lrc");
        if (!shouldReplace(oldLastModified, entry.lastModified, oldFormat, entry.format)) return;
      }
      indexMap.put(id, entry.toJSObject());
    }

    void addFailure(String uri, String name, String reason, String directoryUri) {
      JSObject failure = new JSObject();
      failure.put("uri", uri);
      failure.put("name", name);
      failure.put("reason", reason);
      failure.put("directoryUri", directoryUri);
      failures.put(failure);
    }

    JSObject toJSObject() {
      JSObject response = new JSObject();
      response.put("indexMap", indexMap);
      response.put("totalFiles", totalFiles);
      response.put("matchedFiles", matchedFiles);
      response.put("duplicateIds", duplicateIds);
      response.put("failedFiles", failedFiles);
      response.put("failures", failures);
      return response;
    }

    private boolean shouldReplace(long oldLastModified, long newLastModified, String oldFormat, String newFormat) {
      // TTML 优先
      boolean oldIsTtml = "ttml".equals(oldFormat);
      boolean newIsTtml = "ttml".equals(newFormat);
      if (newIsTtml && !oldIsTtml) return true;
      if (!newIsTtml && oldIsTtml) return false;
      // 同格式按时间戳比较
      if (oldLastModified > 0 && newLastModified > 0) {
        return newLastModified > oldLastModified;
      }
      if (oldLastModified <= 0 && newLastModified > 0) return true;
      if (oldLastModified > 0) return false;
      return true;
    }
  }
}
