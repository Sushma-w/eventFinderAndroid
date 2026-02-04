package com.example.eventfinderjava;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String BASE_URL = "https://backend-dot-steam-house-473501-t8.wl.r.appspot.com";
    
    private final ArrayList<FavoriteEvent> favorites = new ArrayList<>();
    private FavoritesAdapter adapter;
    private RecyclerView favoritesRecycler;
    private View emptyStateContainer;
    private TextView poweredByView;
    private RequestQueue requestQueue;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.home_fragment, container, false);

        TextView dateView = view.findViewById(R.id.textDate);
        poweredByView = view.findViewById(R.id.textPoweredBy);
        emptyStateContainer = view.findViewById(R.id.layoutEmptyState);
        favoritesRecycler = view.findViewById(R.id.recyclerFavorites);
        LinearLayout headerRow = view.findViewById(R.id.layoutEventSearchHeader);
        ImageView searchIcon = view.findViewById(R.id.imageSearchIcon);

        dateView.setText(formatToday());

        poweredByView.setOnClickListener(v -> openTicketmaster());

        // Navigate to SearchFragment when search icon is clicked
        searchIcon.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                // Add SearchFragment to back stack so back button works
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragmentContainer, new SearchFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });

        // Position header below status bar
        if (headerRow != null) {
            ViewCompat.setOnApplyWindowInsetsListener(headerRow, (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                v.setPadding(0, statusBarHeight, 0, 0);
                return insets;
            });
        }

        favoritesRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        favoritesRecycler.setHasFixedSize(true);
        
        // Initialize Volley request queue
        requestQueue = Volley.newRequestQueue(requireContext());
        
        adapter = new FavoritesAdapter(favorites);
        adapter.setImageRequestQueue(requestQueue);
        adapter.setOnItemClickListener(event -> {
            Log.d(TAG, "Favorite clicked: event=" + (event != null ? event.name : "null") + ", id=" + (event != null ? event.id : "null"));
            
            // Validate event data before navigation
            if (event == null) {
                Log.e(TAG, "Cannot navigate: event is null");
                Toast.makeText(requireContext(), "Event information is missing", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (event.id == null || event.id.isEmpty()) {
                Log.e(TAG, "Cannot navigate: event ID is null or empty");
                Toast.makeText(requireContext(), "Event ID is missing", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                // Navigate to event details
                String eventName = event.name != null && !event.name.isEmpty() ? event.name : "Event Details";
                Log.d(TAG, "Navigating to event details: id=" + event.id + ", name=" + eventName);
                
                EventDetailsFragment detailsFragment = EventDetailsFragment.newInstance(event.id, eventName);
                
                if (getActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) getActivity();
                    activity.getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer, detailsFragment)
                            .addToBackStack(null)
                            .commit();
                    Log.d(TAG, "Navigation transaction committed");
                } else {
                    Log.e(TAG, "Activity is not MainActivity");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to event details", e);
                Toast.makeText(requireContext(), "Error opening event details", Toast.LENGTH_SHORT).show();
            }
        });
        favoritesRecycler.setAdapter(adapter);

        loadFavorites();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload favorites when fragment becomes visible
        loadFavorites();
    }

    private void loadFavorites() {
        String url = BASE_URL + "/api/favorites";
        Log.d(TAG, "Loading favorites from: " + url);

        JsonArrayRequest request = new JsonArrayRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    Log.d(TAG, "Favorites response received: " + response.length() + " items");
                    favorites.clear();
                    
                    try {
                        for (int i = 0; i < response.length(); i++) {
                            JSONObject favorite = response.getJSONObject(i);
                            
                            String id = favorite.optString("id", "");
                            String name = favorite.optString("name", "");
                            String dateRaw = favorite.optString("date", "");
                            String timeRaw = favorite.optString("time", "");
                            String image = favorite.optString("image", "");
                            
                            Log.d(TAG, "Parsing favorite: id=" + id + ", name=" + name + ", dateRaw=" + dateRaw + ", timeRaw=" + timeRaw);
                            
                            // Format date from YYYY-MM-DD to "MMM d, yyyy" (e.g., "Jan 2, 2026")
                            String dateFormatted = formatDate(dateRaw);
                            
                            // Format time from 24-hour to AM/PM (e.g., "7:00 PM")
                            String timeFormatted = formatTime(timeRaw);
                            
                            // Combine date and time for display (e.g., "Jan 2, 2026, 7:00 PM")
                            String dateTime = dateFormatted;
                            if (!timeFormatted.isEmpty()) {
                                dateTime += ", " + timeFormatted;
                            }
                            
                            Log.d(TAG, "Formatted dateTime: " + dateTime + " (dateFormatted=" + dateFormatted + ", timeFormatted=" + timeFormatted + ")");
                            
                            // Get timestampAdded - could be in addedAt field
                            String timestampAdded = "";
                            if (favorite.has("addedAt")) {
                                Object addedAtObj = favorite.get("addedAt");
                                if (addedAtObj instanceof String) {
                                    timestampAdded = (String) addedAtObj;
                                } else if (addedAtObj instanceof Long) {
                                    timestampAdded = String.valueOf(addedAtObj);
                                }
                            } else if (favorite.has("timestampAdded")) {
                                timestampAdded = favorite.optString("timestampAdded", "");
                            }
                            
                            FavoriteEvent event = new FavoriteEvent(id, name, dateTime, timeFormatted, image, timestampAdded);
                            favorites.add(event);
                            
                            // If image URL is empty OR date/time are missing, fetch event details
                            if ((image.isEmpty() || dateRaw.isEmpty() || timeRaw.isEmpty()) && !id.isEmpty()) {
                                fetchEventDetailsForFavorite(event, dateRaw.isEmpty() || timeRaw.isEmpty());
                            }
                        }
                        
                        // Sort by timestamp (newest first - reverse chronological)
                        Collections.sort(favorites, new Comparator<FavoriteEvent>() {
                            @Override
                            public int compare(FavoriteEvent e1, FavoriteEvent e2) {
                                // Parse timestamps and compare
                                long time1 = parseTimestamp(e1.timestampAdded);
                                long time2 = parseTimestamp(e2.timestampAdded);
                                // Reverse order (newest first)
                                return Long.compare(time2, time1);
                            }
                        });
                        
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing favorites", e);
                        updateEmptyState();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error loading favorites", error);
                    favorites.clear();
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                }
            }
        );

        requestQueue.add(request);
    }
    
    private void fetchEventDetailsForFavorite(FavoriteEvent event, boolean needDate) {
        String url = BASE_URL + "/api/eventdetails?id=" + event.id;
        Log.d(TAG, "Fetching event details for: " + event.name + " (needDate: " + needDate + ")");
        
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    try {
                        boolean updated = false;
                        
                        // Get image URL from event details if needed
                        if (event.imageUrl == null || event.imageUrl.isEmpty()) {
                            String imageUrl = "";
                            JSONArray images = response.optJSONArray("images");
                            if (images != null && images.length() > 0) {
                                // Try to get the largest image
                                JSONObject bestImage = null;
                                int maxWidth = 0;
                                for (int j = 0; j < images.length(); j++) {
                                    JSONObject img = images.getJSONObject(j);
                                    int width = img.optInt("width", 0);
                                    if (width > maxWidth) {
                                        maxWidth = width;
                                        bestImage = img;
                                    }
                                }
                                if (bestImage != null) {
                                    imageUrl = bestImage.optString("url", "");
                                } else {
                                    imageUrl = images.getJSONObject(0).optString("url", "");
                                }
                            }
                            
                            if (!imageUrl.isEmpty()) {
                                event.imageUrl = imageUrl;
                                Log.d(TAG, "Updated image URL for: " + event.name + ", imageUrl: " + imageUrl);
                                updated = true;
                            }
                        }
                        
                        // Get date and time from event details if needed
                        if (needDate) {
                            JSONObject dates = response.optJSONObject("dates");
                            if (dates != null) {
                                JSONObject start = dates.optJSONObject("start");
                                if (start != null) {
                                    String dateRaw = start.optString("localDate", "");
                                    String timeRaw = start.optString("localTime", "");
                                    
                                    if (!dateRaw.isEmpty() || !timeRaw.isEmpty()) {
                                        // Format date from YYYY-MM-DD to "MMM d, yyyy"
                                        String dateFormatted = formatDate(dateRaw);
                                        
                                        // Format time from 24-hour to AM/PM
                                        String timeFormatted = formatTime(timeRaw);
                                        
                                        // Combine date and time for display
                                        String dateTime = dateFormatted;
                                        if (!timeFormatted.isEmpty()) {
                                            if (!dateTime.isEmpty()) {
                                                dateTime += ", " + timeFormatted;
                                            } else {
                                                dateTime = timeFormatted;
                                            }
                                        }
                                        
                                        event.date = dateTime;
                                        Log.d(TAG, "Updated date/time for: " + event.name + ", dateTime: " + dateTime);
                                        updated = true;
                                    }
                                }
                            }
                        }
                        
                        // Notify adapter to update the specific item if anything changed
                        if (updated) {
                            // Find the event in the list by ID and update it directly
                            FavoriteEvent listEvent = null;
                            int position = -1;
                            for (int i = 0; i < favorites.size(); i++) {
                                FavoriteEvent fav = favorites.get(i);
                                if (fav.id.equals(event.id)) {
                                    listEvent = fav;
                                    position = i;
                                    break;
                                }
                            }
                            
                            if (listEvent != null && position >= 0) {
                                // Update the event in the list with the new data
                                if (event.imageUrl != null && !event.imageUrl.isEmpty()) {
                                    listEvent.imageUrl = event.imageUrl;
                                }
                                if (event.date != null && !event.date.isEmpty()) {
                                    listEvent.date = event.date;
                                }
                                
                                Log.d(TAG, "Updated event in list at position: " + position + " for event: " + event.name);
                                Log.d(TAG, "  - Image URL: " + listEvent.imageUrl);
                                Log.d(TAG, "  - Date: " + listEvent.date);
                                
                                // Store position in final variable for lambda
                                final int finalPosition = position;
                                final String eventId = event.id;
                                
                                // Ensure we're on the main thread
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (adapter != null && finalPosition < favorites.size()) {
                                            // Double-check the position is still valid
                                            if (finalPosition < favorites.size() && favorites.get(finalPosition).id.equals(eventId)) {
                                                adapter.notifyItemChanged(finalPosition);
                                                Log.d(TAG, "Adapter notified for position: " + finalPosition);
                                            } else {
                                                Log.w(TAG, "Position changed, notifying entire dataset");
                                                adapter.notifyDataSetChanged();
                                            }
                                        }
                                    });
                                }
                            } else {
                                Log.w(TAG, "Could not find event in list: " + event.name + " (id: " + event.id + "), notifying entire dataset");
                                // Fallback: notify entire dataset if position not found
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (adapter != null) {
                                            adapter.notifyDataSetChanged();
                                        }
                                    });
                                }
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing event details for favorite", e);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching event details for favorite: " + event.name, error);
                }
            }
        );
        
        if (requestQueue != null) {
            requestQueue.add(request);
        }
    }

    private long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return 0;
        }
        try {
            // Try ISO 8601 format first
            if (timestamp.contains("T") || timestamp.contains("-")) {
                // Try with milliseconds first
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                try {
                    return sdf.parse(timestamp).getTime();
                } catch (Exception e) {
                    // Try without milliseconds
                    sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    return sdf.parse(timestamp).getTime();
                }
            } else {
                // Try as epoch milliseconds
                return Long.parseLong(timestamp);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse timestamp: " + timestamp);
            return 0;
        }
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "";
        }
        try {
            // Parse date from YYYY-MM-DD format
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = inputFormat.parse(dateStr);
            
            // Format to "MMM d, yyyy" (e.g., "Aug 8, 2026")
            SimpleDateFormat outputFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            Log.e(TAG, "Error formatting date: " + dateStr, e);
            return dateStr; // Return original if parsing fails
        }
    }

    private String formatTime(String time24) {
        if (time24 == null || time24.isEmpty()) {
            return "";
        }
        
        try {
            // Parse 24-hour format (HH:mm:ss) and convert to 12-hour format (h:mm AM/PM)
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
            Log.e(TAG, "Error formatting time: " + time24, e);
            return time24; // Return original if parsing fails
        }
    }

    private void updateEmptyState() {
        boolean hasFavorites = !favorites.isEmpty();
        favoritesRecycler.setVisibility(hasFavorites ? View.VISIBLE : View.GONE);
        emptyStateContainer.setVisibility(hasFavorites ? View.GONE : View.VISIBLE);
    }

    private String formatToday() {
        return new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());
    }

    private void openTicketmaster() {
        String url = getString(R.string.home_ticketmaster_url);
        if (TextUtils.isEmpty(url)) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(requireContext(), R.string.home_ticketmaster_error, Toast.LENGTH_SHORT).show();
        }
    }
}
