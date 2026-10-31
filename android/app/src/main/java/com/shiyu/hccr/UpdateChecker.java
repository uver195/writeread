package com.shiyu.hccr;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * 写读板 v1.5.8 在线更新检查器。
 *
 * 流程：
 *  1. 拉取 update.json（版本清单，托管在 GitHub raw）。
 *  2. 比对 versionCode。
 *  3. 有新版 -> 弹「立即更新 / 稍后」。
 *  4. 立即更新 -> 后台下载 APK -> 校验 sha256（若清单提供）-> 唤起系统安装器。
 *
 * 仅检查更新时联网；手写识别与朗读仍完全离线。不收集、不上传任何数据。
 */
public class UpdateChecker {

    /** update.json list address (multi-mirror, try in order, fallback on failure). */
    private static final String[] UPDATE_JSON_URLS = {
            "https://raw.githubusercontent.com/uver195/writeread/main/update.json",
            "https://gh-proxy.com/https://raw.githubusercontent.com/uver195/writeread/main/update.json",
            "https://ghfast.top/https://raw.githubusercontent.com/uver195/writeread/main/update.json",
            "https://ghproxy.net/https://raw.githubusercontent.com/uver195/writeread/main/update.json",
            "https://ghproxy.cc/https://raw.githubusercontent.com/uver195/writeread/main/update.json"
    };

    /** 下载目录名（getExternalFilesDir 下）。 */
    private static final String UPDATE_DIR = "updates";

