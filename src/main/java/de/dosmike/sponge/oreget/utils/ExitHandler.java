package de.dosmike.sponge.oreget.utils;

import de.dosmike.sponge.oreget.OreGet;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** I could probably start and detach a process that waits
 * for the server to terminate before just performing the actions
 * dumped into scripts here, but for security and management
 * purpose, i decided to not do that. If you don't trust the script
 * generated by the plugin you can always decide to not run it. */
public class ExitHandler extends Thread {

    static Set<File> markForDelete = new HashSet<>();
    static Map<File, File> moveFiles = new HashMap<>();
    private static boolean singleRun =false;

    /** can't delete file because sponge will at least hold ono to jars until the vm exits.
     * With a shutdown hook, this should also write if the server is ^C-ed (some people seem
     * to do that on local test environments) */
    public static void attach() {
        if (singleRun) return; else singleRun = true;

        Runtime.getRuntime().addShutdownHook(new ExitHandler());
    }
    ExitHandler() {
        Thread.currentThread().setName("OreGet Exit Handler");
    }

    @Override
    public void run() {
        OreGet.getPluginCache().save();
        OreGet.getPluginCache().notifyExitHandler();
        try {
            OreGet.getOre().close();
        } catch (Exception ignore) {
            /* overwrite signature for closeable thorws, make the ide happy with this catch */
        }

        // os detection from https://stackoverflow.com/questions/14288185/detecting-windows-or-linux/14288297
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win"))
            createScript(true);
        else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix"))
            createScript(false);
        else
            System.err.println("Operating system nor supported");
    }

    private void createScript(boolean osWindows) {
        String scriptName = osWindows ? "oreget_postserver.bat" : "oreget_postserver.sh";
        File scriptTarget = new File(".", scriptName);

        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(scriptTarget)));
            pwComment(pw, osWindows, "This file was automatically generated by OreGet");
            pwComment(pw, osWindows, "Please append execution of this script in your server watchdog, after the");
            pwComment(pw, osWindows, "server has halted for OreGet to work as intended.");
            pwComment(pw, osWindows, "If you do not trust automatic execution, you can always perform the listed");
            pwComment(pw, osWindows, "actions yourself, while the server is halted");
            pwComment(pw, osWindows, "");
            pwComment(pw, osWindows, "Deleting plugins marked for removal");
            for (File f : markForDelete) {
                if (!moveFiles.values().contains(f)) {
                    pwErase(pw, osWindows, f.getPath());
                }
            }
            pwComment(pw, osWindows, "");
            pwComment(pw, osWindows, "Moving new plugins into the mods directory");
            for (Map.Entry<File, File> entry : moveFiles.entrySet()) {
                File a = entry.getKey();
                File b = entry.getValue();
                pwMove(pw, osWindows, a.getPath(), b.getPath());
            }
            pwComment(pw, osWindows, "");
            pwComment(pw, osWindows, "Clearing cache folder");
            pwErase(pw, osWindows, "oreget_cache" + File.separatorChar + "*.jar");
            pwComment(pw, osWindows, "");
            pwComment(pw, osWindows, "Delete this script to prevent double execution on accident");
            pwErase(pw, osWindows, scriptName);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                pw.flush();
            } catch (Exception ignore) {
            }
            try {
                pw.close();
            } catch (Exception ignore) {
            }
        }
    }
    private void pwComment(PrintWriter pw, boolean osWindows, String string) {
        if (osWindows)
            pw.println("REM "+string);
        else
            pw.println("# "+string);
    }
    private void pwErase(PrintWriter pw, boolean osWindows, String file) {
        if (osWindows)
            pw.println("erase /Q /S "+escapeArgWin(file));
        else
            pw.println("rm -f -r "+escapeArgUnix(file));
    }
    private void pwMove(PrintWriter pw, boolean osWindows, String from, String to) {
        if (osWindows)
            pw.println("move /Y "+escapeArgWin(from)+" "+escapeArgWin(to));
        else
            pw.println("mv -f "+escapeArgUnix(from)+" "+escapeArgWin(to));
    }
    private String escapeArgWin(String arg) {
        if (arg.indexOf(' ')>=0)
            return '"'+arg.replace("\"", "\"\"")+'"'; // " -> "" -> double quotes in a quoted string are interpreted as escaped
        else
            return arg;
    }
    private String escapeArgUnix(String arg) {
        if (arg.indexOf(' ')>=0)
            return '"'+arg.replace("\"", "\"'\"'\"")+'"'; // " -> "'"'" -> ends normal quote, concatenates " in single quotes, concatenates rest
        else
            return arg;
    }

    /** will delete file on exit, undoes move on exit */
    public static void deleteOnExit(File file) {
        markForDelete.add(file);
        moveFiles.remove(file); //shall not be moved anymore
    }
    /** unmarks delete on exit (reinstall) */
    public static void notifyModified(File file) {
        markForDelete.remove(file);
    }
    public static void moveOnExit(File from, File to) {
        moveFiles.put(from, to);
    }

}
