package com.classic.sonorstream;
//Todo: to complete

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ClientListAdapter extends RecyclerView.Adapter<ClientListAdapter.mViewholder> {
    private final HashMap<String,String> scan_res;

    public ClientListAdapter(HashMap<String,String>scanResults) {
        scan_res = scanResults;
    }

    @NonNull
    @Override
    public mViewholder onCreateViewHolder(ViewGroup parent, int viewType) {
        // to inflate the layout for each item of recycler view.
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.client_list_card, parent, false);
        return new mViewholder(view);
    }

    @Override
    public void onBindViewHolder(final mViewholder holder,final int position) {
        TextView dev_name = holder.dev_name;
        TextView dev_ip = holder.dev_ip;
        Map.Entry<String, String> entry = new ArrayList<>(scan_res.entrySet()).get(position);
        dev_name.setText(entry.getValue());
        dev_ip.setText(entry.getKey());
    }

    @Override
    public int getItemCount() {
        return scan_res.size();
    }

    public static class mViewholder extends RecyclerView.ViewHolder {
        TextView dev_name;
        TextView dev_ip;
        public mViewholder(View itemView) {
            super(itemView);
            dev_name = itemView.findViewById(R.id.device_name);
            dev_ip = itemView.findViewById(R.id.client_ip);
        }
    }
}