    /** 检查更新。silent=true 时仅在「有新版」时弹窗；无新版/出错时静默。 */
    public static void check(final Activity activity, final boolean silent) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String body = fetchFirstAvailable(UPDATE_JSON_URLS, 10000, 10000);
                if (body == null) {
                    fail(activity, silent, activity.getString(R.string.update_check_failed));
                    return;
                }
                JSONObject json = new JSONObject(body);
                int remoteCode = json.optInt("versionCode", -1);
                final String remoteName = json.optString("versionName", "");
                final List<String> downloadUrls = parseDownloadUrls(json);
                final String changelog = json.optString("changelog", "");
                final String sha256 = json.optString("sha256", "");
                if (remoteCode <= 0 || downloadUrls.isEmpty()) {
                    fail(activity, silent, activity.getString(R.string.update_manifest_bad));
                    return;
                }
                android.content.pm.PackageInfo pi =
                        activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                long localCode = PackageInfoCompat.getLongVersionCode(pi);
                final String localName = pi.versionName != null ? pi.versionName : "";
                final boolean hasUpdate = remoteCode > localCode;
                activity.runOnUiThread(() -> {
                    if (hasUpdate) {
                        showUpdateDialog(activity, localName, remoteName, downloadUrls, changelog, sha256);
                    } else if (!silent) {
                        Toast.makeText(activity,
                                activity.getString(R.string.update_latest, localName),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                fail(activity, silent, activity.getString(R.string.update_check_failed)
                        + ": " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static void fail(final Activity activity, final boolean silent, final String msg) {
        activity.runOnUiThread(() -> {
            if (!silent) Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
        });
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }

    private static void showUpdateDialog(final Activity activity, String localName, String versionName,
                                         final List<String> downloadUrls, String changelog, final String sha256) {
        String msg = activity.getString(R.string.update_found_message, localName, versionName, changelog);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_found_title)
                .setMessage(msg)
                .setPositiveButton(R.string.update_now,
                        (d, w) -> startDownload(activity, downloadUrls, sha256))
                .setNegativeButton(R.string.update_later, null)
                .show();
    }

    private static void startDownload(final Activity activity, final List<String> downloadUrls, final String sha256) {
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setMessage(activity.getString(R.string.update_downloading));
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                File dir = new File(activity.getExternalFilesDir(null), UPDATE_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    downloadFailed(activity, pd, activity.getString(R.string.update_download_failed));
                    return;
                }
                File apk = new File(dir, "writeread-update.apk");
                if (apk.exists()) apk.delete();

                // v1.5.14: 多镜像按序尝试，一个失败自动切下一个，全部失败才报错
                String lastErr = null;
                boolean success = false;
                for (String url : downloadUrls) {
                    if (url == null || url.isEmpty()) continue;
                    try {
                        conn = (HttpURLConnection) new URL(url).openConnection();
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(60000);
                        conn.setInstanceFollowRedirects(true);
                        conn.setRequestMethod("GET");
                        conn.connect();
                        int code = conn.getResponseCode();
                        if (code != 200) {
                            lastErr = activity.getString(R.string.update_download_failed)
                                    + " (" + code + ") @ " + hostOf(url);
                            continue;
                        }
                        long total = conn.getContentLength();
                        InputStream is = conn.getInputStream();
                        MessageDigest md = (sha256 != null && !sha256.isEmpty())
                                ? MessageDigest.getInstance("SHA-256") : null;
                        try (OutputStream os = new FileOutputStream(apk)) {
                            byte[] buf = new byte[8192];
                            int n;
                            long done = 0;
                            while ((n = is.read(buf)) != -1) {
                                os.write(buf, 0, n);
                                if (md != null) md.update(buf, 0, n);
                                done += n;
                                if (total > 0) {
                                    final int pct = (int) (done * 100 / total);
                                    activity.runOnUiThread(() -> pd.setProgress(pct));
                                }
                            }
                        }
                        if (md != null) {
                            String got = bytesToHex(md.digest());
                            if (!got.equalsIgnoreCase(sha256)) {
                                apk.delete();
                                downloadFailed(activity, pd, activity.getString(R.string.update_hash_mismatch));
                                return;
                            }
                        }
                        success = true;
                        break;
                    } catch (Exception e) {
                        lastErr = activity.getString(R.string.update_download_failed)
                                + ": " + e.getMessage();
                        if (conn != null) { try { conn.disconnect(); } catch (Throwable ignored) {} }
                        conn = null;
                    }
                }
                if (!success) {
                    downloadFailed(activity, pd, lastErr != null ? lastErr
                            : activity.getString(R.string.update_download_failed));
                    return;
                }
                activity.runOnUiThread(() -> { if (pd.isShowing()) pd.dismiss(); });
                installApk(activity, apk);
            } catch (Exception e) {
                downloadFailed(activity, pd, activity.getString(R.string.update_download_failed)
                        + ": " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static void downloadFailed(Activity activity, final ProgressDialog pd, final String msg) {
        activity.runOnUiThread(() -> {
            if (pd.isShowing()) pd.dismiss();
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
        });
    }

    /** 从 update.json 解析下载地址：优先 downloadUrls 数组，回退单 downloadUrl。 */
    private static List<String> parseDownloadUrls(JSONObject json) {
        List<String> urls = new ArrayList<>();
        JSONArray arr = json.optJSONArray("downloadUrls");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String u = arr.optString(i, "");
                if (u != null && !u.isEmpty()) urls.add(u);
            }
        }
        String single = json.optString("downloadUrl", "");
        if (urls.isEmpty() && single != null && !single.isEmpty()) urls.add(single);
        return urls;
    }

    /** 按序尝试多个 URL，返回第一个 HTTP 200 的响应体；全部失败返回 null。 */
    private static String fetchFirstAvailable(String[] urls, int connectTimeout, int readTimeout) {
        for (String u : urls) {
            if (u == null || u.isEmpty()) continue;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(connectTimeout);
                c.setReadTimeout(readTimeout);
                c.setInstanceFollowRedirects(true);
                c.setRequestMethod("GET");
                c.connect();
                if (c.getResponseCode() == 200) return readAll(c.getInputStream());
            } catch (Exception ignored) {
                // try next mirror
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    private static String hostOf(String url) {
        try { return new URL(url).getHost(); } catch (Exception e) { return url; }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    /** 唤起系统安装器安装下载好的 APK。 */
    private static void installApk(Activity activity, File apk) {
        if (!apk.exists()) return;
        Uri uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".fileprovider", apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            // 系统仍无法安装（多为未授权"安装未知应用"），引导用户去设置
            Toast.makeText(activity, R.string.update_install_unknown_sources, Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= 26) {
                Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                s.setData(Uri.parse("package:" + activity.getPackageName()));
                s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { activity.startActivity(s); } catch (Exception ignored) { }
            }
        }
    }
}
