package com.example.eventfinderjava;

public class FavoriteEvent {
    public String id;
    public String name;
    public String date;
    public String time;
    public String imageUrl;
    public String timestampAdded; // ISO 8601 format or timestamp

    public FavoriteEvent(String id, String name, String date, String time, String imageUrl, String timestampAdded) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.time = time;
        this.imageUrl = imageUrl;
        this.timestampAdded = timestampAdded;
    }
}
