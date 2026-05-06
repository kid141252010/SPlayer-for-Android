package top.imsyy.splayer.android.download;

import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import androidx.activity.result.ActivityResult;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.ActivityCallback;
import android.content.Intent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

@CapacitorPlugin(name = "AndroidDownload")
public class AndroidDownloadPlugin extends Plugin {
  private static final int READ_FLAGS =
      Intent.FLAG_GRANT_READ_URI_PERMISSION
          | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
          | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;
  private static final int WRITE_FLAGS =
      Intent.FLAG_GRANT_READ_URI_PERMISSION
          | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
          | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

  private final ExecutorService executor = Executors.newFixedThreadPool(2);
  private final ConcurrentHashMap<Long, PluginCall> activeDownloads = new ConcurrentHashMap<>();

  @Override
  protected void handleOnDestroy() {
    executor.shutdownNow();
  }

  @PluginMethod
  public void pickDownloadDirectory(PluginCall call) {
    if (getActivity() == null) {
      call.reject("Activity unavailable");
      return;
    }

    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    intent.addFlags(WRITE_FLAGS);
    startActivityForResult(call, intent, "onPickDownloadDirectoryResult");
  }

  @ActivityCallback
  private void onPickDownloadDirectoryResult(@Nullable PluginCall call, ActivityResult result) {
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
      int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      if (flags == 0) flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
      getContext().getContentResolver().takePersistableUriPermission(uri, flags);
    } catch (SecurityException error) {
      call.reject("DOWNLOAD_DIRECTORY_PERMISSION_FAILED", error);
      return;
    }

