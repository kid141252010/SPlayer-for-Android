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
      if (name == null || !name.toLowerCase().endsWith(".ttml")) continue;

      accumulator.totalFiles++;
      String fileUri = child.getUri().toString();
      try {
        String content = readText(child.getUri());
        String ncmMusicId = extractNcmMusicId(content);
        if (ncmMusicId == null || ncmMusicId.isEmpty()) continue;

        accumulator.matchedFiles++;
        accumulator.putIndex(
            ncmMusicId,
            new AndroidLyricIndexEntry(
                fileUri, name, Math.max(child.lastModified(), 0L), directoryInfo.uri));
      } catch (SecurityException error) {
        accumulator.failedFiles++;
        accumulator.addFailure(fileUri, name, "FILE_PERMISSION_EXPIRED", directoryInfo.uri);
      } catch (Exception error) {
        accumulator.failedFiles++;
        accumulator.addFailure(fileUri, name, "FILE_READ_FAILED", directoryInfo.uri);
      }
    }
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

    AndroidLyricIndexEntry(String uri, String name, long lastModified, String directoryUri) {
      this.uri = uri;
      this.name = name;
      this.lastModified = lastModified;
      this.directoryUri = directoryUri;
    }

    JSObject toJSObject() {
      JSObject object = new JSObject();
      object.put("uri", uri);
      object.put("name", name);
      object.put("lastModified", lastModified);
      object.put("directoryUri", directoryUri);
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
        if (!shouldReplace(oldLastModified, entry.lastModified)) return;
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

    private boolean shouldReplace(long oldLastModified, long newLastModified) {
      if (oldLastModified > 0 && newLastModified > 0) {
        return newLastModified > oldLastModified;
      }
      if (oldLastModified <= 0 && newLastModified > 0) return true;
      if (oldLastModified > 0) return false;
      return true;
    }
  }
}
