package com.infowave.highwayhelp;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    ProgressBar progressBar;
    SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layout Programmatically set kar rahe hain taaki nayi XML file na banani pade (Optional)
        // Ya aap activity_history.xml bana sakte hain. Main easy way use kar raha hu:
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        sessionManager = new SessionManager(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fetchHistory();
    }

    private void fetchHistory() {
        progressBar.setVisibility(View.VISIBLE);
        String userId = sessionManager.getUserId();

        ApiInterface api = ApiClient.getClient().create(ApiInterface.class);
        api.getHistory(userId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().get("success").getAsBoolean()) {
                        JsonArray data = response.body().getAsJsonArray("data");
                        List<JsonObject> list = new ArrayList<>();
                        for(JsonElement e : data) list.add(e.getAsJsonObject());

                        HistoryAdapter adapter = new HistoryAdapter(list);
                        recyclerView.setAdapter(adapter);
                    } else {
                        Toast.makeText(HistoryActivity.this, "No history found", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(HistoryActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}