    DocumentFile directory = DocumentFile.fromTreeUri(getContext(), uri);
    JSObject response = new JSObject();
    response.put("cancelled", false);
    response.put("uri", uri.toString());
    response.put("name", directory != null && directory.getName() != null ? directory.getName() : "Download");
    call.resolve(response);
  }

  @PluginMethod
  public void downloadFile(PluginCall call) {
    String fileUrl = call.getString("url", "");
    String fileName = call.getString("fileName", "");
    String directoryUri = call.getString("directoryUri", "");
    String subPath = call.getString("subPath", "");

    if (fileUrl.isEmpty() || fileName.isEmpty() || directoryUri.isEmpty()) {
      call.reject("url, fileName, and directoryUri are required");
      return;
    }

    executor.execute(() -> {
      try {
        Uri dirUri = Uri.parse(directoryUri);
        DocumentFile directory = DocumentFile.fromTreeUri(getContext(), dirUri);
        if (directory == null || !directory.exists() || !directory.canWrite()) {
          call.reject("DOWNLOAD_DIRECTORY_NOT_WRITABLE");
          return;
        }

        // 处理子目录
        DocumentFile targetDir = directory;
        if (!subPath.isEmpty()) {
          String[] parts = subPath.split("/");
          for (String part : parts) {
            if (part.isEmpty()) continue;
            DocumentFile existing = findChild(targetDir, part);
            if (existing != null && existing.isDirectory()) {
              targetDir = existing;
            } else {
              targetDir = targetDir.createDirectory(part);
              if (targetDir == null) {
                call.reject("FAILED_TO_CREATE_SUBDIRECTORY: " + part);
                return;
              }
            }
          }
        }

        // 检查文件是否已存在
        String extension = getFileExtension(fileName);
        String baseName = getBaseName(fileName);
        DocumentFile existingFile = findChild(targetDir, fileName);
        if (existingFile != null && existingFile.exists()) {
          JSObject skipResult = new JSObject();
          skipResult.put("status", "skipped");
          skipResult.put("path", existingFile.getUri().toString());
          call.resolve(skipResult);
          return;
        }

        // 创建目标文件
        String mimeType = guessMimeType(extension);
        DocumentFile targetFile = targetDir.createFile(mimeType, fileName);
        if (targetFile == null) {
          call.reject("FAILED_TO_CREATE_FILE");
          return;
        }

        // 下载文件
        HttpURLConnection connection = null;
        InputStream input = null;
        OutputStream output = null;
        try {
          URL url = new URL(fileUrl);
          connection = (HttpURLConnection) url.openConnection();
          connection.setConnectTimeout(30000);
          connection.setReadTimeout(60000);
          connection.setRequestProperty("User-Agent", "SPlayer-for-Android");
          connection.connect();

          int responseCode = connection.getResponseCode();
          if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
            targetFile.delete();
            call.reject("HTTP_ERROR_" + responseCode);
            return;
          }

          int contentLength = connection.getContentLength();
          input = connection.getInputStream();
          output = getContext().getContentResolver().openOutputStream(targetFile.getUri());

          if (output == null) {
            targetFile.delete();
            call.reject("FAILED_TO_OPEN_OUTPUT_STREAM");
            return;
          }

          byte[] buffer = new byte[8192];
          int bytesRead;
          long totalRead = 0;
          int lastReportedPercent = -1;

          while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            totalRead += bytesRead;

            if (contentLength > 0) {
              int percent = (int) ((totalRead * 100) / contentLength);
              if (percent != lastReportedPercent && percent % 5 == 0) {
                lastReportedPercent = percent;
                JSObject progress = new JSObject();
                progress.put("bytesRead", totalRead);
                progress.put("contentLength", contentLength);
                progress.put("percent", percent / 100.0);
                notifyListeners("downloadProgress", progress);
              }
            }
          }

          output.flush();

          JSObject result = new JSObject();
          result.put("status", "success");
          result.put("path", targetFile.getUri().toString());
          result.put("fileName", fileName);
          call.resolve(result);

        } finally {
          if (input != null) try { input.close(); } catch (IOException ignored) {}
          if (output != null) try { output.close(); } catch (IOException ignored) {}
          if (connection != null) connection.disconnect();
        }
      } catch (Exception error) {
        call.reject("DOWNLOAD_FAILED", error);
      }
    });
  }

  @PluginMethod
  public void writeTextFile(PluginCall call) {
    String fileName = call.getString("fileName", "");
    String content = call.getString("content", "");
    String directoryUri = call.getString("directoryUri", "");
    String subPath = call.getString("subPath", "");

    if (fileName.isEmpty() || directoryUri.isEmpty()) {
      call.reject("fileName and directoryUri are required");
      return;
    }

    executor.execute(() -> {
      try {
        Uri dirUri = Uri.parse(directoryUri);
        DocumentFile directory = DocumentFile.fromTreeUri(getContext(), dirUri);
        if (directory == null || !directory.exists() || !directory.canWrite()) {
          call.reject("DOWNLOAD_DIRECTORY_NOT_WRITABLE");
          return;
        }

        DocumentFile targetDir = directory;
        if (!subPath.isEmpty()) {
          String[] parts = subPath.split("/");
          for (String part : parts) {
            if (part.isEmpty()) continue;
            DocumentFile existing = findChild(targetDir, part);
            if (existing != null && existing.isDirectory()) {
              targetDir = existing;
            } else {
              targetDir = targetDir.createDirectory(part);
              if (targetDir == null) {
                call.reject("FAILED_TO_CREATE_SUBDIRECTORY");
                return;
              }
            }
          }
        }

        String extension = getFileExtension(fileName);
        String mimeType = guessMimeType(extension);

        // 删除已存在的文件
        DocumentFile existingFile = findChild(targetDir, fileName);
        if (existingFile != null) existingFile.delete();

        DocumentFile targetFile = targetDir.createFile(mimeType, fileName);
        if (targetFile == null) {
          call.reject("FAILED_TO_CREATE_FILE");
          return;
        }

        OutputStream output = null;
        try {
          output = getContext().getContentResolver().openOutputStream(targetFile.getUri());
          if (output == null) {
            call.reject("FAILED_TO_OPEN_OUTPUT_STREAM");
            return;
          }
          output.write(content.getBytes(StandardCharsets.UTF_8));
          output.flush();

          JSObject result = new JSObject();
          result.put("status", "success");
          result.put("path", targetFile.getUri().toString());
          call.resolve(result);
        } finally {
          if (output != null) try { output.close(); } catch (IOException ignored) {}
        }
      } catch (Exception error) {
        call.reject("WRITE_FILE_FAILED", error);
      }
    });
  }

  @PluginMethod
  public void getDownloadDirectoryInfo(PluginCall call) {
    String directoryUri = call.getString("directoryUri", "");
    if (directoryUri.isEmpty()) {
      JSObject result = new JSObject();
      result.put("exists", false);
      call.resolve(result);
      return;
    }

    try {
      Uri dirUri = Uri.parse(directoryUri);
      DocumentFile directory = DocumentFile.fromTreeUri(getContext(), dirUri);
      JSObject result = new JSObject();
      result.put("exists", directory != null && directory.exists());
      result.put("canWrite", directory != null && directory.canWrite());
      result.put("name", directory != null && directory.getName() != null ? directory.getName() : "");
      call.resolve(result);
    } catch (Exception error) {
      JSObject result = new JSObject();
      result.put("exists", false);
      call.resolve(result);
    }
  }

  /**
   * 列出下载目录下的全部音乐文件（递归扫描），并提取基础元数据。
   * 用于"下载完成"页面渲染下载历史。
   */
  @PluginMethod
  public void listDownloadedSongs(PluginCall call) {
    String directoryUri = call.getString("directoryUri", "");
    if (directoryUri.isEmpty()) {
      JSObject empty = new JSObject();
      empty.put("songs", new JSArray());
      call.resolve(empty);
      return;
    }

    executor.execute(() -> {
      try {
        Uri dirUri = Uri.parse(directoryUri);
        DocumentFile directory = DocumentFile.fromTreeUri(getContext(), dirUri);
        if (directory == null || !directory.exists() || !directory.canRead()) {
          JSObject empty = new JSObject();
          empty.put("songs", new JSArray());
          call.resolve(empty);
          return;
        }

        JSArray songs = new JSArray();
        scanAudioFiles(directory, songs);

        JSObject response = new JSObject();
        response.put("songs", songs);
        call.resolve(response);
      } catch (SecurityException error) {
        call.reject("DOWNLOAD_DIRECTORY_PERMISSION_EXPIRED", error);
      } catch (Exception error) {
        call.reject("LIST_DOWNLOADED_SONGS_FAILED", error);
      }
    });
  }

  /**
   * 递归扫描目录中的音乐文件，并提取元数据
   */
  private void scanAudioFiles(DocumentFile directory, JSArray output) {
    DocumentFile[] children;
    try {
      children = directory.listFiles();
    } catch (SecurityException ignored) {
      return;
    }

    for (DocumentFile child : children) {
      if (child == null) continue;
      if (child.isDirectory()) {
        scanAudioFiles(child, output);
        continue;
      }
      if (!child.isFile()) continue;

      String name = child.getName();
      if (name == null || !isAudioFile(name)) continue;

      JSObject song = buildSongMetadata(child, name);
      if (song != null) output.put(song);
    }
  }

  /**
   * 从 DocumentFile 提取歌曲元数据（艺术家、专辑、时长等）
   */
  @Nullable
  private JSObject buildSongMetadata(DocumentFile file, String fileName) {
    String uri = file.getUri().toString();
    long size = file.length();
    long lastModified = file.lastModified();

    // 默认从文件名解析（"Artist - Title.ext" 或 "Title.ext"）
    String baseName = fileName;
    int dotIdx = baseName.lastIndexOf('.');
    String extension = dotIdx > 0 ? baseName.substring(dotIdx + 1).toLowerCase() : "";
    if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);

    String fallbackTitle = baseName;
    String fallbackArtist = "未知歌手";
    int sepIdx = baseName.indexOf(" - ");
    if (sepIdx > 0) {
      fallbackArtist = baseName.substring(0, sepIdx).trim();
      fallbackTitle = baseName.substring(sepIdx + 3).trim();
      if (fallbackTitle.isEmpty()) fallbackTitle = baseName;
    }

    String title = fallbackTitle;
    String artist = fallbackArtist;
    String album = "未知专辑";
    long duration = 0L;

    // 尝试用 MediaMetadataRetriever 读取标签信息
    MediaMetadataRetriever retriever = null;
    try {
      retriever = new MediaMetadataRetriever();
      retriever.setDataSource(getContext(), file.getUri());

      String mTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
      String mArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
      String mAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
      String mDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

      if (mTitle != null && !mTitle.trim().isEmpty()) title = mTitle.trim();
      if (mArtist != null && !mArtist.trim().isEmpty()) artist = mArtist.trim();
      if (mAlbum != null && !mAlbum.trim().isEmpty()) album = mAlbum.trim();
      if (mDuration != null) {
        try {
          duration = Long.parseLong(mDuration);
        } catch (NumberFormatException ignored) {
        }
      }
    } catch (Exception ignored) {
      // 文件不可解析，使用文件名兜底
    } finally {
      if (retriever != null) {
        try {
          retriever.release();
        } catch (Exception ignored) {
        }
      }
    }

    // ID 用 URI 哈希生成（确保稳定 + 不与在线歌曲 ID 冲突）
    long id = Math.abs((long) uri.hashCode()) + 1_000_000_000L;

    JSObject song = new JSObject();
    song.put("id", id);
    song.put("name", title);
    song.put("artists", artist);
    song.put("album", album);
    song.put("duration", duration);
    song.put("size", size);
    song.put("path", uri);
    song.put("fileName", fileName);
    song.put("ext", extension);
    song.put("lastModified", lastModified);
    return song;
  }

  private boolean isAudioFile(String name) {
    String lower = name.toLowerCase();
    return lower.endsWith(".mp3")
        || lower.endsWith(".flac")
        || lower.endsWith(".wav")
        || lower.endsWith(".ogg")
        || lower.endsWith(".m4a")
        || lower.endsWith(".aac")
        || lower.endsWith(".ape")
        || lower.endsWith(".wma")
        || lower.endsWith(".opus");
  }

  /**
   * SAF 选取本地音乐目录（与下载目录使用相同 ACTION_OPEN_DOCUMENT_TREE）
   */
  @PluginMethod
  public void pickLocalMusicDirectory(PluginCall call) {
    if (getActivity() == null) {
      call.reject("Activity unavailable");
      return;
    }

    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    intent.addFlags(READ_FLAGS);
    startActivityForResult(call, intent, "onPickLocalMusicDirectoryResult");
  }

  @ActivityCallback
  private void onPickLocalMusicDirectoryResult(@Nullable PluginCall call, ActivityResult result) {
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
      call.reject("LOCAL_MUSIC_DIRECTORY_PERMISSION_FAILED", error);
      return;
    }

    DocumentFile directory = DocumentFile.fromTreeUri(getContext(), uri);
    JSObject response = new JSObject();
    response.put("cancelled", false);
    response.put("uri", uri.toString());
    response.put(
        "name", directory != null && directory.getName() != null ? directory.getName() : "Local Music");
    call.resolve(response);
  }

  /**
   * 扫描本地音乐目录列表（多目录递归），返回歌曲数组
   */
  @PluginMethod
  public void scanLocalMusic(PluginCall call) {
    JSArray directories = call.getArray("directories");
    if (directories == null || directories.length() == 0) {
      JSObject empty = new JSObject();
      empty.put("songs", new JSArray());
      call.resolve(empty);
      return;
    }

    executor.execute(() -> {
      JSArray songs = new JSArray();
      int failedDirs = 0;

      for (int i = 0; i < directories.length(); i++) {
        try {
          Object item = directories.get(i);
          String uriText;
          if (item instanceof JSObject) {
            uriText = ((JSObject) item).getString("uri", "");
          } else if (item instanceof org.json.JSONObject) {
            uriText = ((org.json.JSONObject) item).optString("uri", "");
          } else {
            uriText = String.valueOf(item);
          }
          if (uriText == null || uriText.isEmpty()) {
            failedDirs++;
            continue;
          }

          Uri uri = Uri.parse(uriText);
          DocumentFile directory = DocumentFile.fromTreeUri(getContext(), uri);
          if (directory == null || !directory.exists() || !directory.canRead()) {
            failedDirs++;
            continue;
          }
          scanAudioFiles(directory, songs);
        } catch (SecurityException error) {
          failedDirs++;
        } catch (Exception error) {
          failedDirs++;
        }
      }

      JSObject response = new JSObject();
      response.put("songs", songs);
      response.put("failedDirectories", failedDirs);
      call.resolve(response);
    });
  }

  @Nullable
  private DocumentFile findChild(DocumentFile parent, String name) {
    for (DocumentFile child : parent.listFiles()) {
      if (name.equals(child.getName())) return child;
    }
    return null;
  }

  private String getFileExtension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "";
  }

  private String getBaseName(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  private String guessMimeType(String extension) {
    switch (extension) {
      case "mp3": return "audio/mpeg";
      case "flac": return "audio/flac";
      case "wav": return "audio/wav";
      case "ogg": return "audio/ogg";
      case "m4a": return "audio/mp4";
      case "aac": return "audio/aac";
      case "lrc": return "text/plain";
      case "ttml": return "application/xml";
      case "yrc": return "text/plain";
      case "ass": return "text/plain";
      case "json": return "application/json";
      default: return "application/octet-stream";
    }
  }
}
