package cn.bavelee.mirefreshrate;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import cn.bavelee.shelldaemon.ShellDaemon;

public class MainActivity extends AppCompatActivity implements Handler.Callback {
    private static final String TAG = "MIRR";
    private String SQLITE3_PATH;
    private Handler handler;
    private final ArrayMap<String, AppInfo> packageList = new ArrayMap<>();
    private RecyclerView recyclerView;
    private AppAdapter appAdapter;
    public static final int MSG_ROOT_PERMITTED = 2001;
    public static final int MSG_DATABASE_INITIALIZED = MSG_ROOT_PERMITTED + 1;
    public static final int MSG_APP_LIST_LOADED = MSG_DATABASE_INITIALIZED + 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper(), this);
        setContentView(R.layout.activity_main);
        checkIsROOTPermitted();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menuClearDataOfPowerKeeper) {
            clearDataOfPowerKeeper();
        } else if (item.getItemId() == R.id.menuOpenRefreshActivity) {
            openRefreshActivity();
        } else if (item.getItemId() == R.id.menuStopPowerKeeper) {
            stopPowerKeeper();
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void initSqlite3Binary() {
        SQLITE3_PATH = getFilesDir().getPath() + "/sqlite3";
        File file = new File(SQLITE3_PATH);
        if (!file.canExecute()) {
            try {
                FileOutputStream fos = new FileOutputStream(file);
                InputStream is = getAssets().open("sqlite3");
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                    fos.flush();
                }
                is.close();
                fos.close();
                file.setExecutable(true);
                file.setReadable(true);
                file.setWritable(false);
            } catch (Exception ignore) {

            }
        }
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        EditText etFilterText = findViewById(R.id.etFilterText);
        etFilterText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                appAdapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        findViewById(R.id.btnGlobalRefreshRate).setOnClickListener(v -> appAdapter.setGlobalRefreshRate());
        findViewById(R.id.btnApplyCustom).setOnClickListener(v -> new AlertDialog.Builder(v.getContext())
                .setTitle("警告")
                .setCancelable(false)
                .setMessage("本程序不保证一定有用，请自行尝试，造成不良后果自行承担；出现问题清除【电量和性能】的数据即可恢复原配置。")
                .setPositiveButton("我已知晓", (dialog, which) -> {
                    StringBuilder sb = new StringBuilder();
                    for (AppInfo ai : appAdapter.getOriginList()) {
                        if (ai.getRefreshRate() == 0) continue;
                        sb.append(ai.getPkg()).append(":").append(ai.getRefreshRate()).append(",");
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    String command = String.format("%s /data/data/com.miui.powerkeeper/databases/user_configure.db \"insert into misc(name, value) values('key_priv_names', '%s');\"", SQLITE3_PATH,
                            sb.toString());
                    StringBuilder output = new StringBuilder();
                    Log.d(TAG, "sqlite write => " + command);
                    if (ShellDaemon.executeForAll(true, command, output) == 0) {
                        stopPowerKeeper();
                        showMessage("写入成功，已自动重启【电量和性能】，可能需要几分钟乃至十几分钟才生效，或者你可以选择重启手机");
                    } else {
                        showMessage("写入失败：可能你需要清除【电量和性能】的数据后再试" + output.toString());
                    }
                })
                .setNegativeButton("算了", (dialog, which) -> dialog.cancel())
                .show());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private void checkIsDataBaseFileExists() {
        new Thread(() -> {
            handler.sendMessage(handler.obtainMessage(MSG_DATABASE_INITIALIZED, ShellDaemon.executeForExitCode(true, "ls /data/data/com.miui.powerkeeper/databases/user_configure.db") == 0));
        }).start();
    }

    private void checkIsROOTPermitted() {
        LoadingDialog.show(this, "正在检查ROOT权限...");
        new Thread(() -> {
            handler.sendMessage(handler.obtainMessage(MSG_ROOT_PERMITTED, ShellDaemon.executeForExitCode(true, "ls /data/app") == 0));
        }).start();
    }

    private void showList() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        List<AppInfo> list = new ArrayList<>();
        for (AppInfo ai : packageList.values()) {
            if (!TextUtils.isEmpty(ai.getName()))
                list.add(ai);
        }
        appAdapter = new AppAdapter(this, list);
        appAdapter.sortList();
        recyclerView.setAdapter(appAdapter);
    }

    private void openRefreshActivity() {
        ShellDaemon.executeForExitCode(true, "am broadcast -W -a android.provider.Telephony.SECRET_CODE -d android_secret_code://37263");
    }

    private void stopPowerKeeper() {
        ShellDaemon.executeForExitCode(true, "am force-stop com.miui.powerkeeper");
    }

    private void clearDataOfPowerKeeper() {
        ShellDaemon.executeForExitCode(true, "pm clear com.miui.powerkeeper");
    }

    private void loadAppList() {
        LoadingDialog.show(MainActivity.this, "正在加载应用列表...");
        new Thread(() -> {
            packageList.clear();
            try {
                List<PackageInfo> packageInfoList = getPackageManager().getInstalledPackages(PackageManager.GET_ACTIVITIES |
                        PackageManager.GET_SERVICES);
                for (PackageInfo info : packageInfoList) {
                    packageList.put(info.packageName, new AppInfo(info.applicationInfo.loadLabel(getPackageManager()).toString(), info.packageName, 0));
                }
                String command = String.format("%s /data/data/com.miui.powerkeeper/databases/user_configure.db \"select value from misc where name='key_priv_names';\"", SQLITE3_PATH);
                String data = ShellDaemon.executeForResult(true, command);
                Log.d(TAG, "user_configure.db => " + data);
                data = data.trim().replace("\n", "");
                String[] arr = data.split(",");
                for (String str : arr) {
                    String[] split = str.split(":");
                    if (split == null || split.length != 2) break;
                    String name = packageList.containsKey(split[0]) ? packageList.get(split[0]).getName() : "";
                    packageList.put(split[0], new AppInfo(name, split[0], Integer.parseInt(split[1])));
                }
                handler.sendMessage(handler.obtainMessage(MSG_APP_LIST_LOADED, true));
            } catch (Throwable e) {
                showMessage("请确认你是小米手机且是MIUI系统！！！" + e.toString());
            }
        }).start();
    }

    @Override
    public boolean handleMessage(@NonNull Message msg) {
        LoadingDialog.dismiss();
        switch (msg.what) {
            case MSG_ROOT_PERMITTED:
                if (msg.obj == Boolean.FALSE) {
                    showMessage("没有获取到 ROOT 权限，拜拜了您嘞！", true);
                } else {
                    initSqlite3Binary();
                    initViews();
                    checkIsDataBaseFileExists();
                }
                break;
            case MSG_APP_LIST_LOADED:
                showList();
                break;
            case MSG_DATABASE_INITIALIZED:
                if (msg.obj == Boolean.FALSE) {
                    showMessage("请确认你是小米手机且是MIUI系统！！！或者你的【电量与性能】还没有生成数据库文件(/data/data/com.miui.powerkeeper/databases/user_configure.db)，请打开几个应用使用一会手机再来试试。", true);
                } else {
                    loadAppList();
                }
                break;
        }
        return false;
    }


    private void showMessage(String msg) {
        showMessage(msg, false);
    }

    private void showMessage(String msg, boolean directlyExit) {
        handler.post(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle("提示")
                .setMessage(msg)
                .setPositiveButton("确定", (dialog, which) -> {
                    dialog.cancel();
                    if (directlyExit) finishAndRemoveTask();
                })
                .show());
    }

}