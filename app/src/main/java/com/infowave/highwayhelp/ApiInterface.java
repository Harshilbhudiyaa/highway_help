package com.infowave.highwayhelp;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import com.google.gson.JsonObject;

public interface ApiInterface {

    // 1. LOGIN
    @FormUrlEncoded
    @POST("user_login.php")
    Call<JsonObject> loginUser(
            @Field("intent") String intent,
            @Field("phone") String phone,
            @Field("name") String name,
            @Field("fcm_token") String fcmToken
    );

    // 2. REGISTER
    @FormUrlEncoded
    @POST("user_login.php")
    Call<JsonObject> registerUser(
            @Field("intent") String intent,
            @Field("phone") String phone,
            @Field("name") String name,
            @Field("fcm_token") String fcmToken,
            @Field("language") String language,
            @Field("state") String state,
            @Field("city") String city
    );

    // 3. VERIFY OTP
    @FormUrlEncoded
    @POST("verify_otp.php")
    Call<JsonObject> verifyOtp(
            @Field("phone") String phone,
            @Field("otp") String otp
    );

    // 4. GET CATEGORIES
    @GET("get_data.php")
    Call<JsonObject> getMetaData();

    // 5. FIND MECHANIC
    @Multipart
    @POST("find_mechanic.php")
    Call<JsonObject> findMechanic(
            @Part("user_id") RequestBody userId,
            @Part("category") RequestBody category,
            @Part("service") RequestBody service,
            @Part("lat") RequestBody lat,
            @Part("lng") RequestBody lng,
            @Part MultipartBody.Part image
    );

    // 6. CHECK STATUS
    @FormUrlEncoded
    @POST("user_check_status.php")
    Call<JsonObject> checkRequestStatus(
            @Field("request_id") String requestId
    );

    // 7. RATE MECHANIC
    @FormUrlEncoded
    @POST("rate_mechanic.php")
    Call<JsonObject> rateMechanic(
            @Field("request_id") String requestId,
            @Field("rating") float rating,
            @Field("review") String review
    );

    // 8. GET HISTORY (NEW)
    @FormUrlEncoded
    @POST("get_user_history.php")
    Call<JsonObject> getHistory(
            @Field("user_id") String userId
    );
}