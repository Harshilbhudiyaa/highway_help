package com.infowave.highwayhelp;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonObject;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<JsonObject> historyList;

    public HistoryAdapter(List<JsonObject> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JsonObject item = historyList.get(position);

        String issue = item.get("vehicle_category").getAsString() + " - " + item.get("issue_type").getAsString();
        String status = item.get("status").getAsString();
        String date = item.get("created_at").getAsString();
        String amount = item.has("total_amount") && !item.get("total_amount").isJsonNull() ? "₹" + item.get("total_amount").getAsString() : "₹0";
        String mechName = item.has("mech_name") && !item.get("mech_name").isJsonNull() ? "Mech: " + item.get("mech_name").getAsString() : "Searching...";

        holder.tvIssue.setText(issue);
        holder.tvStatus.setText(status);
        holder.tvDate.setText(date);
        holder.tvAmount.setText(amount);
        holder.tvMechanic.setText(mechName);

        // Color Coding
        if(status.equals("COMPLETED")) {
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else if (status.equals("PENDING")) {
            holder.tvStatus.setTextColor(Color.parseColor("#FF9800")); // Orange
        } else {
            holder.tvStatus.setTextColor(Color.parseColor("#0056D2")); // Blue
        }
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIssue, tvStatus, tvDate, tvAmount, tvMechanic;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIssue = itemView.findViewById(R.id.tvIssue);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvMechanic = itemView.findViewById(R.id.tvMechanic);
        }
    }
}