package org.autojs.autojs.core.plugin.center;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Process;
import android.util.Base64;

import androidx.annotation.Keep;

import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/** Runs from the host APK with app_process under an already authorized Shizuku/Root identity. */
@Keep
public final class PluginLauncherComponentCommand {
    private PluginLauncherComponentCommand() {}

    public static void main(String[] args) {
        Timer deadline = new Timer(true);
        deadline.schedule(new TimerTask() {
            @Override public void run() { System.err.println("Launcher update timed out"); System.exit(3); }
        }, 15000L);
        try {
            if (Process.myUid() != 0 && Process.myUid() != 2000) throw new SecurityException("Shizuku or Root is required");
            if (args.length != 3) throw new IllegalArgumentException("Expected package, user and component states");
            String packageName = args[0];
            int userId = Integer.parseInt(args[1]);
            JSONObject states = new JSONObject(new String(Base64.decode(args[2], Base64.URL_SAFE), StandardCharsets.UTF_8));
            if (states.length() > 128 || userId < 0) throw new IllegalArgumentException("Invalid launcher update");
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getDeclaredMethod("getService", String.class).invoke(null, "package");
            Class<?> contract = Class.forName("android.content.pm.IPackageManager");
            Object manager = Class.forName("android.content.pm.IPackageManager$Stub")
                    .getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);
            Method setter = null;
            for (Method method : contract.getMethods()) {
                if (method.getName().equals("setComponentEnabledSetting") &&
                        (method.getParameterTypes().length == 4 || method.getParameterTypes().length == 5)) {
                    setter = method;
                    break;
                }
            }
            if (setter == null) throw new NoSuchMethodException("setComponentEnabledSetting");
            Method getter = contract.getMethod("getComponentEnabledSetting", ComponentName.class, int.class);
            List<ComponentName> components = new ArrayList<>();
            List<Integer> before = new ArrayList<>();
            List<Integer> after = new ArrayList<>();
            for (Iterator<String> names = states.keys(); names.hasNext();) {
                String name = names.next();
                int state = states.getInt(name);
                if (state < 0 || state > 4 || name.isEmpty()) throw new IllegalArgumentException("Invalid component state");
                ComponentName component = new ComponentName(packageName, name);
                components.add(component);
                before.add((Integer) getter.invoke(manager, component, userId));
                after.add(state);
            }
            int applied = 0;
            try {
                for (; applied < components.size(); applied++) {
                    set(setter, manager, components.get(applied), after.get(applied), userId);
                }
            } catch (Throwable failure) {
                for (int i = applied - 1; i >= 0; i--) {
                    try { set(setter, manager, components.get(i), before.get(i), userId); }
                    catch (Throwable rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                }
                throw failure;
            }
            System.out.println("AUTOJS_LAUNCHER_OK");
        } catch (Throwable failure) {
            Throwable cause = failure instanceof InvocationTargetException ? failure.getCause() : failure;
            System.err.println(cause == null ? failure.toString() : cause.toString());
            System.exit(2);
        } finally {
            deadline.cancel();
        }
        System.exit(0);
    }

    private static void set(Method setter, Object manager, ComponentName component, int state, int userId) throws Exception {
        // Keep running plugin services alive while changing only launcher components.
        if (setter.getParameterTypes().length == 5) {
            setter.invoke(manager, component, state, PackageManager.DONT_KILL_APP, userId, "shell:" + Process.myUid());
        } else {
            setter.invoke(manager, component, state, PackageManager.DONT_KILL_APP, userId);
        }
    }
}
