package net.kenevans.android.hxmmonitor;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class UriUtils implements IConstants {
    /**
     * Checks if a file exists for the given document Uri.
     *
     * @param context The context.
     * @param uri     The document Uri.
     * @return Whether it exists.
     */
    public static boolean exists(Context context, Uri uri) {
        // !!!!!!!!!!!!!!!!!!!!! A kludge. Needs to be tested.
//        Log.d(TAG, "exists: uri=" + uri.getLastPathSegment());
        try (Cursor cursor = context.getContentResolver().query(uri,
                null, null, null, null)) {
            return (cursor != null && cursor.moveToFirst());
        } catch (Exception ex) {
            return false;
        }
    }

    /***
     * Gets the file name from the given Uri,
     * @param uri The Uri.
     * @return The file name or null if not determined.
     */
    public static String getFileNameFromUri(Uri uri) {
        if (uri == null) return null;
        String lastSeg = uri.getLastPathSegment();
        String[] tokens = lastSeg.split("/");
        int len = tokens.length;
        if (tokens == null || len == 0) return null;
        return tokens[len - 1];
    }

    /**
     * Gets the display name for a given documentUri.
     *
     * @param context The context.
     * @param uri     The document Uri.
     * @return The name.
     */
    public static String getDisplayName(Context context, Uri uri) {
        String displayName = null;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null,
                null, null)) {
            cursor.moveToFirst();
            int colIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (colIndex < 0) {
                displayName = "NA";
            } else {
                displayName = cursor.getString(colIndex);
            }
        } catch (Exception ex) {
            Utils.excMsg(context, "Error getting display name", ex);
        }
        return displayName;
    }

    /**
     * Check if the mime type of a given document Uri represents a
     * directory.
     *
     * @param context The context.
     * @param uri     The document Uri.
     * @return Whether the Uri represents a directory.
     */
    static public boolean isDirectory(Context context, Uri uri) {
        if (!DocumentsContract.isDocumentUri(context, uri)) return false;
        ContentResolver contentResolver = context.getContentResolver();
        String mimeType = "NA";
        try (Cursor cursor = contentResolver.query(uri, new String[]{
                        DocumentsContract.Document.COLUMN_MIME_TYPE},
                null, null, null)) {
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                mimeType = cursor.getString(0);
            }
        }
        return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
    }

    /**
     * Gets a List of the children of the given document Uri that match the
     * given extension.
     *
     * @param uri A document Uri.
     * @param ext The extension.
     * @return The list.
     */
    public static List<UriData> getChildren(Context context, Uri uri,
                                            String ext) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(uri,
                        DocumentsContract.getTreeDocumentId(uri));
        List<UriData> children = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(childrenUri,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                },
                null,
                null,
                null)) {
            String documentId;
            Uri documentUri;
            long modifiedTime;
            String displayName;
            while (cursor.moveToNext()) {
                documentId = cursor.getString(0);
                documentUri = DocumentsContract.buildDocumentUriUsingTree(uri,
                        documentId);
                if (documentUri == null) continue;
                modifiedTime = cursor.getLong(1);
                displayName = cursor.getString(2);
                String name = documentUri.getLastPathSegment();
                if (name != null) {
                    if (name.toLowerCase().endsWith(ext)) {
                        children.add(new UriData(documentUri, modifiedTime,
                                displayName));
                    }
                }
            }
        }
        // Do nothing
        return children;
    }

    /**
     * Releases all permissions for the given Context.
     *
     * @param context The context.
     */
    public static void releaseAllPermissions(Context context) {
        ContentResolver resolver = context.getContentResolver();
        final List<UriPermission> permissionList =
                resolver.getPersistedUriPermissions();
        int nPermissions = permissionList.size();
        if (nPermissions == 0) {
//            Utils.warnMsg(this, "There are no persisted permissions");
            return;
        }
        Uri uri;
        for (UriPermission permission : permissionList) {
            uri = permission.getUri();
            resolver.releasePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
    }

    /**
     * Removes all but the most recent nToKeep permissions.
     *
     * @param context The context.
     */
    public static void trimPermissions(Context context, int nToKeep) {
        ContentResolver resolver = context.getContentResolver();
        final List<UriPermission> permissionList =
                resolver.getPersistedUriPermissions();
        int nPermissions = permissionList.size();
        if (nPermissions <= nToKeep) return;
        // Add everything in permissionList to sortedList
        List<UriPermission> sortedList = new ArrayList<>(permissionList);
        // Sort with newest first
        Collections.sort(sortedList,
                (p1, p2) -> Long.compare(p2.getPersistedTime(),
                        p1.getPersistedTime()));
        for (int i = nToKeep; i < nPermissions; i++) {
            UriPermission permission = sortedList.get(i);
            resolver.releasePersistableUriPermission(permission.getUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
    }

    /**
     * Returns the number of persisted permissions.
     *
     * @param context The context.
     * @return The number of persisted permissions or -1 on error.
     */
    public static int getNPersistedPermissions(Context context) {
        ContentResolver resolver = context.getContentResolver();
        List<UriPermission> permissionList =
                resolver.getPersistedUriPermissions();
        return permissionList.size();
    }

    /**
     * Returns information about the persisted permissions.
     *
     * @param context The context.
     * @return The information as a formatted string.
     */
    public static String showPermissions(Context context) {
        ContentResolver resolver = context.getContentResolver();
        List<UriPermission> permissionList =
                resolver.getPersistedUriPermissions();
        StringBuilder sb = new StringBuilder();
        sb.append("Persistent Permissions").append("\n");
        for (UriPermission permission : permissionList) {
            sb.append(permission.getUri()).append("\n");
            sb.append("    time=").
                    append(new Date(permission.getPersistedTime())).append(
                    "\n");
            sb.append("    access=").append(permission.isReadPermission() ?
                    "R" : "").append(permission.isWritePermission() ? "W" :
                    "").append("\n");
            sb.append("    special objects flag=").
                    append(permission.describeContents()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Gets the application UID.  This is a unique user ID (UID) to each
     * Android application when it is installed.
     *
     * @param context The Context.
     * @return The UID or -1 on failure.
     */
    public static int getApplicationUid(Context context) {
        int uid = -1;
        try {
            ApplicationInfo info =
                    context.getPackageManager().getApplicationInfo(
                            context.getPackageName(), 0);
            uid = info.uid;
        } catch (Exception ex) {
            Log.e(TAG, "getApplicationUid: Failed to get UID", ex);
        }
        return uid;
    }

    /**
     * Convenience class for managing Uri information.
     */
    public static class UriData {
        final public Uri uri;
        final public long modifiedTime;
        final public String displayName;

        UriData(Uri uri, long modifiedTime, String displayName) {
            this.uri = uri;
            this.modifiedTime = modifiedTime;
            this.displayName = displayName;
        }
    }
}
