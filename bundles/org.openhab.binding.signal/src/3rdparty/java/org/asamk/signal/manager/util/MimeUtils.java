package org.asamk.signal.manager.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.Optional;

public class MimeUtils {

    public static final String LONG_TEXT = "text/x-signal-plain";
    public static final String PLAIN_TEXT = "text/plain";
    public static final String OCTET_STREAM = "application/octet-stream";

    public static Optional<String> getFileMimeType(final File file) throws IOException {
        var mime = Files.probeContentType(file.toPath());
        if (mime != null) {
            return Optional.of(mime);
        }

        try (final InputStream bufferedStream = new BufferedInputStream(new FileInputStream(file))) {
            return getStreamMimeType(bufferedStream);
        }
    }

    public static Optional<String> getStreamMimeType(final InputStream inputStream) throws IOException {
        return Optional.ofNullable(URLConnection.guessContentTypeFromStream(inputStream));
    }

    public static Optional<String> guessExtensionFromMimeType(String mimeType) {

        String mime;
        switch (mimeType) {
            case "application/vnd.android.package-archive":
                mime = "apk";
                break;
            case "application/json":
                mime = "json";
                break;
            case "image/png":
                mime = "png";
                break;
            case "image/jpeg":
                mime = "jpg";
                break;
            case "image/heic":
                mime = "heic";
                break;
            case "image/heif":
                mime = "heif";
                break;
            case "image/webp":
                mime = "webp";
                break;
            case "image/gif":
                mime = "gif";
                break;
            case "audio/aac":
                mime = "aac";
                break;
            case "video/mp4":
                mime = "mp4";
                break;
            case "text/x-vcard":
                mime = "vcf";
                break;
            case PLAIN_TEXT:
            case LONG_TEXT:
                mime = "txt";
                break;
            case OCTET_STREAM:
                mime = "bin";
            default:
                mime = null;
        }

        return Optional.ofNullable(mime);
    }
}
