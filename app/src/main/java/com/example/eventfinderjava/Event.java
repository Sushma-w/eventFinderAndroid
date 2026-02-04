package com.example.eventfinderjava;

public class Event {
    public String id;
    public String name;
    public String venue;
    public String date; // Original date (YYYY-MM-DD) for sorting
    public String dateFormatted; // Formatted date (MMM d, yyyy) for display
    public String time;
    public String time24; // Store original 24-hour format time for sorting
    public String imageUrl;
    public String segment; // Category segment (Music, Sports, Arts & Theatre, etc.)
    public boolean isFavorite;

    public Event(String id, String name, String venue, String date, String dateFormatted, String time, String time24, String imageUrl, String segment) {
        this.id = id;
        this.name = name;
        this.venue = venue;
        this.date = date;
        this.dateFormatted = dateFormatted;
        this.time = time;
        this.time24 = time24;
        this.imageUrl = imageUrl;
        this.segment = segment;
        this.isFavorite = false;
    }
}

