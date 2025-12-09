package com.infowave.highwayhelp;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TrackMechanicActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private String requestId, mechName, mechPhone;
    private TextView tvStatus, tvMechName, tvMechPhone;
    private Marker mechMarker;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable trackingRunnable;
    private boolean isTracking = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track_mechanic);

        requestId = getIntent().getStringExtra("request_id");
        mechName = getIntent().getStringExtra("mech_name");
        mechPhone = getIntent().getStringExtra("mech_phone");

        tvStatus = findViewById(R.id.tvStatus);
        tvMechName = findViewById(R.id.tvMechName);
        tvMechPhone = findViewById(R.id.tvMechPhone);

        tvMechName.setText(mechName);
        tvMechPhone.setText(mechPhone);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        findViewById(R.id.btnCallMech).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + mechPhone));
            startActivity(intent);
        });

        startTrackingLoop();
    }

    private void startTrackingLoop() {
        trackingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTracking) return;
                checkStatusAndLocation();
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(trackingRunnable);
    }

    private void checkStatusAndLocation() {
        ApiInterface api = ApiClient.getClient().create(ApiInterface.class);
        api.checkRequestStatus(requestId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().get("success").getAsBoolean()) {
                        String status = response.body().get("status").getAsString();
                        updateStatusUI(status);

                        if (response.body().has("mechanic")) {
                            JsonObject mech = response.body().getAsJsonObject("mechanic");
                            double lat = mech.get("lat").getAsDouble();
                            double lng = mech.get("lng").getAsDouble();
                            updateMechanicMarker(lat, lng);
                        }

                        if (status.equals("COMPLETED")) {
                            isTracking = false;
                            String amount = response.body().get("amount").getAsString();
                            showBillDialog(amount);
                        }
                    }
                }
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    private void updateStatusUI(String status) {
        switch (status) {
            case "ACCEPTED":
                tvStatus.setText("Mechanic is on the way...");
                tvStatus.setBackgroundColor(0xFFE3F2FD);
                break;
            case "ARRIVED":
                tvStatus.setText("Mechanic has Arrived! 📍");
                tvStatus.setBackgroundColor(0xFFE8F5E9);
                break;
            case "COMPLETED":
                tvStatus.setText("Job Completed ✅");
                break;
        }
    }

    private void updateMechanicMarker(double lat, double lng) {
        if (mMap == null) return;
        LatLng mechLoc = new LatLng(lat, lng);
        if (mechMarker == null) {
            mechMarker = mMap.addMarker(new MarkerOptions()
                    .position(mechLoc)
                    .title("Mechanic")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mechLoc, 15f));
        } else {
            mechMarker.setPosition(mechLoc);
        }
    }

    private void showBillDialog(String amount) {
        new AlertDialog.Builder(this)
                .setTitle("Job Completed")
                .setMessage("Total Bill Amount: ₹" + amount + "\nPlease pay cash to the mechanic.")
                .setCancelable(false)
                .setPositiveButton("OK, PAID", (dialog, which) -> {
                    dialog.dismiss();
                    showRatingDialog(); // Next Step: Rating
                })
                .show();
    }

    // NEW: Rating Dialog Logic
    private void showRatingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_rating, null);
        builder.setView(view);
        builder.setCancelable(false);

        AlertDialog dialog = builder.create();
        dialog.show();

        RatingBar ratingBar = view.findViewById(R.id.ratingBar);
        EditText etReview = view.findViewById(R.id.etReview);
        Button btnSubmit = view.findViewById(R.id.btnSubmitRating);

        btnSubmit.setOnClickListener(v -> {
            float rating = ratingBar.getRating();
            String review = etReview.getText().toString();
            submitRating(rating, review, dialog);
        });
    }

    private void submitRating(float rating, String review, AlertDialog dialog) {
        ApiInterface api = ApiClient.getClient().create(ApiInterface.class);
        api.rateMechanic(requestId, rating, review).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                Toast.makeText(TrackMechanicActivity.this, "Thank you for your feedback!", Toast.LENGTH_LONG).show();
                dialog.dismiss();

                // Go Home
                Intent intent = new Intent(TrackMechanicActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            }
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Toast.makeText(TrackMechanicActivity.this, "Error submitting rating", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                finish();
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isTracking = false;
        handler.removeCallbacks(trackingRunnable);
    }
}