package cn.bavelee.shelldaemon;

import java.io.BufferedReader;
import java.io.IOException;

class OutputReader extends Thread {
    private IOutputDelegate IOutputDelegate = null;
    private BufferedReader reader = null;
    private boolean isRunning = false;
    public static int BUFFER_SIZE = 9182;
    private char[] buffer = new char[BUFFER_SIZE];

    public OutputReader(BufferedReader reader, IOutputDelegate IOutputDelegate) {
        this.IOutputDelegate = IOutputDelegate;
        this.reader = reader;
        this.isRunning = true;
    }

    public void close() {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        super.run();
        int count = 0;
        while (isRunning) {
            try {
                count = reader.read(buffer);
                if (count > 0)
                    IOutputDelegate.output(new String(buffer, 0, count));
            } catch (IOException ignored) {
            }
        }
    }

    public void cancel() {
        synchronized (this) {
            isRunning = false;
            this.notifyAll();
        }
    }
}