package com.classic.sonorstream;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ServerListAdapter extends RecyclerView.Adapter<ServerListAdapter.mViewholder> {
    private final HashMap<String,String> scan_res;
    private final ReceiveActivity receiveActivity;

    public ServerListAdapter(HashMap<String,String>scanResults,ReceiveActivity receiveActivity) {
        scan_res = scanResults;
        this.receiveActivity = receiveActivity;
    }

    @NonNull
    @Override
    public mViewholder onCreateViewHolder(ViewGroup parent, int viewType) {
        // to inflate the layout for each item of recycler view.
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.server_list_card, parent, false);
        return new mViewholder(view);
    }

    @Override
    public void onBindViewHolder(final mViewholder holder,final int position) {
        TextView ser_name = holder.ser_name;
        TextView ser_ip = holder.ser_ip;
        Button conn = holder.conn;
        Map.Entry<String, String> entry = new ArrayList<>(scan_res.entrySet()).get(position);
        ser_name.setText(entry.getValue());
        ser_ip.setText(entry.getKey());
        conn.setOnClickListener(v -> {
            try {
                receiveActivity.connect(InetAddress.getByName((String) ser_ip.getText()));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public int getItemCount() {
        return scan_res.size();
    }

    public static class mViewholder extends RecyclerView.ViewHolder {
        TextView ser_name;
        TextView ser_ip;
        Button conn;
        public mViewholder(View itemView) {
            super(itemView);
            ser_name = itemView.findViewById(R.id.server_name);
            ser_ip = itemView.findViewById(R.id.server_ip_addr);
            conn = itemView.findViewById(R.id.conn_to_server);
        }
    }
}
