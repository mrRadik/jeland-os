package ru.jeland.installer;

import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Shell-side installer for Android 11. No root, property writes or vehicle API. */
public final class InstallVariants {
    private static final int UNKNOWN_STATUS = -1000;
    private static final String BUILD_ID = "jeland-api-variants-1.2";

    public static void main(String[] args) {
        int result;
        try {
            result = run(args);
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause instanceof InvocationTargetException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            System.out.println("JELAND_EXCEPTION=" + cause.getClass().getName() + ": " + cause.getMessage());
            cause.printStackTrace(System.out);
            System.out.println("JELAND_RESULT=FAIL");
            result = 1;
        }
        System.out.flush();
        System.exit(result);
    }

    private static Context shellContext() throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        // Context changes attribution only. Linux/Binder UID remains shell.
        return system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
    }

    private static Object binderInstaller() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) serviceManager.getMethod("getService", String.class).invoke(null, "package");
        if (binder == null) throw new IllegalStateException("package service not found");
        Class<?> stub = Class.forName("android.content.pm.IPackageManager$Stub");
        Object manager = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
        return Class.forName("android.content.pm.IPackageManager")
                .getMethod("getPackageInstaller").invoke(manager);
    }

    private static Method createSessionMethod() throws Exception {
        for (Method method : Class.forName("android.content.pm.IPackageInstaller").getMethods()) {
            if (!method.getName().equals("createSession")) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 3 && types[0] == PackageInstaller.SessionParams.class
                    && types[1] == String.class && types[2] == int.class) return method;
            if (types.length == 4 && types[0] == PackageInstaller.SessionParams.class
                    && types[1] == String.class && types[2] == String.class && types[3] == int.class) return method;
        }
        throw new NoSuchMethodException("Unsupported IPackageInstaller.createSession signature");
    }

    // Android 11's ContextImpl can retain opPackageName=android when created from
    // ActivityThread.systemMain(). Attribute this IPC to the actual shell UID's
    // package explicitly, without changing identity, permissions or the Intent.
    static int startActivityAsShell(Object taskManager, Class<?> taskApi, Intent intent,
            String resolvedType, int userId) throws Exception {
        Method start = taskApi.getMethod("startActivityAsUser",
                Class.forName("android.app.IApplicationThread"), String.class, String.class,
                Intent.class, String.class, IBinder.class, String.class,
                int.class, int.class, Class.forName("android.app.ProfilerInfo"), Bundle.class,
                int.class);
        int result = ((Integer) start.invoke(taskManager, null, "com.android.shell", null,
                intent, resolvedType, null, null, 0, 0, null, null, userId)).intValue();
        System.out.println("JELAND_CONFIRM_CALLING_PACKAGE=com.android.shell");
        System.out.println("JELAND_CONFIRM_START_RESULT=" + result);
        // START_SUCCESS..START_DELIVERED_TO_TOP are successful. Internal-only
        // results (100+) such as START_ABORTED are not proof an Activity opened.
        if (result < 0 || result >= 100) {
            throw new IllegalStateException("Confirmation Activity start failed: " + result);
        }
        return result;
    }

    private static void launchConfirmation(Context context, Intent intent) throws Exception {
        ApplicationInfo shell = context.getPackageManager().getApplicationInfo("com.android.shell", 0);
        prepareConfirmation(intent, Process.myUid(), shell.uid, (shell.privateFlags & 8) != 0);
        Class<?> taskApi = Class.forName("android.app.IActivityTaskManager");
        Object tasks = Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService").invoke(null);
        if (tasks == null) throw new IllegalStateException("activity_task service not found");
        startActivityAsShell(tasks, taskApi, intent,
                intent.resolveType(context.getContentResolver()), Process.myUid() / 100000);
    }

    static void prepareConfirmation(Intent intent, int processUid, int shellUid,
            boolean privilegedShell) {
        if (processUid != 2000 || shellUid != processUid || !privilegedShell) {
            throw new SecurityException("Confirmation requires the existing privileged shell package");
        }
        if (!"android.content.pm.action.CONFIRM_INSTALL".equals(intent.getAction())
                || intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) < 0) {
            throw new IllegalArgumentException("Invalid session confirmation Intent");
        }
        // InstallStart accepts this only for a package that is already privileged.
        // This does not grant a permission or mark any ordinary APK as privileged.
        intent.putExtra("android.intent.extra.NOT_UNKNOWN_SOURCE", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        System.out.println("JELAND_CONFIRM_TRUSTED_SHELL=true");
    }

    private static void abandon(Object raw, PackageInstaller publicInstaller, int id) {
        if (id < 0) return;
        try {
            if (raw != null) {
                Class.forName("android.content.pm.IPackageInstaller")
                        .getMethod("abandonSession", int.class).invoke(raw, id);
            } else {
                publicInstaller.abandonSession(id);
            }
            System.out.println("JELAND_ABANDONED_SESSION=" + id);
        } catch (Throwable error) {
            System.out.println("JELAND_ABANDON_NOTE=" + error);
        }
    }

    private static int run(String[] args) throws Exception {
        System.out.println("JELAND_HELPER_BUILD=" + BUILD_ID);
        System.out.println("JELAND_UID=" + Process.myUid());
        System.out.println("JELAND_ANDROID_SDK=" + Build.VERSION.SDK_INT);
        if (Process.myUid() != 2000) throw new SecurityException("Run as shell UID 2000, not root/system");
        Context context = shellContext();
        PackageManager packages = context.getPackageManager();
        PackageInstaller installer = null;
        System.out.println("JELAND_CONTEXT=" + context.getPackageName());
        System.out.println("JELAND_INSTALL_PERMISSION=" + context.checkCallingOrSelfPermission("android.permission.INSTALL_PACKAGES"));

        if (args.length == 1 && args[0].equals("inspect")) {
            Object raw = binderInstaller();
            System.out.println("JELAND_BINDER_CLASS=" + raw.getClass().getName());
            System.out.println("JELAND_CREATE_SIGNATURE=" + createSessionMethod());
            System.out.println("JELAND_RESULT=INSPECT_OK");
            return 0;
        }
        if (args.length < 2) throw new IllegalArgumentException("Usage: <api|binder|confirm> <APK> [timeout seconds]");
        String mode = args[0];
        if (!mode.equals("api") && !mode.equals("binder") && !mode.equals("confirm")) {
            throw new IllegalArgumentException("Unknown variant: " + mode);
        }
        int timeout = args.length > 2 ? Integer.parseInt(args[2]) : 120;
        if (timeout < 5 || timeout > 600) throw new IllegalArgumentException("Timeout must be 5..600 seconds");
        File apk = new File(args[1]);
        if (!apk.isFile() || apk.length() < 4) throw new IllegalArgumentException("APK missing or empty");
        PackageInfo archive = packages.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null || archive.packageName == null) throw new IllegalArgumentException("Cannot read APK manifest");
        System.out.println("JELAND_VARIANT=" + mode);
        System.out.println("JELAND_PACKAGE=" + archive.packageName);
        System.out.println("JELAND_EXPECTED_VERSION=" + archive.getLongVersionCode());
        System.out.println("JELAND_APK_BYTES=" + apk.length());
        try {
            PackageInfo existing = packages.getPackageInfo(archive.packageName, 0);
            System.out.println("JELAND_PREVIOUS_VERSION=" + existing.getLongVersionCode());
            if ((existing.applicationInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
                throw new SecurityException("Refusing to replace a system package: " + archive.packageName);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            System.out.println("JELAND_PREVIOUS_VERSION=ABSENT");
        }

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setSize(apk.length());
        params.setAppPackageName(archive.packageName);
        // Only replace-existing. No downgrade, test-only bypass or blanket grants.
        java.lang.reflect.Field flags = params.getClass().getField("installFlags");
        flags.setInt(params, flags.getInt(params) | 2);
        if (mode.equals("confirm")) {
            ApplicationInfo shell = packages.getApplicationInfo("com.android.shell", 0);
            if (shell.uid != Process.myUid() || (shell.privateFlags & 8) == 0) {
                throw new SecurityException("Shell is not privileged; confirmation variant unavailable");
            }
            params.getClass().getMethod("setInstallFlagsForcePermissionPrompt").invoke(params);
        }
        if (!mode.equals("binder")) {
            System.out.println("JELAND_PHASE=GET_PUBLIC_INSTALLER");
            installer = packages.getPackageInstaller();
        }

        int sessionId = -1;
        PackageInstaller.Session session = null;
        Object raw = null;
        boolean installed = false;
        boolean committed = false;
        boolean uncertain = false;
        try {
            System.out.println("JELAND_PHASE=CREATE_SESSION");
            if (mode.equals("binder")) {
                raw = binderInstaller();
                Method create = createSessionMethod();
                System.out.println("JELAND_CREATE_SIGNATURE=" + create);
                int userId = Process.myUid() / 100000;
                sessionId = ((Integer) (create.getParameterTypes().length == 3
                        ? create.invoke(raw, params, "com.android.shell", userId)
                        : create.invoke(raw, params, "com.android.shell", null, userId))).intValue();
                Class<?> api = Class.forName("android.content.pm.IPackageInstaller");
                Object rawSession = api.getMethod("openSession", int.class).invoke(raw, sessionId);
                Constructor<PackageInstaller.Session> constructor = PackageInstaller.Session.class
                        .getConstructor(Class.forName("android.content.pm.IPackageInstallerSession"));
                session = constructor.newInstance(rawSession);
            } else {
                sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);
            }
            System.out.println("JELAND_SESSION_ID=" + sessionId);
            System.out.println("JELAND_PHASE=WRITE_APK");
            try (FileInputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[65536];
                int count;
                long written = 0;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    written += count;
                }
                session.fsync(output);
                if (written != apk.length()) throw new IllegalStateException("APK length changed during copy");
            }
            InstallStatus status = new InstallStatus(context, mode.equals("confirm"));
            IntentSender sender = new IntentSender((IBinder) status.asBinder());
            System.out.println("JELAND_PHASE=COMMIT");
            session.commit(sender);
            committed = true;
            System.out.println("JELAND_PHASE=WAIT_FINAL_RESULT");
            if (!status.finished.await(timeout, TimeUnit.SECONDS)) {
                uncertain = true;
                System.out.println("JELAND_RESULT=UNKNOWN_TIMEOUT");
                return 3;
            }
            System.out.println("JELAND_FINAL_STATUS=" + status.status);
            System.out.println("JELAND_FINAL_MESSAGE=" + status.message);
            if (status.callbackError != null) throw new IllegalStateException("Result callback failed", status.callbackError);
            if (status.status != PackageInstaller.STATUS_SUCCESS) {
                System.out.println("JELAND_RESULT=" + (status.status == PackageInstaller.STATUS_PENDING_USER_ACTION
                        ? "NEEDS_CONFIRMATION" : "FAIL"));
                return 1;
            }
            System.out.println("JELAND_PHASE=VERIFY_PACKAGE");
            PackageInfo actual = packages.getPackageInfo(archive.packageName, 0);
            System.out.println("JELAND_INSTALLED_VERSION=" + actual.getLongVersionCode());
            if (actual.getLongVersionCode() != archive.getLongVersionCode()) {
                throw new IllegalStateException("Installed version does not match APK");
            }
            installed = true;
            System.out.println("JELAND_RESULT=SUCCESS");
            return 0;
        } finally {
            if (session != null) {
                try { session.close(); } catch (Throwable ignored) { }
            }
            // Abort only this invocation's incomplete session; never uninstall apps.
            if (!installed && !uncertain) abandon(raw, installer, sessionId);
            if (uncertain && committed) {
                System.out.println("JELAND_SESSION_STATE=COMMITTED_RESULT_UNKNOWN; do not retry automatically");
            }
        }
    }

    private static final class InstallStatus extends IIntentSender.Stub {
        final CountDownLatch finished = new CountDownLatch(1);
        final Context context;
        final boolean showConfirmation;
        volatile int status = UNKNOWN_STATUS;
        volatile String message = "No callback";
        volatile Throwable callbackError;
        InstallStatus(Context context, boolean showConfirmation) {
            this.context = context;
            this.showConfirmation = showConfirmation;
        }
        @Override public synchronized void send(int code, Intent intent, String resolvedType,
                IBinder whitelistToken, IIntentReceiver receiver, String permission, Bundle options) {
            try {
                if (intent == null) throw new IllegalArgumentException("Empty result Intent");
                status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, UNKNOWN_STATUS);
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                System.out.println("JELAND_CALLBACK_STATUS=" + status);
                System.out.println("JELAND_CALLBACK_MESSAGE=" + message);
                System.out.println("JELAND_CALLBACK_LEGACY=" + intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS", -999));
                if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && showConfirmation) {
                    Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirmation == null) throw new IllegalStateException("No confirmation Intent");
                    // Clear the Binder callback identity before an IPC made as shell.
                    long identity = android.os.Binder.clearCallingIdentity();
                    try { launchConfirmation(context, confirmation); }
                    finally { android.os.Binder.restoreCallingIdentity(identity); }
                    System.out.println("JELAND_ACTION=CONFIRM_ON_HEAD_UNIT");
                    return;
                }
            } catch (Throwable error) {
                callbackError = error;
                error.printStackTrace(System.out);
            }
            finished.countDown();
        }
    }
}
