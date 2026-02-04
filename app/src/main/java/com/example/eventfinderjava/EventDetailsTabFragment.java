package com.example.eventfinderjava;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;
import androidx.fragment.app.Fragment;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventDetailsTabFragment extends Fragment {

    private static final String TAG = "EventDetailsTabFragment";
    private static final String ARG_TAB_TYPE = "tab_type";
    private static final String ARG_EVENT_ID = "event_id";
    private static final String BASE_URL = "https://backend-dot-steam-house-473501-t8.wl.r.appspot.com";

    public static final int TAB_DETAILS = 0;
    public static final int TAB_ARTIST = 1;
    public static final int TAB_VENUE = 2;

    private int tabType;
    private String eventId;
    private RequestQueue requestQueue;
    private ProgressBar progressLoading;
    private View contentView;

    public static EventDetailsTabFragment newInstance(int tabType, String eventId) {
        EventDetailsTabFragment fragment = new EventDetailsTabFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TAB_TYPE, tabType);
        args.putString(ARG_EVENT_ID, eventId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabType = getArguments().getInt(ARG_TAB_TYPE);
            eventId = getArguments().getString(ARG_EVENT_ID);
        }
        requestQueue = Volley.newRequestQueue(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view;
        switch (tabType) {
            case TAB_DETAILS:
                view = inflater.inflate(R.layout.event_details_tab_details, container, false);
                break;
            case TAB_ARTIST:
                view = inflater.inflate(R.layout.event_details_tab_artist, container, false);
                break;
            case TAB_VENUE:
                view = inflater.inflate(R.layout.event_details_tab_venue, container, false);
                break;
            default:
                view = inflater.inflate(R.layout.event_details_tab_details, container, false);
        }

        progressLoading = view.findViewById(R.id.progressLoading);
        contentView = view.findViewById(R.id.contentView);

        // Show loading initially
        if (progressLoading != null) {
            progressLoading.setVisibility(View.VISIBLE);
        }
        if (contentView != null) {
            contentView.setVisibility(View.GONE);
        }

        // Fetch data for this tab
        fetchTabData();

        return view;
    }

    private void fetchTabData() {
        switch (tabType) {
            case TAB_DETAILS:
                fetchDetailsTab();
                break;
            case TAB_ARTIST:
                fetchArtistTab();
                break;
            case TAB_VENUE:
                fetchVenueTab();
                break;
        }
    }

    private void fetchDetailsTab() {
        String url = BASE_URL + "/api/eventdetails?id=" + eventId;
        Log.d(TAG, "Fetching event details from: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, "Event details response received");
                    try {
                        parseAndDisplayData(response);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing event details", e);
                        hideLoading();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching event details", error);
                    hideLoading();
                    // Show error message to user if content view is available
                    if (contentView != null) {
                        TextView errorText = contentView.findViewById(R.id.textContent);
                        if (errorText != null) {
                            errorText.setText("Error loading event details. Please try again.");
                            errorText.setVisibility(View.VISIBLE);
                        }
                    }
                }
            }
        );

        requestQueue.add(request);
    }

    private void fetchArtistTab() {
        // First fetch event details to get artist name
        String eventUrl = BASE_URL + "/api/eventdetails?id=" + eventId;
        Log.d(TAG, "Fetching event details for artist tab from: " + eventUrl);

        JsonObjectRequest eventRequest = new JsonObjectRequest(
            Request.Method.GET,
            eventUrl,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject eventResponse) {
                    try {
                        // Check if it's a music event
                        JSONArray classifications = eventResponse.optJSONArray("classifications");
                        boolean isMusicEvent = false;
                        if (classifications != null && classifications.length() > 0) {
                            JSONObject classification = classifications.getJSONObject(0);
                            JSONObject segment = classification.optJSONObject("segment");
                            if (segment != null) {
                                String segmentName = segment.optString("name", "");
                                isMusicEvent = segmentName.toLowerCase().equals("music");
                            }
                        }

                        if (!isMusicEvent) {
                            // Not a music event - show "No artist data"
                            hideLoading();
                            TextView textContent = contentView.findViewById(R.id.textContent);
                            if (textContent != null) {
                                textContent.setText("No artist data");
                                textContent.setVisibility(View.VISIBLE);
                            }
                            View artistCard = contentView.findViewById(R.id.artistCard);
                            if (artistCard != null) {
                                artistCard.setVisibility(View.GONE);
                            }
                            View albumsSection = contentView.findViewById(R.id.albumsSection);
                            if (albumsSection != null) {
                                albumsSection.setVisibility(View.GONE);
                            }
                            return;
                        }

                        // Get artist name from event
                        JSONObject embedded = eventResponse.optJSONObject("_embedded");
                        if (embedded != null) {
                            JSONArray attractions = embedded.optJSONArray("attractions");
                            if (attractions != null && attractions.length() > 0) {
                                String artistName = attractions.getJSONObject(0).optString("name", "");
                                if (!artistName.isEmpty()) {
                                    fetchArtistInfo(artistName);
                                    return;
                                }
                            }
                        }

                        // No artist found
                        hideLoading();
                        TextView textContent = contentView.findViewById(R.id.textContent);
                        if (textContent != null) {
                            textContent.setText("No artist data");
                            textContent.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing event for artist", e);
                        hideLoading();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching event for artist tab", error);
                    hideLoading();
                    // Show error message to user
                    TextView textContent = contentView.findViewById(R.id.textContent);
                    if (textContent != null) {
                        textContent.setText("Error loading artist data. Please try again.");
                        textContent.setVisibility(View.VISIBLE);
                    }
                }
            }
        );

        requestQueue.add(eventRequest);
    }

    private void fetchArtistInfo(String artistName) {
        String url = BASE_URL + "/api/artist?artist=" + Uri.encode(artistName);
        Log.d(TAG, "Fetching artist info from: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, "Artist info response received");
                    try {
                        displayArtistTab(response);
                        // Fetch albums if we have Spotify link
                        String spotifyLink = response.optString("spotifyLink", "");
                        if (!spotifyLink.isEmpty()) {
                            String artistId = spotifyLink.substring(spotifyLink.lastIndexOf('/') + 1);
                            fetchAlbums(artistId);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing artist info", e);
                        hideLoading();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching artist info", error);
                    hideLoading();
                }
            }
        );

        requestQueue.add(request);
    }

    private void fetchAlbums(String artistId) {
        String url = BASE_URL + "/api/albums?id=" + artistId;
        Log.d(TAG, "Fetching albums from: " + url);

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    Log.d(TAG, "Albums response received: " + response.length() + " albums");
                    try {
                        displayAlbums(response);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing albums", e);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching albums", error);
                }
            }
        );

        requestQueue.add(request);
    }

    private void fetchVenueTab() {
        // First fetch event details to get venue ID
        String eventUrl = BASE_URL + "/api/eventdetails?id=" + eventId;
        Log.d(TAG, "Fetching event details for venue tab from: " + eventUrl);

        JsonObjectRequest eventRequest = new JsonObjectRequest(
            Request.Method.GET,
            eventUrl,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject eventResponse) {
                    try {
                        JSONObject embedded = eventResponse.optJSONObject("_embedded");
                        if (embedded != null) {
                            JSONArray venues = embedded.optJSONArray("venues");
                            if (venues != null && venues.length() > 0) {
                                String venueId = venues.getJSONObject(0).optString("id", "");
                                Log.d(TAG, "Found venue ID: " + venueId);
                                if (!venueId.isEmpty()) {
                                    fetchVenueInfo(venueId);
                                    return;
                                } else {
                                    Log.w(TAG, "Venue ID is empty");
                                }
                            } else {
                                Log.w(TAG, "No venues found in _embedded");
                            }
                        } else {
                            Log.w(TAG, "No _embedded object found in event response");
                        }
                        hideLoading();
                        // Show error message if no venue found
                        if (contentView != null) {
                            TextView textVenueName = contentView.findViewById(R.id.textVenueName);
                            if (textVenueName != null) {
                                textVenueName.setText("No venue data available");
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing event for venue", e);
                        hideLoading();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching event for venue tab", error);
                    hideLoading();
                }
            }
        );

        requestQueue.add(eventRequest);
    }

    private void fetchVenueInfo(String venueId) {
        String url = BASE_URL + "/api/venue?id=" + venueId;
        Log.d(TAG, "Fetching venue info from: " + url);

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(TAG, "Venue info response received");
                    try {
                        displayVenueTab(response);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing venue info", e);
                        hideLoading();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching venue info", error);
                    hideLoading();
                }
            }
        );

        requestQueue.add(request);
    }

    private void parseAndDisplayData(JSONObject response) throws JSONException {
        hideLoading();

        switch (tabType) {
            case TAB_DETAILS:
                displayDetailsTab(response);
                break;
            case TAB_ARTIST:
                displayArtistTab(response);
                break;
            case TAB_VENUE:
                displayVenueTab(response);
                break;
        }
    }

    private void displayDetailsTab(JSONObject response) throws JSONException {
        Log.d(TAG, "Displaying details tab with response: " + response.toString());
        
        // Parse date and time
        String date = "";
        String time = "";
        JSONObject dates = response.optJSONObject("dates");
        if (dates != null) {
            JSONObject start = dates.optJSONObject("start");
            if (start != null) {
                date = formatDate(start.optString("localDate", ""));
                String timeStr = start.optString("localTime", "");
                if (!timeStr.isEmpty()) {
                    time = formatTime(timeStr);
                }
                Log.d(TAG, "Parsed date: " + date + ", time: " + time);
            }
        }

        TextView textDate = contentView.findViewById(R.id.textDate);
        View layoutDate = contentView.findViewById(R.id.layoutDate);
        if (textDate != null && layoutDate != null) {
            String dateTime = date;
            // Only add time if it's present
            if (!time.isEmpty()) {
                dateTime += ", " + time;
            }
            if (dateTime.isEmpty()) {
                layoutDate.setVisibility(View.GONE);
                Log.d(TAG, "Hiding date field - no date value");
            } else {
                textDate.setText(dateTime);
                layoutDate.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set date text: " + dateTime);
            }
        }

        // Parse artists
        List<String> artistNames = new ArrayList<>();
        JSONObject embedded = response.optJSONObject("_embedded");
        if (embedded != null) {
            JSONArray attractions = embedded.optJSONArray("attractions");
            if (attractions != null) {
                Log.d(TAG, "Found " + attractions.length() + " attractions");
                for (int i = 0; i < attractions.length(); i++) {
                    JSONObject attraction = attractions.getJSONObject(i);
                    String name = attraction.optString("name", "");
                    if (!name.isEmpty()) {
                        artistNames.add(name);
                        Log.d(TAG, "Added artist: " + name);
                    }
                }
            } else {
                Log.w(TAG, "No attractions array found in _embedded");
            }
        } else {
            Log.w(TAG, "No _embedded object found in response");
        }
        TextView textArtists = contentView.findViewById(R.id.textArtists);
        View layoutArtists = contentView.findViewById(R.id.layoutArtists);
        if (textArtists != null && layoutArtists != null) {
            String artistsText = artistNames.isEmpty() ? "" : String.join(", ", artistNames);
            if (artistsText.isEmpty()) {
                layoutArtists.setVisibility(View.GONE);
                Log.d(TAG, "Hiding artists field - no artists value");
            } else {
                textArtists.setText(artistsText);
                textArtists.setSelected(true); // Enable marquee
                layoutArtists.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set artists text: " + artistsText);
            }
        }

        // Parse venue
        String venueName = "";
        if (embedded != null) {
            JSONArray venues = embedded.optJSONArray("venues");
            if (venues != null && venues.length() > 0) {
                venueName = venues.getJSONObject(0).optString("name", "");
                Log.d(TAG, "Found venue: " + venueName);
            } else {
                Log.w(TAG, "No venues array found in _embedded");
            }
        }
        TextView textVenue = contentView.findViewById(R.id.textVenue);
        View layoutVenue = contentView.findViewById(R.id.layoutVenue);
        if (textVenue != null && layoutVenue != null) {
            if (venueName.isEmpty()) {
                layoutVenue.setVisibility(View.GONE);
                Log.d(TAG, "Hiding venue field - no venue value");
            } else {
                textVenue.setText(venueName);
                textVenue.setSelected(true); // Enable marquee
                layoutVenue.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set venue text: " + venueName);
            }
        }

        // Parse genres with proper null checks and prevent duplicates
        List<String> genres = new ArrayList<>();
        JSONArray classifications = response.optJSONArray("classifications");
        if (classifications != null && classifications.length() > 0) {
            JSONObject classification = classifications.getJSONObject(0);
            Log.d(TAG, "Found classification object");
            
            // Use a Set to track added genres to prevent duplicates
            java.util.Set<String> addedGenres = new java.util.HashSet<>();
            
            // Check each nested object before accessing
            JSONObject segment = classification.optJSONObject("segment");
            if (segment != null) {
                addGenreIfNotEmpty(genres, addedGenres, segment.optString("name", ""));
            }
            
            JSONObject genre = classification.optJSONObject("genre");
            if (genre != null) {
                addGenreIfNotEmpty(genres, addedGenres, genre.optString("name", ""));
            }
            
            JSONObject subGenre = classification.optJSONObject("subGenre");
            if (subGenre != null) {
                addGenreIfNotEmpty(genres, addedGenres, subGenre.optString("name", ""));
            }
            
            JSONObject type = classification.optJSONObject("type");
            if (type != null) {
                addGenreIfNotEmpty(genres, addedGenres, type.optString("name", ""));
            }
            
            JSONObject subType = classification.optJSONObject("subType");
            if (subType != null) {
                addGenreIfNotEmpty(genres, addedGenres, subType.optString("name", ""));
            }
            
            Log.d(TAG, "Found genres: " + genres);
        } else {
            Log.w(TAG, "No classifications array found");
        }
        LinearLayout layoutGenres = contentView.findViewById(R.id.layoutGenres);
        View layoutGenresContainer = contentView.findViewById(R.id.layoutGenresContainer);
        if (layoutGenres != null && layoutGenresContainer != null) {
            if (genres.isEmpty()) {
                layoutGenresContainer.setVisibility(View.GONE);
                Log.d(TAG, "Hiding genres field - no genres value");
            } else {
                layoutGenresContainer.setVisibility(View.VISIBLE);
                layoutGenres.removeAllViews();
                for (String genre : genres) {
                    TextView genreView = new TextView(requireContext());
                    genreView.setText(genre);
                    genreView.setPadding(12, 6, 12, 6);
                    int bgColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, 0);
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setShape(GradientDrawable.RECTANGLE);
                    drawable.setColor(bgColor);
                    drawable.setCornerRadius(8f * getResources().getDisplayMetrics().density); // 8dp radius
                    genreView.setBackground(drawable);
                    genreView.setTextColor(MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface, 0));
                    genreView.setTextSize(12);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(0, 0, 8, 8);
                    genreView.setLayoutParams(params);
                    layoutGenres.addView(genreView);
                }
            }
        }

        // Parse price ranges
        List<String> priceStrings = new ArrayList<>();
        JSONArray priceRanges = response.optJSONArray("priceRanges");
        if (priceRanges != null) {
            Log.d(TAG, "Found " + priceRanges.length() + " price ranges");
            for (int i = 0; i < priceRanges.length(); i++) {
                JSONObject priceRange = priceRanges.getJSONObject(i);
                double min = priceRange.optDouble("min", 0);
                double max = priceRange.optDouble("max", 0);
                String currency = priceRange.optString("currency", "USD");
                if (min > 0 && max > 0) {
                    priceStrings.add(String.format(Locale.getDefault(), "%s %.2f - %.2f", currency, min, max));
                } else if (min > 0) {
                    priceStrings.add(String.format(Locale.getDefault(), "%s %.2f+", currency, min));
                }
            }
        } else {
            Log.w(TAG, "No priceRanges array found");
        }
        TextView textPriceRanges = contentView.findViewById(R.id.textPriceRanges);
        View layoutPriceRanges = contentView.findViewById(R.id.layoutPriceRanges);
        if (textPriceRanges != null && layoutPriceRanges != null) {
            if (priceStrings.isEmpty()) {
                layoutPriceRanges.setVisibility(View.GONE);
                Log.d(TAG, "Hiding price ranges field - no price ranges value");
            } else {
                textPriceRanges.setText(String.join(", ", priceStrings));
                layoutPriceRanges.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set price ranges: " + String.join(", ", priceStrings));
            }
        }

        // Parse ticket status - check dates.status.code first (most reliable)
        // Reuse the dates object already parsed above
        String ticketStatus = "On Sale";
        if (dates != null) {
            JSONObject status = dates.optJSONObject("status");
            if (status != null) {
                String statusCode = status.optString("code", "");
                Log.d(TAG, "Ticket status code from dates.status.code: " + statusCode);
                if (!statusCode.isEmpty()) {
                    switch (statusCode.toLowerCase()) {
                        case "onsale":
                            ticketStatus = "On Sale";
                            break;
                        case "offsale":
                            ticketStatus = "Off Sale";
                            break;
                        case "cancelled":
                        case "canceled":
                            ticketStatus = "Canceled";
                            break;
                        case "postponed":
                            ticketStatus = "Postponed";
                            break;
                        case "rescheduled":
                            ticketStatus = "Rescheduled";
                            break;
                        default:
                            ticketStatus = "On Sale"; // Default fallback
                            break;
                    }
                }
            }
        }
        
        // Fallback: Check sales object if dates.status not available
        if (ticketStatus.equals("On Sale")) {
            JSONObject sales = response.optJSONObject("sales");
            if (sales != null) {
                // Check if event is canceled
                if (sales.has("presales") && sales.optJSONArray("presales") != null) {
                    // Check for canceled status
                    String status = sales.optString("status", "");
                    if (status.equalsIgnoreCase("cancelled") || status.equalsIgnoreCase("canceled")) {
                        ticketStatus = "Canceled";
                    }
                }
                
                JSONObject publicSales = sales.optJSONObject("public");
                if (publicSales != null) {
                    String startDateTime = publicSales.optString("startDateTime", "");
                    String endDateTime = publicSales.optString("endDateTime", "");
                    
                    // Try to determine status based on dates
                    if (!startDateTime.isEmpty() && !endDateTime.isEmpty()) {
                        try {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                            Date startDate = dateFormat.parse(startDateTime);
                            Date endDate = dateFormat.parse(endDateTime);
                            Date now = new Date();
                            
                            if (now.before(startDate)) {
                                ticketStatus = "Off Sale";
                            } else if (now.after(endDate)) {
                                ticketStatus = "Off Sale";
                            } else {
                                ticketStatus = "On Sale";
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error parsing sales dates", e);
                        }
                    }
                }
            }
        }
        TextView textTicketStatus = contentView.findViewById(R.id.textTicketStatus);
        View layoutTicketStatus = contentView.findViewById(R.id.layoutTicketStatus);
        if (textTicketStatus != null && layoutTicketStatus != null) {
            if (ticketStatus == null || ticketStatus.isEmpty()) {
                layoutTicketStatus.setVisibility(View.GONE);
                Log.d(TAG, "Hiding ticket status field - no ticket status value");
            } else {
                textTicketStatus.setText(ticketStatus);
                int bg;
                int textColor;
                
                if ("Off Sale".equals(ticketStatus)) {
                    // Off Sale: use darker version of details card background (colorSurfaceVariant)
                    int baseColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurfaceVariant, 0);
                    // Darken the color by blending with black (10% black blend for darker shade)
                    bg = Color.rgb(
                        (int) (Color.red(baseColor) * 0.9f),
                        (int) (Color.green(baseColor) * 0.9f),
                        (int) (Color.blue(baseColor) * 0.9f)
                    );
                    // Use muted text color for Off Sale
                    textColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0);
                } else if ("Rescheduled".equals(ticketStatus)) {
                    // Rescheduled: green background
                    bg = Color.rgb(76, 175, 80); // Material Green 500
                    textColor = Color.WHITE;
                } else if ("Canceled".equals(ticketStatus) || "Cancelled".equals(ticketStatus)) {
                    // Cancelled: brown background
                    bg = Color.rgb(121, 85, 72); // Material Brown 500
                    textColor = Color.WHITE;
                } else if ("Postponed".equals(ticketStatus)) {
                    // Postponed: orange background
                    bg = Color.rgb(255, 152, 0); // Material Orange 500
                    textColor = Color.WHITE;
                } else {
                    // Other statuses (On Sale, etc.): use lighter shade of search input form background color (colorSecondaryContainer)
                    int baseColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSecondaryContainer, 0);
                    // Lighten the color by blending with white (30% white blend for lighter shade)
                    bg = Color.rgb(
                        (int) (Color.red(baseColor) * 0.7f + 255 * 0.3f),
                        (int) (Color.green(baseColor) * 0.7f + 255 * 0.3f),
                        (int) (Color.blue(baseColor) * 0.7f + 255 * 0.3f)
                    );
                    textColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface, 0);
                }
                
                int strokeColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOutline, 0);
                // Create rounded background drawable with border
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setColor(bg);
                float strokeWidth = 2f * getResources().getDisplayMetrics().density; // 2dp stroke
                drawable.setStroke((int) strokeWidth, strokeColor);
                drawable.setCornerRadius(12f * getResources().getDisplayMetrics().density); // 12dp radius
                textTicketStatus.setBackground(drawable);
                textTicketStatus.setTextColor(textColor);
                layoutTicketStatus.setVisibility(View.VISIBLE);
                Log.d(TAG, "Set ticket status: " + ticketStatus);
            }
        }

        // Parse Ticketmaster URL
        String ticketmasterUrl = response.optString("url", "");
        Log.d(TAG, "Ticketmaster URL: " + ticketmasterUrl);
        
        // External link button
        ImageView buttonExternalLink = contentView.findViewById(R.id.buttonExternalLink);
        if (buttonExternalLink != null) {
            if (!ticketmasterUrl.isEmpty()) {
                // Show button and set click listener
                buttonExternalLink.setVisibility(View.VISIBLE);
                buttonExternalLink.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ticketmasterUrl));
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening Ticketmaster link", e);
                    }
                });
            } else {
                // Hide button when no URL
                buttonExternalLink.setVisibility(View.GONE);
                Log.d(TAG, "Hiding external link button - no Ticketmaster URL");
            }
        }

        // Share button
        ImageView buttonShare = contentView.findViewById(R.id.buttonShare);
        if (buttonShare != null) {
            if (!ticketmasterUrl.isEmpty()) {
                // Show button and set click listener
                buttonShare.setVisibility(View.VISIBLE);
                buttonShare.setOnClickListener(v -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, ticketmasterUrl);
                    startActivity(Intent.createChooser(shareIntent, "Share Event"));
                });
            } else {
                // Hide button when no URL
                buttonShare.setVisibility(View.GONE);
                Log.d(TAG, "Hiding share button - no Ticketmaster URL");
            }
        }

        // Parse and display seatmap - check multiple possible field names
        String seatmapUrl = "";
        JSONObject seatmap = response.optJSONObject("seatmap");
        if (seatmap != null) {
            // Try different possible field names
            seatmapUrl = seatmap.optString("staticUrl", "");
            if (seatmapUrl.isEmpty()) {
                seatmapUrl = seatmap.optString("url", "");
            }
            if (seatmapUrl.isEmpty()) {
                seatmapUrl = seatmap.optString("imageUrl", "");
            }
            Log.d(TAG, "Seatmap URL: " + seatmapUrl);
        } else {
            Log.w(TAG, "No seatmap object found in response");
        }
        
        // Get the seatmap card and its child views
        View cardSeatmap = contentView.findViewById(R.id.cardSeatmap);
        ImageView imageSeatmap = contentView.findViewById(R.id.imageSeatmap);
        TextView labelSeatmap = contentView.findViewById(R.id.labelSeatmap);
        
        if (cardSeatmap != null) {
            if (!seatmapUrl.isEmpty()) {
                Log.d(TAG, "Loading seatmap image from: " + seatmapUrl);
                // Show the entire card
                cardSeatmap.setVisibility(View.VISIBLE);
                if (imageSeatmap != null) {
                    imageSeatmap.setVisibility(View.VISIBLE);
                    loadSeatmapImage(imageSeatmap, seatmapUrl);
                }
                if (labelSeatmap != null) {
                    labelSeatmap.setVisibility(View.VISIBLE);
                }
            } else {
                Log.w(TAG, "Seatmap URL is empty, hiding seatmap card");
                // Hide the entire card when there's no seatmap
                cardSeatmap.setVisibility(View.GONE);
            }
        }
    }

    private void displayArtistTab(JSONObject response) throws JSONException {
        hideLoading();
        
        // Hide "No artist data" message
        TextView textContent = contentView.findViewById(R.id.textContent);
        if (textContent != null) {
            textContent.setVisibility(View.GONE);
        }
        
        // Show artist card
        View artistCard = contentView.findViewById(R.id.artistCard);
        if (artistCard != null) {
            artistCard.setVisibility(View.VISIBLE);
        }
        
        // Artist Name
        String artistName = response.optString("name", "");
        TextView textArtistName = contentView.findViewById(R.id.textArtistName);
        if (textArtistName != null) {
            textArtistName.setText(artistName);
            textArtistName.setSelected(true); // Enable marquee
        }

        // Spotify URL
        String spotifyUrl = response.optString("spotifyLink", "");
        ImageView buttonSpotifyLink = contentView.findViewById(R.id.buttonSpotifyLink);
        if (buttonSpotifyLink != null) {
            if (!spotifyUrl.isEmpty()) {
                buttonSpotifyLink.setVisibility(View.VISIBLE);
                buttonSpotifyLink.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUrl));
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening Spotify link", e);
                    }
                });
            } else {
                buttonSpotifyLink.setVisibility(View.GONE);
            }
        }

        // Followers - backend returns as formatted string, but we need to parse it
        String followersStr = response.optString("followers", "0");
        // Remove commas and parse
        int followers = 0;
        try {
            followers = Integer.parseInt(followersStr.replace(",", "").replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            Log.w(TAG, "Could not parse followers: " + followersStr);
        }
        TextView textFollowers = contentView.findViewById(R.id.textFollowers);
        if (textFollowers != null) {
            textFollowers.setText(formatFollowersWithCommas(followers));
        }

        // Popularity
        int popularity = response.optInt("popularity", 0);
        TextView textPopularity = contentView.findViewById(R.id.textPopularity);
        if (textPopularity != null) {
            textPopularity.setText(popularity + "%");
        }

        // Artist Genre - backend returns array, take first one and display as tag
        JSONArray genresArray = response.optJSONArray("genres");
        String genre = "";
        if (genresArray != null && genresArray.length() > 0) {
            genre = genresArray.optString(0, "");
        }
        TextView textGenre = contentView.findViewById(R.id.textGenre);
        View genreParent = textGenre != null ? (View) textGenre.getParent() : null;
        if (textGenre != null) {
            if (!genre.isEmpty()) {
                textGenre.setText(genre);
                // Apply same styling as ticket status pill: lighter shade of colorSecondaryContainer with border
                int baseColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSecondaryContainer, 0);
                // Lighten the color by blending with white (30% white blend for lighter shade)
                int bg = Color.rgb(
                    (int) (Color.red(baseColor) * 0.7f + 255 * 0.3f),
                    (int) (Color.green(baseColor) * 0.7f + 255 * 0.3f),
                    (int) (Color.blue(baseColor) * 0.7f + 255 * 0.3f)
                );
                int strokeColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOutline, 0);
                int textColor = MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorOnSurface, 0);
                // Create rounded background drawable with border
                GradientDrawable drawable = new GradientDrawable();
                drawable.setShape(GradientDrawable.RECTANGLE);
                drawable.setColor(bg);
                float strokeWidth = 2f * getResources().getDisplayMetrics().density; // 2dp stroke
                drawable.setStroke((int) strokeWidth, strokeColor);
                drawable.setCornerRadius(12f * getResources().getDisplayMetrics().density); // 12dp radius
                textGenre.setBackground(drawable);
                textGenre.setTextColor(textColor);
                textGenre.setVisibility(View.VISIBLE);
                if (genreParent != null) {
                    genreParent.setVisibility(View.VISIBLE);
                }
            } else {
                textGenre.setVisibility(View.GONE);
                if (genreParent != null) {
                    genreParent.setVisibility(View.GONE);
                }
            }
        }

        // Artist Image
        String imageUrl = response.optString("image", "");
        ImageView imageArtist = contentView.findViewById(R.id.imageArtist);
        if (imageArtist != null && !imageUrl.isEmpty()) {
            loadArtistImage(imageArtist, imageUrl);
        }
    }

    private void displayAlbums(JSONArray albums) throws JSONException {
        LinearLayout layoutAlbums = contentView.findViewById(R.id.layoutAlbums);
        View albumsSection = contentView.findViewById(R.id.albumsSection);
        
        if (layoutAlbums != null) {
            layoutAlbums.removeAllViews();
            
            if (albums != null && albums.length() > 0) {
                // Show albums section
                if (albumsSection != null) {
                    albumsSection.setVisibility(View.VISIBLE);
                }
                
                // Sort albums by release date in reverse chronological order (newest first)
                List<JSONObject> albumList = new ArrayList<>();
                for (int i = 0; i < albums.length(); i++) {
                    albumList.add(albums.getJSONObject(i));
                }
                
                albumList.sort((a, b) -> {
                    String dateA = a.optString("releaseDate", "");
                    String dateB = b.optString("releaseDate", "");
                    
                    // Handle empty dates - put them at the end
                    if (dateA.isEmpty() && dateB.isEmpty()) {
                        return 0;
                    }
                    if (dateA.isEmpty()) {
                        return 1; // Put empty date after
                    }
                    if (dateB.isEmpty()) {
                        return -1; // Put empty date after
                    }
                    
                    // Compare dates (format should be yyyy-MM-dd from backend)
                    // Reverse order: newest first (dateB.compareTo(dateA))
                    return dateB.compareTo(dateA);
                });

                // Create rows of 2 albums each
                LinearLayout currentRow = null;
                for (int i = 0; i < albumList.size(); i++) {
                    JSONObject album = albumList.get(i);
                    
                    // Create a new row for every 2 albums
                    if (i % 2 == 0) {
                        currentRow = new LinearLayout(requireContext());
                        currentRow.setOrientation(LinearLayout.HORIZONTAL);
                        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        layoutAlbums.addView(currentRow, rowParams);
                    }
                    
                    // Add album to current row
                    if (currentRow != null) {
                        View albumView = createAlbumView(album);
                        
                        // Get existing layout params or create new ones
                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) albumView.getLayoutParams();
                        if (params == null) {
                            params = new LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1.0f
                            );
                        } else {
                            // Update existing params
                            params.width = 0;
                            params.height = LinearLayout.LayoutParams.WRAP_CONTENT;
                            params.weight = 1.0f;
                        }
                        
                        // Set margins: horizontal spacing between items, preserve bottom margin
                        int bottomMargin = params.bottomMargin > 0 ? params.bottomMargin : 30; // Preserve or use default
                        if (i % 2 == 0) {
                            // First item in row - add right margin for spacing
                            params.setMargins(0, 0, 32, bottomMargin);
                        } else {
                            // Second item in row - no additional margin needed
                            params.setMargins(0, 0, 0, bottomMargin);
                        }
                        
                        albumView.setLayoutParams(params);
                        currentRow.addView(albumView);
                    }
                }
            } else {
                // Hide albums section if no albums
                if (albumsSection != null) {
                    albumsSection.setVisibility(View.GONE);
                }
            }
        }
    }

    private String formatFollowers(int followers) {
        if (followers >= 1000000) {
            return String.format(Locale.getDefault(), "%.1fM", followers / 1000000.0);
        } else if (followers >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK", followers / 1000.0);
        } else {
            return String.valueOf(followers);
        }
    }

    private String formatFollowersWithCommas(int followers) {
        return String.format(Locale.getDefault(), "%,d", followers);
    }

    private View createAlbumView(JSONObject album) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View albumView = inflater.inflate(R.layout.album_item, null);
        
        String albumName = album.optString("name", "");
        String releaseDate = album.optString("releaseDate", "");
        int trackCount = album.optInt("totalTracks", 0);
        String imageUrl = album.optString("image", "");
        String spotifyUrl = album.optString("spotifyLink", "");

        TextView textAlbumName = albumView.findViewById(R.id.textAlbumName);
        TextView textAlbumDate = albumView.findViewById(R.id.textAlbumDate);
        TextView textAlbumTracks = albumView.findViewById(R.id.textAlbumTracks);
        ImageView imageAlbum = albumView.findViewById(R.id.imageAlbum);

        if (textAlbumName != null) {
            textAlbumName.setText(albumName);
        }
        if (textAlbumDate != null && !releaseDate.isEmpty()) {
            textAlbumDate.setText(formatAlbumDate(releaseDate));
        } else if (textAlbumDate != null) {
            textAlbumDate.setText("");
        }
        if (textAlbumTracks != null && trackCount > 0) {
            textAlbumTracks.setText(trackCount + " tracks");
        } else if (textAlbumTracks != null) {
            textAlbumTracks.setText("");
        }
        if (imageAlbum != null && !imageUrl.isEmpty()) {
            loadAlbumImage(imageAlbum, imageUrl);
        }

        // Open Spotify on click
        if (!spotifyUrl.isEmpty()) {
            albumView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUrl));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error opening album Spotify link", e);
                }
            });
        }

        return albumView;
    }

    private String formatAlbumDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "";
        }
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = inputFormat.parse(dateStr);
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            return dateStr;
        }
    }

    private void loadArtistImage(ImageView imageView, String url) {
        ImageRequest imageRequest = new ImageRequest(
            url,
            response -> {
                if (response != null) {
                    imageView.setImageBitmap(response);
                    imageView.setVisibility(View.VISIBLE);
                }
            },
            0, 0,
            ImageView.ScaleType.CENTER_CROP,
            null,
            error -> {
                Log.e(TAG, "Error loading artist image", error);
            }
        );
        requestQueue.add(imageRequest);
    }

    private void loadAlbumImage(ImageView imageView, String url) {
        ImageRequest imageRequest = new ImageRequest(
            url,
            response -> {
                if (response != null) {
                    imageView.setImageBitmap(response);
                }
            },
            0, 0,
            ImageView.ScaleType.CENTER_CROP,
            null,
            error -> {
                Log.e(TAG, "Error loading album image", error);
            }
        );
        requestQueue.add(imageRequest);
    }

    private void displayVenueTab(JSONObject response) throws JSONException {
        Log.d(TAG, "=== VENUE TAB - Full Response ===");
        Log.d(TAG, "Response JSON: " + response.toString());
        hideLoading();
        
        // Venue Logo/Image - check images array for logo
        String logoUrl = "";
        JSONArray images = response.optJSONArray("images");
        if (images != null && images.length() > 0) {
            // Look for a square or portrait image (logo)
            for (int i = 0; i < images.length(); i++) {
                JSONObject img = images.getJSONObject(i);
                int width = img.optInt("width", 0);
                int height = img.optInt("height", 0);
                // Prefer square or portrait images for logo
                if (height >= width && width > 0) {
                    logoUrl = img.optString("url", "");
                    break;
                }
            }
            // If no portrait/square image, use first image
            if (logoUrl.isEmpty()) {
                logoUrl = images.getJSONObject(0).optString("url", "");
            }
        }
        
        ImageView imageVenueLogo = contentView.findViewById(R.id.imageVenueLogo);
        if (imageVenueLogo != null) {
            if (!logoUrl.isEmpty()) {
                Log.d(TAG, "Loading venue logo from: " + logoUrl);
                imageVenueLogo.setVisibility(View.VISIBLE);
                loadVenueImage(imageVenueLogo, logoUrl);
            } else {
                Log.w(TAG, "No logo URL found, hiding logo");
                imageVenueLogo.setVisibility(View.GONE);
            }
        } else {
            Log.e(TAG, "imageVenueLogo is null");
        }
        
        // Venue Name
        String venueName = response.optString("name", "");
        Log.d(TAG, "Venue name: " + venueName);
        TextView textVenueName = contentView.findViewById(R.id.textVenueName);
        if (textVenueName != null) {
            textVenueName.setText(venueName);
            textVenueName.setVisibility(View.VISIBLE);
            textVenueName.setSelected(true); // Enable marquee
        } else {
            Log.e(TAG, "textVenueName is null");
        }

        // Address - Ticketmaster API structure
        Log.d(TAG, "=== EXTRACTING ADDRESS DATA ===");
        JSONObject address = response.optJSONObject("address");
        String addressLine1 = "";
        String city = "";
        String state = "";
        String country = "";
        
        if (address != null) {
            Log.d(TAG, "Address object found: " + address.toString());
            addressLine1 = address.optString("line1", "");
            Log.d(TAG, "Address line1 from address object: '" + addressLine1 + "'");
            
            // Check if city is a string or nested object
            Object cityObj = address.opt("city");
            Log.d(TAG, "City object type: " + (cityObj != null ? cityObj.getClass().getSimpleName() : "null"));
            if (cityObj instanceof String) {
                city = (String) cityObj;
                Log.d(TAG, "City is String: '" + city + "'");
            } else if (cityObj instanceof JSONObject) {
                Log.d(TAG, "City is JSONObject: " + ((JSONObject) cityObj).toString());
                city = ((JSONObject) cityObj).optString("name", "");
                Log.d(TAG, "Extracted city name: '" + city + "'");
            } else {
                Log.w(TAG, "City is neither String nor JSONObject, type: " + (cityObj != null ? cityObj.getClass().getName() : "null"));
            }
            
            // Try multiple state field names
            state = address.optString("stateCode", "");
            if (state.isEmpty()) {
                state = address.optString("state", "");
                Log.d(TAG, "Tried 'state' field: '" + state + "'");
            }
            if (state.isEmpty()) {
                Object stateObj = address.opt("state");
                if (stateObj instanceof JSONObject) {
                    state = ((JSONObject) stateObj).optString("code", "");
                    if (state.isEmpty()) {
                        state = ((JSONObject) stateObj).optString("name", "");
                    }
                    Log.d(TAG, "Extracted state from JSONObject: '" + state + "'");
                }
            }
            Log.d(TAG, "State from address object: '" + state + "'");
            
            // Try multiple country field names
            country = address.optString("countryCode", "");
            if (country.isEmpty()) {
                country = address.optString("country", "");
                Log.d(TAG, "Tried 'country' field: '" + country + "'");
            }
            if (country.isEmpty()) {
                Object countryObj = address.opt("country");
                if (countryObj instanceof JSONObject) {
                    country = ((JSONObject) countryObj).optString("code", "");
                    if (country.isEmpty()) {
                        country = ((JSONObject) countryObj).optString("name", "");
                    }
                    Log.d(TAG, "Extracted country from JSONObject: '" + country + "'");
                }
            }
            Log.d(TAG, "Country from address object: '" + country + "'");
            
            Log.d(TAG, "Address object summary - line1: '" + addressLine1 + "', city: '" + city + "', state: '" + state + "', country: '" + country + "'");
        } else {
            Log.w(TAG, "Address object is null!");
        }
        
        // Also check location object for missing fields
        JSONObject location = response.optJSONObject("location");
        if (location != null) {
            Log.d(TAG, "Location object found: " + location.toString());
            if (addressLine1.isEmpty()) {
                addressLine1 = location.optString("address", "");
                Log.d(TAG, "Got addressLine1 from location: '" + addressLine1 + "'");
            }
            if (city.isEmpty()) {
                // Check if city is a string or nested object
                Object cityObj = location.opt("city");
                Log.d(TAG, "Location city object type: " + (cityObj != null ? cityObj.getClass().getSimpleName() : "null"));
                if (cityObj instanceof String) {
                    city = (String) cityObj;
                    Log.d(TAG, "Got city from location (String): '" + city + "'");
                } else if (cityObj instanceof JSONObject) {
                    Log.d(TAG, "Location city JSONObject: " + ((JSONObject) cityObj).toString());
                    city = ((JSONObject) cityObj).optString("name", "");
                    Log.d(TAG, "Got city from location (JSONObject): '" + city + "'");
                }
            }
            // Try to get state code from location
            if (state.isEmpty()) {
                state = location.optString("stateCode", "");
                Log.d(TAG, "Location stateCode: '" + state + "'");
                if (state.isEmpty()) {
                    state = location.optString("state", "");
                    Log.d(TAG, "Location state: '" + state + "'");
                }
                if (state.isEmpty()) {
                    // Check if state is a string or nested object
                    Object stateObj = location.opt("state");
                    Log.d(TAG, "Location state object type: " + (stateObj != null ? stateObj.getClass().getSimpleName() : "null"));
                    if (stateObj instanceof String) {
                        state = (String) stateObj;
                        Log.d(TAG, "Got state from location (String): '" + state + "'");
                    } else if (stateObj instanceof JSONObject) {
                        Log.d(TAG, "Location state JSONObject: " + ((JSONObject) stateObj).toString());
                        state = ((JSONObject) stateObj).optString("code", "");
                        if (state.isEmpty()) {
                            state = ((JSONObject) stateObj).optString("name", "");
                        }
                        Log.d(TAG, "Got state from location (JSONObject): '" + state + "'");
                    }
                }
            }
            // Try to get country code from location
            if (country.isEmpty()) {
                country = location.optString("countryCode", "");
                Log.d(TAG, "Location countryCode: '" + country + "'");
                if (country.isEmpty()) {
                    country = location.optString("country", "");
                    Log.d(TAG, "Location country: '" + country + "'");
                }
                if (country.isEmpty()) {
                    // Check if country is a string or nested object
                    Object countryObj = location.opt("country");
                    Log.d(TAG, "Location country object type: " + (countryObj != null ? countryObj.getClass().getSimpleName() : "null"));
                    if (countryObj instanceof String) {
                        country = (String) countryObj;
                        Log.d(TAG, "Got country from location (String): '" + country + "'");
                    } else if (countryObj instanceof JSONObject) {
                        Log.d(TAG, "Location country JSONObject: " + ((JSONObject) countryObj).toString());
                        country = ((JSONObject) countryObj).optString("code", "");
                        if (country.isEmpty()) {
                            country = ((JSONObject) countryObj).optString("name", "");
                        }
                        Log.d(TAG, "Got country from location (JSONObject): '" + country + "'");
                    }
                }
            }
        } else {
            Log.w(TAG, "Location object is null!");
        }
        
        // Also check top-level fields as fallback
        Log.d(TAG, "=== CHECKING TOP-LEVEL FIELDS ===");
        if (city.isEmpty()) {
            Object cityObj = response.opt("city");
            Log.d(TAG, "Top-level city object type: " + (cityObj != null ? cityObj.getClass().getSimpleName() : "null"));
            if (cityObj instanceof String) {
                city = (String) cityObj;
                Log.d(TAG, "Got city from top-level (String): '" + city + "'");
            } else if (cityObj instanceof JSONObject) {
                city = ((JSONObject) cityObj).optString("name", "");
                Log.d(TAG, "Got city from top-level (JSONObject): '" + city + "'");
            }
        }
        if (state.isEmpty()) {
            // First check if stateCode exists as a string
            state = response.optString("stateCode", "");
            Log.d(TAG, "Top-level stateCode: '" + state + "'");
            
            // If stateCode is empty, check if state is a JSONObject (must check BEFORE optString)
            if (state.isEmpty()) {
                Object stateObj = response.opt("state");
                Log.d(TAG, "Top-level state object type: " + (stateObj != null ? stateObj.getClass().getSimpleName() : "null"));
                if (stateObj instanceof JSONObject) {
                    Log.d(TAG, "Top-level state JSONObject: " + ((JSONObject) stateObj).toString());
                    // Extract stateCode from the JSONObject
                    state = ((JSONObject) stateObj).optString("stateCode", "");
                    if (state.isEmpty()) {
                        state = ((JSONObject) stateObj).optString("code", "");
                    }
                    if (state.isEmpty()) {
                        state = ((JSONObject) stateObj).optString("name", "");
                    }
                    Log.d(TAG, "Got state from top-level (JSONObject): '" + state + "'");
                } else if (stateObj instanceof String) {
                    state = (String) stateObj;
                    Log.d(TAG, "Got state from top-level (String): '" + state + "'");
                }
            }
        }
        if (country.isEmpty()) {
            // First check if countryCode exists as a string
            country = response.optString("countryCode", "");
            Log.d(TAG, "Top-level countryCode: '" + country + "'");
            
            // If countryCode is empty, check if country is a JSONObject (must check BEFORE optString)
            if (country.isEmpty()) {
                Object countryObj = response.opt("country");
                Log.d(TAG, "Top-level country object type: " + (countryObj != null ? countryObj.getClass().getSimpleName() : "null"));
                if (countryObj instanceof JSONObject) {
                    Log.d(TAG, "Top-level country JSONObject: " + ((JSONObject) countryObj).toString());
                    // Extract countryCode from the JSONObject
                    country = ((JSONObject) countryObj).optString("countryCode", "");
                    if (country.isEmpty()) {
                        country = ((JSONObject) countryObj).optString("code", "");
                    }
                    if (country.isEmpty()) {
                        country = ((JSONObject) countryObj).optString("name", "");
                    }
                    Log.d(TAG, "Got country from top-level (JSONObject): '" + country + "'");
                } else if (countryObj instanceof String) {
                    country = (String) countryObj;
                    Log.d(TAG, "Got country from top-level (String): '" + country + "'");
                }
            }
        }
        
        Log.d(TAG, "=== FINAL EXTRACTED VALUES ===");
        Log.d(TAG, "addressLine1: '" + addressLine1 + "'");
        Log.d(TAG, "city: '" + city + "'");
        Log.d(TAG, "state: '" + state + "'");
        Log.d(TAG, "country: '" + country + "'");
        
        // Build address string - format: street, city, state code, country code (no postal code)
        // Example: "1001 S. Stadium Dr, Inglewood, CA, US"
        StringBuilder addressBuilder = new StringBuilder();
        
        // Street address
        if (!addressLine1.isEmpty()) {
            addressBuilder.append(addressLine1);
        }
        
        // City
        if (!city.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(city);
        }
        
        // State code (always include if available)
        if (!state.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            // State should be a code, ensure it's uppercase and max 2 characters
            String stateCode = state.length() > 2 ? state.substring(0, 2).toUpperCase() : state.toUpperCase();
            addressBuilder.append(stateCode);
            Log.d(TAG, "Added state to address: " + stateCode);
        } else {
            Log.w(TAG, "State is empty, not adding to address");
        }
        
        // Country code (always include if available)
        if (!country.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            // Country should be a code, ensure it's uppercase and max 2 characters
            String countryCode = country.length() > 2 ? country.substring(0, 2).toUpperCase() : country.toUpperCase();
            addressBuilder.append(countryCode);
            Log.d(TAG, "Added country to address: " + countryCode);
        } else {
            Log.w(TAG, "Country is empty, not adding to address");
        }
        
        Log.d(TAG, "Final address string: " + addressBuilder.toString());
        Log.d(TAG, "Address components - line1: '" + addressLine1 + "', city: '" + city + "', state: '" + state + "', country: '" + country + "'");

        TextView textAddress = contentView.findViewById(R.id.textAddress);
        String addressText = addressBuilder.toString();
        Log.d(TAG, "Address: " + addressText);
        if (textAddress != null) {
            textAddress.setText(addressText);
            textAddress.setVisibility(View.VISIBLE);
            textAddress.setSelected(true); // Enable marquee
        } else {
            Log.e(TAG, "textAddress is null");
        }

        // Ticketmaster URL - use venue's url field
        String ticketmasterUrl = response.optString("url", "");
        ImageView buttonVenueLink = contentView.findViewById(R.id.buttonVenueLink);
        
        if (!ticketmasterUrl.isEmpty()) {
            // Set up external link button next to address
            if (buttonVenueLink != null) {
                buttonVenueLink.setVisibility(View.VISIBLE);
                buttonVenueLink.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(ticketmasterUrl));
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error opening venue Ticketmaster link", e);
                    }
                });
            }
        } else {
            if (buttonVenueLink != null) {
                buttonVenueLink.setVisibility(View.GONE);
            }
        }

    }

    private void loadVenueImage(ImageView imageView, String url) {
        ImageRequest imageRequest = new ImageRequest(
            url,
            response -> {
                if (response != null) {
                    imageView.setImageBitmap(response);
                    imageView.setVisibility(View.VISIBLE);
                }
            },
            0, 0,
            ImageView.ScaleType.CENTER_CROP,
            null,
            error -> {
                Log.e(TAG, "Error loading venue image", error);
            }
        );
        requestQueue.add(imageRequest);
    }

    private void addGenreIfNotEmpty(List<String> genres, java.util.Set<String> addedGenres, String genre) {
        if (genre != null && !genre.isEmpty() && !genre.equals("Undefined") && !addedGenres.contains(genre)) {
            genres.add(genre);
            addedGenres.add(genre);
        }
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "";
        }
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = inputFormat.parse(dateStr);
            Date currentDate = new Date();
            SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
            
            // Check if year is current year
            boolean isCurrentYear = yearFormat.format(date).equals(yearFormat.format(currentDate));
            
            SimpleDateFormat outputFormat;
            if (isCurrentYear) {
                // Omit year if current year: "MMM d"
                outputFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
            } else {
                // Include year if not current: "MMM d, yyyy"
                outputFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            }
            return outputFormat.format(date);
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatTime(String time24) {
        if (time24 == null || time24.isEmpty()) {
            return "";
        }
        try {
            String[] parts = time24.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            String period = "AM";
            if (hour >= 12) {
                period = "PM";
                if (hour > 12) {
                    hour -= 12;
                }
            }
            if (hour == 0) {
                hour = 12;
            }
            return String.format(Locale.getDefault(), "%d:%02d %s", hour, minute, period);
        } catch (Exception e) {
            return time24;
        }
    }

    private void loadSeatmapImage(ImageView imageView, String url) {
        ImageRequest imageRequest = new ImageRequest(
            url,
            response -> {
                if (response != null) {
                    imageView.setImageBitmap(response);
                    imageView.setVisibility(View.VISIBLE);
                }
            },
            0, 0,
            ImageView.ScaleType.FIT_CENTER,
            null,
            error -> {
                Log.e(TAG, "Error loading seatmap image", error);
                imageView.setVisibility(View.GONE);
            }
        );
        requestQueue.add(imageRequest);
    }

    private void hideLoading() {
        Log.d(TAG, "hideLoading called");
        if (progressLoading != null) {
            progressLoading.setVisibility(View.GONE);
            Log.d(TAG, "Progress bar hidden");
        } else {
            Log.w(TAG, "progressLoading is null");
        }
        if (contentView != null) {
            contentView.setVisibility(View.VISIBLE);
            Log.d(TAG, "Content view shown");
        } else {
            Log.e(TAG, "contentView is null");
        }
    }
}

