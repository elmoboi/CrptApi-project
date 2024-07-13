package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final HttpClient httpClient;
    private final Gson gson;
    private final Semaphore semaphore;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.semaphore = new Semaphore(requestLimit);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        long period = timeUnit.toMillis(1);
        scheduler.scheduleAtFixedRate(() -> {
            semaphore.release(requestLimit - semaphore.availablePermits());
        }, period, period, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Object document, String signature) throws Exception {
        semaphore.acquire();
        try {
            String json = gson.toJson(document);
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("document", gson.toJsonTree(document));
            jsonObject.addProperty("signature", signature);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to create document: " + response.body());
            }
        } finally {
            semaphore.release();
        }
    }

    public static void main(String[] args) {
        try {
            CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 5);
            Object document = new Object();
            String signature = "signature";
            crptApi.createDocument(document, signature);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
