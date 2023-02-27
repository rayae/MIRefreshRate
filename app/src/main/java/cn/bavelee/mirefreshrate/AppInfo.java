package cn.bavelee.mirefreshrate;

public class AppInfo {
    private String name;
    private String pkg;
    private int refreshRate;

    public AppInfo(String name, String pkg, int refreshRate) {
        this.name = name;
        this.pkg = pkg;
        this.refreshRate = refreshRate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPkg() {
        return pkg;
    }

    public void setPkg(String pkg) {
        this.pkg = pkg;
    }

    public int getRefreshRate() {
        return refreshRate;
    }

    public void setRefreshRate(int refreshRate) {
        this.refreshRate = refreshRate;
    }

    @Override
    public String toString() {
        return refreshRate <= 0 ? "" : pkg + ":" + refreshRate;
    }
}
