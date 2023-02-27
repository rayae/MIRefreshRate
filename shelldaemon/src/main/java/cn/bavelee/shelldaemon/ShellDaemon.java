package cn.bavelee.shelldaemon;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class ShellDaemon {

    private static final String DEFAULT_TAG = "ShellDaemon";
    private final Object mCallbackLock = new Object();

    private String TAG = DEFAULT_TAG;
    private List<IOutputWatcher> mOutputWatchers;
    private Process process = null;
    private DataOutputStream stdin = null;
    private OutputReader stdout = null;
    private OutputReader stderr = null;
    private boolean isDebugEnabled = false;
    private boolean isRootShell = false;
    private boolean isMergeErrorOutput = false;


    public static class Builder {
        private ShellDaemon daemon = new ShellDaemon();

        /**
         * @param isMergeErrorOutput 是否合并错误输出流到标准输出，默认值 false
         * @return ShellDaemon.Builder
         */
        public Builder setMergeErrorOutput(boolean isMergeErrorOutput) {
            daemon.isMergeErrorOutput = isMergeErrorOutput;
            return this;
        }

        /**
         * @param rootShell 是否启动ROOT Shell，默认值 false
         * @return ShellDaemon.Builder
         */
        public Builder setRootShell(boolean rootShell) {
            daemon.isRootShell = rootShell;
            return this;
        }

        public Builder setDebugEnabled(boolean isDebugEnabled) {
            daemon.isDebugEnabled = isDebugEnabled;
            return this;
        }


        /**
         * @param watcher 增加输出监听器，可有多个监听器
         * @return ShellDaemon.Builder
         */
        public Builder addWatcher(IOutputWatcher watcher) {
            if (daemon.mOutputWatchers == null) daemon.mOutputWatchers = new ArrayList<>();
            daemon.mOutputWatchers.add(watcher);
            return this;
        }

        public Builder setTag(String tag) {
            daemon.TAG = tag;
            return this;
        }

        public ShellDaemon build() {
            daemon.setup();
            return daemon;
        }
    }

    private ShellDaemon() {
    }


    private void setup() {
        try {
            String sh = "sh";
            boolean isRootPermitted = isRootPermitted();
            if (isRootShell && isRootPermitted)
                sh = "su";
            debug("init() isRootShell=" + isRootShell + " isRootPermitted=" + isRootPermitted);
            process = Runtime.getRuntime().exec(sh);
            stdin = new DataOutputStream(process.getOutputStream());
            IOutputDelegate stdoutDelegate = new IOutputDelegate() {
                @Override
                public void output(String text) {
                    synchronized (mCallbackLock) {
                        debug("onStdout() " + text);
                        if (mOutputWatchers != null)
                            for (IOutputWatcher watcher : mOutputWatchers)
                                watcher.onStdout(TAG, text);
                    }
                }
            };
            stdout = new OutputReader(new BufferedReader(new InputStreamReader(process.getInputStream())), stdoutDelegate);

            // 若合并错误输出，则使用标准输出流的输出代理
            if (!isMergeErrorOutput) {
                stderr = new OutputReader(new BufferedReader(new InputStreamReader(process.getErrorStream())),
                        new IOutputDelegate() {
                            @Override
                            public void output(String text) {
                                synchronized (mCallbackLock) {
                                    debug("onStderr() " + text);
                                    if (mOutputWatchers != null)
                                        for (IOutputWatcher watcher : mOutputWatchers)
                                            watcher.onStderr(TAG, text);
                                }
                            }
                        });
            } else {
                stderr = new OutputReader(new BufferedReader(new InputStreamReader(process.getErrorStream())), stdoutDelegate);
            }
            stdout.start();
            stderr.start();
        } catch (Exception ignored) {
        }
    }

    private void debug(String msg) {
        if (isDebugEnabled) {
            Log.d(TAG, msg);
        }
    }

    public void addWatcher(IOutputWatcher watcher) {
        if (watcher == null || mOutputWatchers == null) {
            return;
        }
        mOutputWatchers.add(watcher);
    }

    public void removeWatcher(IOutputWatcher watcher) {
        if (watcher == null || mOutputWatchers == null) {
            return;
        }
        mOutputWatchers.remove(watcher);
    }

    public static boolean isRootPermitted() {
        String suPath = ShellDaemon.which("su");
        if (suPath != null) {
            File suFile = new File(suPath);
            return suFile.canExecute();
        }
        return false;
    }


    /**
     * 资源回收
     */
    public void destroy() {
        try {
            stdin.writeBytes("exit $?\n");
            stdin.flush();
            int resultCode = process.waitFor();

            synchronized (mCallbackLock) {
                debug("onFinish() " + resultCode);
                if (mOutputWatchers != null)
                    for (IOutputWatcher watcher : mOutputWatchers)
                        watcher.onFinish(TAG, resultCode);
            }

        } catch (Exception ignored) {

        } finally {
            try {
                stdout.cancel();
                stderr.cancel();
                stdin.close();
                stdout.close();
                stderr.close();
                process.destroy();
            } catch (Exception ignored) {

            }
        }
        if (mOutputWatchers != null)
            mOutputWatchers.clear();
        debug("destroy()");
    }


    /**
     * @param commands 按顺序执行的多条指令
     */
    public void execute(List<String> commands) {
        for (String cmd : commands) {
            debug("execute() " + cmd);
            synchronized (mCallbackLock) {
                debug("onCommand() " + cmd);
                if (mOutputWatchers != null)
                    for (IOutputWatcher watcher : mOutputWatchers)
                        watcher.onCommand(TAG, cmd);
            }
            try {
                stdin.writeBytes(cmd);
                stdin.writeBytes("\n");
                stdin.flush();
            } catch (Exception ignored) {

            }
        }
    }

    /**
     * @param command 执行单条指令
     */
    public void execute(String command) {
        execute(Collections.singletonList(command));
    }


    /**
     * @param which 判断环境变量中命令是否存在且可执行可执行
     * @return 可执行文件的路径
     */
    public static String which(String which) {
        String systemPath = System.getenv("PATH");
        if (systemPath == null) return null;
        String[] pathDirs = systemPath.split(File.pathSeparator);
        for (String pathDir : pathDirs) {
            File file = new File(pathDir, which);
            if (file.isFile()) {
                return file.getPath();
            }
        }
        return null;
    }

    /**
     * @param command 执行指令且
     * @return 返回输出结果
     */
    public static String executeForResult(boolean isRoot, String command) {
        StringBuilder output = new StringBuilder();
        executeForAll(isRoot, command, output);
        return output.toString();
    }

    /**
     * @param command 执行指令且
     * @return 返回输出结果
     */
    public static String executeForResult(String command) {
        return executeForResult(false, command);
    }

    /**
     * @param command 执行指令
     * @return 返回指令结束时的返回值
     */
    public static int executeForExitCode(String command) {
        return executeForExitCode(false, command);
    }

    /**
     * @param command 执行指令
     * @return 返回指令结束时的返回值
     */
    public static int executeForExitCode(boolean isRoot, String command) {
        return executeForAll(isRoot, command, null);
    }

    /**
     * @param command 执行指令
     * @param output  输出文本
     * @return 返回输出结果
     */
    public static int executeForAll(boolean isRoot, String command, StringBuilder output) {
        Process p = null;
        BufferedReader reader = null;
        try {
            p = Runtime.getRuntime().exec(isRoot ? "su" : "sh");
            if (isRoot) {
                DataOutputStream stdin = new DataOutputStream(p.getOutputStream());
                stdin.writeBytes(command);
                stdin.writeBytes("\n");
                stdin.writeBytes("exit $?\n");
                stdin.flush();
                stdin.close();
            }
            int ret = p.waitFor();
            if (output == null) return ret;
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return ret;
        } catch (Exception ignored) {

        } finally {
            try {
                if (reader != null)
                    reader.close();
                if (p != null)
                    p.destroy();
            } catch (Exception ignored) {

            }
        }
        return -1;
    }
}
