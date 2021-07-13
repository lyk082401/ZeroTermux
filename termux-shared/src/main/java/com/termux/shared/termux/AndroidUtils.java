package com.termux.shared.termux;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.common.base.Joiner;
import com.termux.shared.logger.Logger;
import com.termux.shared.markdown.MarkdownUtils;
import com.termux.shared.packages.PackageUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AndroidUtils {

    /**
     * Get a markdown {@link String} for the app info for the package associated with the {@code context}.
     *
     * @param context The context for operations for the package.
     * @return Returns the markdown {@link String}.
     */
    public static String getAppInfoMarkdownString(@NonNull final Context context) {
        StringBuilder markdownString = new StringBuilder();

        AndroidUtils.appendPropertyToMarkdown(markdownString,"APP_NAME", PackageUtils.getAppNameForPackage(context));
        AndroidUtils.appendPropertyToMarkdown(markdownString,"PACKAGE_NAME", PackageUtils.getPackageNameForPackage(context));
        AndroidUtils.appendPropertyToMarkdown(markdownString,"VERSION_NAME", PackageUtils.getVersionNameForPackage(context));
        AndroidUtils.appendPropertyToMarkdown(markdownString,"VERSION_CODE", PackageUtils.getVersionCodeForPackage(context));
        AndroidUtils.appendPropertyToMarkdown(markdownString,"TARGET_SDK", PackageUtils.getTargetSDKForPackage(context));
        AndroidUtils.appendPropertyToMarkdown(markdownString,"IS_DEBUG_BUILD", PackageUtils.isAppForPackageADebugBuild(context));

        String filesDir = context.getFilesDir().getAbsolutePath();
        if (!filesDir.equals("/data/user/0/" + context.getPackageName() + "/files") &&
            !filesDir.equals("/data/data/" + context.getPackageName() + "/files"))
            AndroidUtils.appendPropertyToMarkdown(markdownString,"FILES_DIR", filesDir);

        Long userId = PackageUtils.getSerialNumberForCurrentUser(context);
        if (userId == null || userId != 0)
            AndroidUtils.appendPropertyToMarkdown(markdownString,"USER_ID", userId);

        AndroidUtils.appendPropertyToMarkdownIfSet(markdownString,"PROFILE_OWNER", PackageUtils.getProfileOwnerPackageNameForUser(context));


        return markdownString.toString();
    }

    /**
     * Get a markdown {@link String} for the device info.
     *
     * @param context The context for operations.
     * @return Returns the markdown {@link String}.
     */
    public static String getDeviceInfoMarkdownString(@NonNull final Context context) {
        // Some properties cannot be read with {@link System#getProperty(String)} but can be read
        // directly by running getprop command
        Properties systemProperties = getSystemProperties();

        StringBuilder markdownString = new StringBuilder();

        markdownString.append("## Device Info");

        markdownString.append("\n\n### Software\n");
        appendPropertyToMarkdown(markdownString,"OS_VERSION", getSystemPropertyWithAndroidAPI("os.version"));
        appendPropertyToMarkdown(markdownString, "SDK_INT", Build.VERSION.SDK_INT);
        // If its a release version
        if ("REL".equals(Build.VERSION.CODENAME))
            appendPropertyToMarkdown(markdownString, "RELEASE", Build.VERSION.RELEASE);
        else
            appendPropertyToMarkdown(markdownString, "CODENAME", Build.VERSION.CODENAME);
        appendPropertyToMarkdown(markdownString, "ID", Build.ID);
        appendPropertyToMarkdown(markdownString, "DISPLAY", Build.DISPLAY);
        appendPropertyToMarkdown(markdownString, "INCREMENTAL", Build.VERSION.INCREMENTAL);
        appendPropertyToMarkdownIfSet(markdownString, "SECURITY_PATCH", systemProperties.getProperty("ro.build.version.security_patch"));
        appendPropertyToMarkdownIfSet(markdownString, "IS_DEBUGGABLE", systemProperties.getProperty("ro.debuggable"));
        appendPropertyToMarkdownIfSet(markdownString, "IS_EMULATOR", systemProperties.getProperty("ro.boot.qemu"));
        appendPropertyToMarkdownIfSet(markdownString, "IS_TREBLE_ENABLED", systemProperties.getProperty("ro.treble.enabled"));
        appendPropertyToMarkdown(markdownString, "TYPE", Build.TYPE);
        appendPropertyToMarkdown(markdownString, "TAGS", Build.TAGS);

        markdownString.append("\n\n### Hardware\n");
        appendPropertyToMarkdown(markdownString, "MANUFACTURER", Build.MANUFACTURER);
        appendPropertyToMarkdown(markdownString, "BRAND", Build.BRAND);
        appendPropertyToMarkdown(markdownString, "MODEL", Build.MODEL);
        appendPropertyToMarkdown(markdownString, "PRODUCT", Build.PRODUCT);
        appendPropertyToMarkdown(markdownString, "BOARD", Build.BOARD);
        appendPropertyToMarkdown(markdownString, "HARDWARE", Build.HARDWARE);
        appendPropertyToMarkdown(markdownString, "DEVICE", Build.DEVICE);
        appendPropertyToMarkdown(markdownString, "SUPPORTED_ABIS", Joiner.on(", ").skipNulls().join(Build.SUPPORTED_ABIS));

        markdownString.append("\n##\n");

        return markdownString.toString();
    }



    public static Properties getSystemProperties() {
        Properties systemProperties = new Properties();

        // getprop commands returns values in the format `[key]: [value]`
        // Regex matches string starting with a literal `[`,
        // followed by one or more characters that do not match a closing square bracket as the key,
        // followed by a literal `]: [`,
        // followed by one or more characters as the value,
        // followed by string ending with literal `]`
        // multiline values will be ignored
        Pattern propertiesPattern = Pattern.compile("^\\[([^]]+)]: \\[(.+)]$");

        try {
            Process process = new ProcessBuilder()
                .command("/system/bin/getprop")
                .redirectErrorStream(true)
                .start();

            InputStream inputStream = process.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line, key, value;

            while ((line = bufferedReader.readLine()) != null) {
                Matcher matcher = propertiesPattern.matcher(line);
                if (matcher.matches()) {
                    key = matcher.group(1);
                    value = matcher.group(2);
                    if (key != null && value != null && !key.isEmpty() && !value.isEmpty())
                        systemProperties.put(key, value);
                }
            }

            bufferedReader.close();
            process.destroy();

        } catch (IOException e) {
            Logger.logStackTraceWithMessage("Failed to get run \"/system/bin/getprop\" to get system properties.", e);
        }

        //for (String key : systemProperties.stringPropertyNames()) {
        //    Logger.logVerbose(key + ": " +  systemProperties.get(key));
        //}

        return systemProperties;
    }

    public static String getSystemPropertyWithAndroidAPI(@NonNull String property) {
        try {
            return System.getProperty(property);
        } catch (Exception e) {
            Logger.logVerbose("Failed to get system property \"" + property + "\":" + e.getMessage());
            return null;
        }
    }

    public static void appendPropertyToMarkdownIfSet(StringBuilder markdownString, String label, Object value) {
        if (value == null) return;
        if (value instanceof String && (((String) value).isEmpty()) || "REL".equals(value)) return;
        markdownString.append("\n").append(getPropertyMarkdown(label, value));
    }

    public static void appendPropertyToMarkdown(StringBuilder markdownString, String label, Object value) {
        markdownString.append("\n").append(getPropertyMarkdown(label, value));
    }

    public static String getPropertyMarkdown(String label, Object value) {
        return MarkdownUtils.getSingleLineMarkdownStringEntry(label, value, "-");
    }



    public static String getCurrentTimeStamp() {
        @SuppressLint("SimpleDateFormat")
        final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date());
    }

    public static String getCurrentMilliSecondLocalTimeStamp() {
        @SuppressLint("SimpleDateFormat")
        final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS");
        df.setTimeZone(TimeZone.getDefault());
        return df.format(new Date());
    }

}
