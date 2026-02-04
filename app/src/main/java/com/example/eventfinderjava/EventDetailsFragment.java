package com.example.eventfinderjava;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.material.color.MaterialColors;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.widget.ViewPager2;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventDetailsFragment extends Fragment {

    private static final String TAG = "EventDetailsFragment";
    private static final String ARG_EVENT_ID = "event_id";
    private static final String ARG_EVENT_NAME = "event_name";
    private static final String BASE_URL = "https://backend-dot-steam-house-473501-t8.wl.r.appspot.com";

    // UI Components
    private ImageButton buttonBack;
    private TextView textTitle;
    private ImageButton buttonFavorite;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private EventDetailsPagerAdapter pagerAdapter;
    private View headerRow;

    // State
    private String eventId;
    private String eventName;
    private EventDetails eventDetails;
    private boolean isFavorite = false;
    private RequestQueue requestQueue;

    public static EventDetailsFragment newInstance(String eventId, String eventName) {
        EventDetailsFragment fragment = new EventDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        args.putString(ARG_EVENT_NAME, eventName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId = getArguments().getString(ARG_EVENT_ID);
            eventName = getArguments().getString(ARG_EVENT_NAME);
        }
        
        // Validate event ID
        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "Event ID is null or empty");
            // Don't crash - we'll show an error in the UI
        }
        
        // Initialize request queue safely
        if (getContext() != null) {
            requestQueue = Volley.newRequestQueue(getContext());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.event_details_fragment, container, false);

        initializeViews(view);
        setupBackButton();
        setupFavoriteButton();
        setupTabs();
        
        // Position header below status bar
        if (headerRow != null) {
            ViewCompat.setOnApplyWindowInsetsListener(headerRow, (v, insets) -> {
                int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                v.setPadding(0, statusBarHeight, 0, 0);
                return insets;
            });
        }

        // Fetch event details and check favorite status
        // Always try to fetch - the methods will handle null/empty eventId gracefully
        fetchEventDetails();
        checkFavoriteStatus();

        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh favorite status when fragment is resumed (in case it was changed elsewhere)
        if (eventId != null && !eventId.isEmpty()) {
            checkFavoriteStatus();
        }
    }

    private void initializeViews(View view) {
        buttonBack = view.findViewById(R.id.buttonBack);
        textTitle = view.findViewById(R.id.textTitle);
        buttonFavorite = view.findViewById(R.id.buttonFavorite);
        tabLayout = view.findViewById(R.id.tabLayout);
        viewPager = view.findViewById(R.id.viewPager);
        headerRow = view.findViewById(R.id.layoutHeader);

        textTitle.setText(eventName != null ? eventName : "Event Details");
        textTitle.setSelected(true); // Enable marquee
    }

    private void setupBackButton() {
        buttonBack.setOnClickListener(v -> {
            if (getActivity() != null && getActivity().getSupportFragmentManager() != null) {
                int backStackCount = getActivity().getSupportFragmentManager().getBackStackEntryCount();
                if (backStackCount > 0) {
                    getActivity().getSupportFragmentManager().popBackStack();
                } else {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).replaceFragment(new SearchFragment());
                    }
                }
            }
        });
    }

    private void setupFavoriteButton() {
        updateFavoriteButton();
        buttonFavorite.setOnClickListener(v -> {
            isFavorite = !isFavorite;
            updateFavoriteButton();
            handleFavoriteToggle();
        });
    }

    private void updateFavoriteButton() {
        if (buttonFavorite == null || getContext() == null) {
            return;
        }
        
        if (isFavorite) {
            // Filled star with #50515B color
            buttonFavorite.setImageResource(R.drawable.ic_star_filled);
            buttonFavorite.setColorFilter(MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, 0));
        } else {
            // Outline star with border only, no fill
            buttonFavorite.setImageResource(R.drawable.ic_star_outline);
            buttonFavorite.setColorFilter(MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        }
    }

    private void setupTabs() {
        pagerAdapter = new EventDetailsPagerAdapter(EventDetailsFragment.this, eventId);
        viewPager.setAdapter(pagerAdapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Details");
                    tab.setIcon(R.drawable.ic_details);
                    break;
                case 1:
                    tab.setText("Artist");
                    tab.setIcon(R.drawable.ic_artist);
                    break;
                case 2:
                    tab.setText("Venue");
                    tab.setIcon(R.drawable.ic_venue);
                    break;
            }
        }).attach();

        // Enable swiping between tabs
        viewPager.setUserInputEnabled(true);
    }

    private void fetchEventDetails() {
        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "Cannot fetch event details: eventId is null or empty");
            return;
        }
        
        if (requestQueue == null) {
            Log.e(TAG, "Request queue is null");
            if (getContext() != null) {
                requestQueue = Volley.newRequestQueue(getContext());
            } else {
                Log.e(TAG, "Cannot create request queue: context is null");
                return;
            }
        }
        
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
                    if (response == null) {
                        Log.e(TAG, "Event details response is null");
                        return;
                    }
                    try {
                        parseEventDetails(response);
                        if (pagerAdapter != null) {
                            pagerAdapter.setEventDetails(eventDetails);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing event details", e);
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error parsing event details", e);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e(TAG, "Error fetching event details", error);
                }
            }
        );

        requestQueue.add(request);
    }

    private void checkFavoriteStatus() {
        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "Cannot check favorite status: eventId is null or empty");
            return;
        }
        
        if (requestQueue == null) {
            Log.e(TAG, "Request queue is null");
            if (getContext() != null) {
                requestQueue = Volley.newRequestQueue(getContext());
            } else {
                Log.e(TAG, "Cannot create request queue: context is null");
                return;
            }
        }
        
        String url = BASE_URL + "/api/favorites";
        Log.d(TAG, "Checking favorite status from: " + url);

        StringRequest request = new StringRequest(
            Request.Method.GET,
            url,
            response -> {
                try {
                    // API returns JSONArray directly, not wrapped in JSONObject
                    JSONArray favorites = new JSONArray(response);
                    for (int i = 0; i < favorites.length(); i++) {
                        JSONObject favorite = favorites.getJSONObject(i);
                        String favoriteId = favorite.optString("id", "");
                        if (favoriteId.equals(eventId)) {
                            isFavorite = true;
                            updateFavoriteButton();
                            break;
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing favorites response", e);
                }
            },
            error -> {
                Log.e(TAG, "Error checking favorite status", error);
            }
        );

        requestQueue.add(request);
    }

    private void parseEventDetails(JSONObject response) throws JSONException {
        eventDetails = new EventDetails();
        eventDetails.id = eventId;

        // Parse date and time
        JSONObject dates = response.optJSONObject("dates");
        if (dates != null) {
            JSONObject start = dates.optJSONObject("start");
            if (start != null) {
                eventDetails.localDate = start.optString("localDate", "");
                eventDetails.localTime = start.optString("localTime", "");
            }
        }

        // Parse artists/team
        JSONObject embedded = response.optJSONObject("_embedded");
        if (embedded != null) {
            JSONArray attractions = embedded.optJSONArray("attractions");
            if (attractions != null && attractions.length() > 0) {
                List<String> artistNames = new ArrayList<>();
                for (int i = 0; i < attractions.length(); i++) {
                    JSONObject attraction = attractions.getJSONObject(i);
                    String name = attraction.optString("name", "");
                    if (!name.isEmpty()) {
                        artistNames.add(name);
                    }
                }
                eventDetails.artists = String.join(", ", artistNames);
            }

            // Parse venue
            JSONArray venues = embedded.optJSONArray("venues");
            if (venues != null && venues.length() > 0) {
                eventDetails.venueName = venues.getJSONObject(0).optString("name", "");
            }
        }

        // Parse classifications (genres)
        JSONArray classifications = response.optJSONArray("classifications");
        if (classifications != null && classifications.length() > 0) {
            JSONObject classification = classifications.getJSONObject(0);
            
            // Safely parse nested JSONObjects - they might be null
            JSONObject segment = classification.optJSONObject("segment");
            if (segment != null) {
                eventDetails.segment = segment.optString("name", "");
            }
            
            JSONObject genre = classification.optJSONObject("genre");
            if (genre != null) {
                eventDetails.genre = genre.optString("name", "");
            }
            
            JSONObject subGenre = classification.optJSONObject("subGenre");
            if (subGenre != null) {
                eventDetails.subGenre = subGenre.optString("name", "");
            }
            
            JSONObject type = classification.optJSONObject("type");
            if (type != null) {
                eventDetails.type = type.optString("name", "");
            }
            
            JSONObject subType = classification.optJSONObject("subType");
            if (subType != null) {
                eventDetails.subType = subType.optString("name", "");
            }
        }

        // Parse ticket status
        JSONObject sales = response.optJSONObject("sales");
        if (sales != null) {
            JSONObject publicSales = sales.optJSONObject("public");
            if (publicSales != null) {
                String startDateTime = publicSales.optString("startDateTime", "");
                String endDateTime = publicSales.optString("endDateTime", "");
                // Determine ticket status based on dates
                // TODO: Compare with current date to determine status
                eventDetails.ticketStatus = "On Sale"; // Placeholder
            }
        }

        // Parse price ranges
        JSONArray priceRanges = response.optJSONArray("priceRanges");
        if (priceRanges != null && priceRanges.length() > 0) {
            List<String> priceStrings = new ArrayList<>();
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
            eventDetails.priceRanges = String.join(", ", priceStrings);
        }

        // Parse seatmap
        JSONObject seatmap = response.optJSONObject("seatmap");
        if (seatmap != null) {
            eventDetails.seatmapUrl = seatmap.optString("staticUrl", "");
        }

        // Parse Ticketmaster URL
        String url = response.optString("url", "");
        eventDetails.ticketmasterUrl = url;
        
        // Parse image URL
        JSONArray images = response.optJSONArray("images");
        if (images != null && images.length() > 0) {
            // Try to get the largest image
            JSONObject bestImage = null;
            int maxWidth = 0;
            for (int i = 0; i < images.length(); i++) {
                JSONObject img = images.getJSONObject(i);
                int width = img.optInt("width", 0);
                if (width > maxWidth) {
                    maxWidth = width;
                    bestImage = img;
                }
            }
            if (bestImage != null) {
                eventDetails.imageUrl = bestImage.optString("url", "");
            } else {
                eventDetails.imageUrl = images.getJSONObject(0).optString("url", "");
            }
        } else {
            eventDetails.imageUrl = "";
        }

        Log.d(TAG, "Event details parsed successfully");
    }

    private void handleFavoriteToggle() {
        if (isFavorite) {
            addToFavorites();
        } else {
            removeFromFavorites();
        }
    }

    private void addToFavorites() {
        String url = BASE_URL + "/api/favorites";
        Log.d(TAG, "Adding to favorites: " + eventId);

        JSONObject requestBody = new JSONObject();
        try {
            // Send full event details as backend expects
            requestBody.put("id", eventId);
            requestBody.put("name", eventName != null ? eventName : "");
            
            // Use eventDetails if available, otherwise use defaults
            if (eventDetails != null) {
                requestBody.put("venue", eventDetails.venueName != null ? eventDetails.venueName : "");
                requestBody.put("image", eventDetails.imageUrl != null ? eventDetails.imageUrl : "");
                requestBody.put("date", eventDetails.localDate != null ? eventDetails.localDate : "");
                requestBody.put("time", eventDetails.localTime != null ? eventDetails.localTime : "");
                requestBody.put("segment", eventDetails.segment != null ? eventDetails.segment : "");
            } else {
                // Fallback if eventDetails not yet loaded
                requestBody.put("venue", "");
                requestBody.put("image", "");
                requestBody.put("date", "");
                requestBody.put("time", "");
                requestBody.put("segment", "");
            }
            
            Log.d(TAG, "Sending favorite data: name=" + eventName + ", venue=" + 
                (eventDetails != null ? eventDetails.venueName : "null"));
        } catch (JSONException e) {
            Log.e(TAG, "Error creating request body", e);
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.POST,
            url,
            requestBody,
            response -> {
                Log.d(TAG, "Successfully added to favorites");
                // Update search results if this event is visible
                updateSearchResultsFavoriteState(eventId, true);
            },
            error -> {
                Log.e(TAG, "Error adding to favorites", error);
                // Revert state on error
                isFavorite = false;
                updateFavoriteButton();
            }
        );

        requestQueue.add(request);
    }

    private void removeFromFavorites() {
        String url = BASE_URL + "/api/favorites/" + eventId;
        Log.d(TAG, "Removing from favorites: " + eventId);

        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.DELETE,
            url,
            null,
            response -> {
                Log.d(TAG, "Successfully removed from favorites");
                // Update search results if this event is visible
                updateSearchResultsFavoriteState(eventId, false);
            },
            error -> {
                Log.e(TAG, "Error removing from favorites", error);
                // Revert state on error
                isFavorite = true;
                updateFavoriteButton();
            }
        );

        requestQueue.add(request);
    }
    
    private void updateSearchResultsFavoriteState(String eventId, boolean isFavorite) {
        // Find and update SearchFragment if it exists
        try {
            if (getActivity() != null) {
                FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
                // Try to find SearchFragment by iterating through fragments
                List<Fragment> fragments = fragmentManager.getFragments();
                for (Fragment fragment : fragments) {
                    if (fragment instanceof SearchFragment) {
                        ((SearchFragment) fragment).updateEventFavoriteState(eventId, isFavorite);
                        Log.d(TAG, "Updated favorite state in SearchFragment");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating search results favorite state", e);
        }
    }

    public EventDetails getEventDetails() {
        return eventDetails;
    }

    public static class EventDetails {
        public String id;
        public String localDate;
        public String localTime;
        public String artists;
        public String venueName;
        public String segment;
        public String genre;
        public String subGenre;
        public String type;
        public String subType;
        public String ticketStatus;
        public String priceRanges;
        public String seatmapUrl;
        public String ticketmasterUrl;
        public String imageUrl;
    }
}

