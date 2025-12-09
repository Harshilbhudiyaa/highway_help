package com.infowave.highwayhelp;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.location.Location;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.bumptech.glide.Glide;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private final int REQUEST_CHECK_SETTINGS = 1002;

    private Button btnRequestMechanic, btnCancelSearch;
    private MaterialCardView btnHistory; // NEW
    private TextView tvLocation;
    private RelativeLayout layoutRadar;
    private ImageView pulse1, pulse2;

    private JsonArray categoriesData = new JsonArray();
    private String selectedVehicle = "";
    private String selectedIssue = "";
    private Location lastKnownLocation;
    private SessionManager sessionManager;

    private String currentRequestId = "";
    private Handler statusHandler = new Handler(Looper.getMainLooper());
    private Runnable statusRunnable;
    private boolean isSearching = false;

    private File selectedImageFile = null;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private TextView tvPhotoStatusRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);

        btnRequestMechanic = findViewById(R.id.btnRequestMechanic);
        tvLocation = findViewById(R.id.tvLocation);
        layoutRadar = findViewById(R.id.layoutRadar);
        pulse1 = findViewById(R.id.pulse1);
        pulse2 = findViewById(R.id.pulse2);
        btnCancelSearch = findViewById(R.id.btnCancelSearch);
        btnHistory = findViewById(R.id.btnHistory); // NEW

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        fetchDynamicData();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        try {
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                            selectedImageFile = bitmapToFile(bitmap);
                            if(tvPhotoStatusRef != null) tvPhotoStatusRef.setText("Image Selected ✅");
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                }
        );

        btnRequestMechanic.setOnClickListener(v -> {
            if (categoriesData.size() > 0) showDynamicRequestPanel();
            else fetchDynamicData();
        });

        btnCancelSearch.setOnClickListener(v -> stopSearching());

        // HISTORY BUTTON CLICK (NEW)
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });
    }

    private void startSearchingAnimation() {
        isSearching = true;
        layoutRadar.setVisibility(View.VISIBLE);
        btnRequestMechanic.setVisibility(View.GONE);
        startPulse(pulse1, 1000);
        startPulse(pulse2, 1500);
        startStatusCheckLoop();
    }

    private void stopSearching() {
        isSearching = false;
        layoutRadar.setVisibility(View.GONE);
        btnRequestMechanic.setVisibility(View.VISIBLE);
        pulse1.clearAnimation();
        pulse2.clearAnimation();
        statusHandler.removeCallbacks(statusRunnable);
        Toast.makeText(this, "Search Stopped", Toast.LENGTH_SHORT).show();
    }

    private void startPulse(View view, int duration) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 4f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 4f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f);

        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        alpha.setRepeatCount(ObjectAnimator.INFINITE);

        scaleX.setDuration(duration);
        scaleY.setDuration(duration);
        alpha.setDuration(duration);

        scaleX.start(); scaleY.start(); alpha.start();
    }

    private void startStatusCheckLoop() {
        statusRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isSearching) return;
                checkServerStatus();
                statusHandler.postDelayed(this, 5000);
            }
        };
        statusHandler.post(statusRunnable);
    }

    private void checkServerStatus() {
        if (currentRequestId.isEmpty()) return;

        ApiInterface api = ApiClient.getClient().create(ApiInterface.class);
        api.checkRequestStatus(currentRequestId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (response.body().get("success").getAsBoolean()) {
                        String status = response.body().get("status").getAsString();

                        if (status.equals("ACCEPTED")) {
                            isSearching = false;
                            statusHandler.removeCallbacks(statusRunnable);

                            JsonObject mech = response.body().getAsJsonObject("mechanic");
                            String mechName = mech.get("name").getAsString();
                            String mechPhone = mech.get("phone").getAsString();
                            double mechLat = mech.get("lat").getAsDouble();
                            double mechLng = mech.get("lng").getAsDouble();

                            Toast.makeText(MainActivity.this, "Mechanic Found: " + mechName, Toast.LENGTH_LONG).show();

                            Intent intent = new Intent(MainActivity.this, TrackMechanicActivity.class);
                            intent.putExtra("request_id", currentRequestId);
                            intent.putExtra("mech_name", mechName);
                            intent.putExtra("mech_phone", mechPhone);
                            intent.putExtra("mech_lat", mechLat);
                            intent.putExtra("mech_lng", mechLng);
                            startActivity(intent);
                            finish();
                        }
                    }
                }
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    private void sendRequestToServer() {
        if(lastKnownLocation == null) {
            checkDeviceLocationSettings();
            return;
        }

        startSearchingAnimation();

        RequestBody userId = RequestBody.create(MediaType.parse("text/plain"), sessionManager.getUserId());
        RequestBody category = RequestBody.create(MediaType.parse("text/plain"), selectedVehicle);
        RequestBody service = RequestBody.create(MediaType.parse("text/plain"), selectedIssue);
        RequestBody lat = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(lastKnownLocation.getLatitude()));
        RequestBody lng = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(lastKnownLocation.getLongitude()));

        MultipartBody.Part body = null;
        if(selectedImageFile != null) {
            RequestBody reqFile = RequestBody.create(MediaType.parse("image/*"), selectedImageFile);
            body = MultipartBody.Part.createFormData("image", selectedImageFile.getName(), reqFile);
        }

        ApiInterface api = ApiClient.getClient().create(ApiInterface.class);
        Call<JsonObject> call = api.findMechanic(userId, category, service, lat, lng, body);

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if(response.isSuccessful() && response.body() != null) {
                    if(response.body().get("success").getAsBoolean()) {
                        currentRequestId = response.body().get("request_id").getAsString();
                    } else {
                        stopSearching();
                        Toast.makeText(MainActivity.this, "Failed: " + response.body().get("message").getAsString(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                stopSearching();
                Toast.makeText(MainActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private File bitmapToFile(Bitmap bitmap) {
        try {
            File f = new File(getCacheDir(), "req_" + System.currentTimeMillis() + ".jpg");
            f.createNewFile();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, bos);
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(bos.toByteArray());
            fos.flush(); fos.close();
            return f;
        } catch (Exception e) { return null; }
    }

    private void showDynamicRequestPanel() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(R.layout.bottom_sheet_request);

        LinearLayout layoutStep1 = sheet.findViewById(R.id.layoutStep1);
        LinearLayout layoutStep2 = sheet.findViewById(R.id.layoutStep2);
        LinearLayout btnChangeVehicle = sheet.findViewById(R.id.btnChangeVehicle);
        TextView tvSelectedVehicle = sheet.findViewById(R.id.tvSelectedVehicle);
        Button btnSearch = sheet.findViewById(R.id.btnSearchMechanic);
        MaterialCardView btnAddPhoto = sheet.findViewById(R.id.btnAddPhoto);
        tvPhotoStatusRef = sheet.findViewById(R.id.tvPhotoStatus);

        GridLayout gridVehicles = sheet.findViewById(R.id.gridVehicles);
        GridLayout gridIssues = sheet.findViewById(R.id.gridIssues);

        btnAddPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            imagePickerLauncher.launch(intent);
        });

        gridVehicles.removeAllViews();
        for (JsonElement element : categoriesData) {
            JsonObject cat = element.getAsJsonObject();
            String name = cat.get("name").getAsString();
            String iconUrl = cat.has("icon") ? cat.get("icon").getAsString() : "";

            View cardView = LayoutInflater.from(this).inflate(R.layout.item_category, gridVehicles, false);
            TextView tvName = cardView.findViewById(R.id.tvName);
            ImageView imgIcon = cardView.findViewById(R.id.imgIcon);
            MaterialCardView root = cardView.findViewById(R.id.cardRoot);

            tvName.setText(name);
            if (!iconUrl.isEmpty()) Glide.with(this).load(iconUrl).override(100, 100).into(imgIcon);

            root.setOnClickListener(v -> {
                selectedVehicle = name;
                tvSelectedVehicle.setText("Selected: " + name);
                layoutStep1.setVisibility(View.GONE);
                layoutStep2.setVisibility(View.VISIBLE);
                loadIssuesForVehicle(cat.getAsJsonArray("services"), gridIssues);
            });
            gridVehicles.addView(cardView);
        }

        btnChangeVehicle.setOnClickListener(v -> {
            layoutStep2.setVisibility(View.GONE);
            layoutStep1.setVisibility(View.VISIBLE);
        });

        btnSearch.setOnClickListener(v -> {
            if(selectedIssue.isEmpty()) {
                Toast.makeText(this, "Select an issue", Toast.LENGTH_SHORT).show();
            } else {
                sheet.dismiss();
                sendRequestToServer();
            }
        });

        sheet.show();
    }

    private void loadIssuesForVehicle(JsonArray services, GridLayout grid) {
        grid.removeAllViews();
        selectedIssue = "";
        for (JsonElement s : services) {
            JsonObject serviceObj = s.getAsJsonObject();
            String serviceName = serviceObj.get("name").getAsString();
            String iconUrl = serviceObj.has("icon") ? serviceObj.get("icon").getAsString() : "";

            View cardView = LayoutInflater.from(this).inflate(R.layout.item_category, grid, false);
            TextView tvName = cardView.findViewById(R.id.tvName);
            ImageView imgIcon = cardView.findViewById(R.id.imgIcon);
            MaterialCardView root = cardView.findViewById(R.id.cardRoot);

            tvName.setText(serviceName);
            if (!iconUrl.isEmpty()) Glide.with(this).load(iconUrl).override(100, 100).into(imgIcon);

            root.setOnClickListener(v -> {
                selectedIssue = serviceName;
                root.setStrokeColor(0xFF0056D2);
                root.setStrokeWidth(5);
            });
            grid.addView(cardView);
        }
    }

    private void fetchDynamicData() {
        ApiInterface api = ApiClient.getClient().create(ApiInterface.class);
        api.getMetaData().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if(response.isSuccessful() && response.body() != null && response.body().has("data")) {
                    categoriesData = response.body().getAsJsonArray("data");
                }
            }
            @Override public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        checkLocationPermission();
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            checkDeviceLocationSettings();
        }
    }

    private void checkDeviceLocationSettings() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnSuccessListener(this, locationSettingsResponse -> enableUserLocation());
        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try { ((ResolvableApiException) e).startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS); } catch (IntentSender.SendIntentException sendEx) {}
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == RESULT_OK) enableUserLocation();
    }

    private void enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            LocationRequest mLocationRequest = new LocationRequest();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setInterval(0);
            mLocationRequest.setFastestInterval(0);
            mLocationRequest.setNumUpdates(1);
            fusedLocationClient.requestLocationUpdates(mLocationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    Location loc = locationResult.getLastLocation();
                    if(loc != null) {
                        lastKnownLocation = loc;
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(loc.getLatitude(), loc.getLongitude()), 15f));
                        tvLocation.setText("📍 Location Found");
                    }
                }
            }, Looper.getMainLooper());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) checkDeviceLocationSettings();
    }
}