package cn.bavelee.shelldaemon;

public interface IOutputWatcher {
    void onStdout(String tag, String text);

    void onStderr(String tag, String text);

    void onCommand(String tag, String command);

    void onFinish(String tag, int resultCode);
}