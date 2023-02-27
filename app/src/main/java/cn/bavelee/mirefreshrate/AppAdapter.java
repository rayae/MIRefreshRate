package cn.bavelee.mirefreshrate;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    private Context context;
    private List<AppInfo> originList;
    private List<AppInfo> currentList;
    private int selectedIndex = 0;

    public AppAdapter(Context context, List<AppInfo> originList) {
        this.context = context;
        this.originList = originList;
        this.currentList = originList;
    }

    public void filter(String text) {
        if (text == null || text.length() == 0) {
            currentList = originList;
            notifyDataSetChanged();
            return;
        }
        currentList = new ArrayList<>();
        for (AppInfo ai : originList) {
            if (ai.getName().contains(text) || ai.getPkg().contains(text))
                currentList.add(ai);
        }
        notifyDataSetChanged();
    }

    public void setGlobalRefreshRate() {
        final String[] refreshRates = context.getResources().getStringArray(R.array.refresh_rate_values);
        new AlertDialog.Builder(context)
                .setTitle("设置所有应用的刷新率")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (AppInfo ai : originList)
                            ai.setRefreshRate(selectedIndex == 0 ? 0 : Integer.parseInt(refreshRates[selectedIndex]));
                        notifyDataSetChanged();
                    }
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.cancel())
                .setSingleChoiceItems(R.array.refresh_rate_values, 0, (dialog, which) -> {
                    selectedIndex = which;

                }).show();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(context).inflate(R.layout.item_app, parent, false));
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppInfo ai = currentList.get(position);
        holder.tvName.setText(ai.getName().isEmpty() ? "未安装" : ai.getName());
        holder.tvPkg.setText(ai.getPkg());
        holder.tvRefreshRate.setText(ai.getRefreshRate() == 0 ? "" : ai.getRefreshRate() + " Hz");
        holder.itemView.setOnClickListener(v -> {
            final String[] refreshRates = context.getResources().getStringArray(R.array.refresh_rate_values);
            int checkedItem = 0;
            int i = 0;
            for (String r : refreshRates) {
                if (r.equals(String.valueOf(ai.getRefreshRate())))
                    checkedItem = i;
                i++;
            }
            new AlertDialog.Builder(v.getContext())
                    .setTitle("设置刷新率")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ai.setRefreshRate(selectedIndex == 0 ? 0 : Integer.parseInt(refreshRates[selectedIndex]));
                            sortList();
                            notifyDataSetChanged();
                        }
                    })
                    .setNegativeButton("取消", (dialog, which) -> dialog.cancel())
                    .setSingleChoiceItems(R.array.refresh_rate_values, checkedItem, (dialog, which) -> {
                        selectedIndex = which;

                    }).show();
        });
    }

    public void sortList() {
        Collections.sort(originList, (o1, o2) -> o2.getRefreshRate() - o1.getRefreshRate());
    }

    @Override
    public int getItemCount() {
        return currentList.size();
    }

    public List<AppInfo> getOriginList() {
        return originList;
    }

    public static class Holder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvPkg;
        TextView tvRefreshRate;

        public Holder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvPkg = itemView.findViewById(R.id.tvPkg);
            tvRefreshRate = itemView.findViewById(R.id.tvRefreshRate);
        }
    }
}
