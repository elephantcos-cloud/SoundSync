package com.elephantcos.soundsync.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.elephantcos.soundsync.R;
import com.elephantcos.soundsync.model.DeviceItem;
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    public interface OnDeviceClickListener { void onDeviceClick(DeviceItem device); }

    private final List<DeviceItem> devices;
    private final OnDeviceClickListener listener;

    public DeviceAdapter(List<DeviceItem> devices, OnDeviceClickListener listener) {
        this.devices  = devices;
        this.listener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        DeviceItem item = devices.get(pos);
        h.name.setText(item.name.isEmpty() ? "Unknown Device" : item.name);
        h.address.setText(item.address);

        String label; int color;
        switch (item.status) {
            case 1:  label = "Connecting…"; color = 0xFFFFB300; break;
            case 2:  label = "Connected";   color = 0xFF43E97B; break;
            default: label = "Available";   color = 0xFF6C63FF; break;
        }
        h.status.setText(label);
        h.status.setTextColor(color);
        h.itemView.setOnClickListener(v -> { if (listener != null) listener.onDeviceClick(item); });
    }

    @Override public int getItemCount() { return devices.size(); }

    public void refresh() { notifyDataSetChanged(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, address, status;
        ViewHolder(View v) {
            super(v);
            name    = v.findViewById(R.id.device_name);
            address = v.findViewById(R.id.device_address);
            status  = v.findViewById(R.id.device_status);
        }
    }
}
