package com.example.eventfinderjava;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.color.MaterialColors;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SearchFragment extends Fragment {

    // UI Components
    private AutoCompleteTextView editKeyword;
    private TextView textKeywordError;
    private AutoCompleteTextView autoCompleteLocation;
    private TextInputEditText editDistance;
    private TextView textDistanceError;
    private TabLayout tabCategories;
    private ImageButton buttonBack;
    private ImageButton buttonIncrease;
    private ImageButton buttonDecrease;
    private ImageView imageLocationDropdown;
    private ImageView imageSearchIcon;
    private ProgressBar progressLoading;
    private TextView textNoEvents;
    private com.google.android.material.card.MaterialCardView cardNoEvents;
    private RecyclerView recyclerResults;
    private View headerRow;
    
    // Search results
    private List<Event> searchResults = new ArrayList<>();
    private List<Event> allSearchResults = new ArrayList<>(); // Store unfiltered results for category filtering
    private List<Event> likedEvents = new ArrayList<>(); // Liked events that persist across searches
    private List<Event> displayResults = new ArrayList<>(); // List for adapter - contains either favorites (when keyword empty) or search results (when keyword has text), never both
    private SearchResultsAdapter resultsAdapter;

    // State
    private String keyword = "";
    private String locationText = "Current Location";
    private double latitude = 0.0;
    private double longitude = 0.0;
    private int distance = 10;
    private String selectedCategory = "All";
    private boolean isLoading = false;
    private boolean hasPerformedSearch = false; // Track if user has performed a search
    private List<String> locationSuggestions = new ArrayList<>();
    private ArrayAdapter<String> locationAdapter;
    private boolean isLocationFieldClickInProgress = false;
    private boolean isLocationSelectionInProgress = false; // Flag to prevent dropdown from showing after selection
    private Handler locationDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable locationDebounceRunnable;
    private Runnable locationSearchingRemoveRunnable; // Runnable to delay removing "Searching..."
    private boolean isLocationSearching = false;
    private long locationSearchStartTime = 0;
    private StringRequest currentLocationRequest; // Track current location API request to cancel if needed
    private String currentLocationQuery = ""; // Track current query to ignore stale responses
    private static final long MIN_SEARCHING_DISPLAY_TIME_MS = 1500; // Minimum time to show "Searching..." (1.5 seconds)
    private TextWatcher locationTextWatcher; // Store TextWatcher to temporarily remove it when setting selected text
    private boolean isFavoriteClickInProgress = false; // Flag to prevent keyboard/dropdown from showing after favorite click
    private boolean isCategorySwitchInProgress = false; // Flag to prevent keyboard/dropdown from showing during category switch
    private boolean isDistanceFieldInUse = false; // Flag to prevent letter keyboard from showing when distance field is used

    // Keyword suggestions
    private List<String> keywordSuggestions = new ArrayList<>();
    private ArrayAdapter<String> keywordAdapter;
    private RequestQueue requestQueue;
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable debounceRunnable;
    private static final String BASE_URL = "https://backend-dot-steam-house-473501-t8.wl.r.appspot.com";
    private static final int DEBOUNCE_DELAY_MS = 250;

    // Categories
    private static final String[] CATEGORIES = {
        "All", "Music", "Sports", "Arts & Theatre", "Film", "Miscellaneous"
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Log.d("SearchFragment", "onCreateView called");
        try {
            View view = inflater.inflate(R.layout.search_fragment, container, false);
            Log.d("SearchFragment", "Layout inflated successfully");

            // Initialize Volley request queue FIRST (before initializeViews which needs it)
            requestQueue = Volley.newRequestQueue(requireContext());
            Log.d("SearchFragment", "Request queue initialized");

            initializeViews(view);
            Log.d("SearchFragment", "Views initialized");

            setupCategoryTabs();
            Log.d("SearchFragment", "Category tabs setup");
            
            setupLocationDropdown();
            Log.d("SearchFragment", "Location dropdown setup");
            
            setupDistanceSelector();
            Log.d("SearchFragment", "Distance selector setup");
            
            setupKeywordInput();
            Log.d("SearchFragment", "Keyword input setup");
            
            // Restore keyword text if it was previously entered (when navigating back from Event Details)
            if (editKeyword != null && !keyword.isEmpty()) {
                editKeyword.setText(keyword);
                editKeyword.setSelection(keyword.length()); // Set cursor at end
                Log.d("SearchFragment", "Restored keyword text: " + keyword);
                
                // Trigger suggestions fetch for the restored keyword
                editKeyword.postDelayed(() -> {
                    if (editKeyword != null && isAdded()) {
                        fetchKeywordSuggestions(keyword);
                        Log.d("SearchFragment", "Triggered suggestions fetch for restored keyword");
                    }
                }, 100);
            }
            
            setupBackButton();
            Log.d("SearchFragment", "Back button setup");
            
            setupSearchIcon(view);
            Log.d("SearchFragment", "Search icon setup");

            // Load favorites on initial load to display them if no search results
            loadFavoritesAsSearchResults();
            
            // Show "No events found" card on initial load only if no favorites
            if (cardNoEvents != null && textNoEvents != null && searchResults.isEmpty()) {
                cardNoEvents.setVisibility(View.VISIBLE);
                if (cardNoEvents != null) cardNoEvents.setVisibility(View.VISIBLE);
                textNoEvents.setVisibility(View.VISIBLE);
            }
            if (progressLoading != null) {
                progressLoading.setVisibility(View.GONE);
            }
            if (recyclerResults != null) {
                recyclerResults.setVisibility(View.GONE);
            }

            // Position header below status bar
            if (headerRow != null) {
                ViewCompat.setOnApplyWindowInsetsListener(headerRow, (v, insets) -> {
                    int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
                    v.setPadding(0, statusBarHeight, 0, 0);
                    return insets;
                });
            }

            // Focus keyword input on load and show keyboard
            Log.d("SearchFragment", "=== Attempting to show keyboard on load ===");
            if (editKeyword != null) {
                Log.d("SearchFragment", "editKeyword is NOT null");
                Log.d("SearchFragment", "Fragment isAdded: " + isAdded());
                Log.d("SearchFragment", "Fragment isResumed: " + isResumed());
                Log.d("SearchFragment", "editKeyword.isFocusable: " + editKeyword.isFocusable());
                Log.d("SearchFragment", "editKeyword.isFocusableInTouchMode: " + editKeyword.isFocusableInTouchMode());
                Log.d("SearchFragment", "editKeyword.hasFocus: " + editKeyword.hasFocus());
                Log.d("SearchFragment", "editKeyword.getWindowToken: " + (editKeyword.getWindowToken() != null ? "NOT NULL" : "NULL"));
                
                // Wait for view to be fully attached and laid out
                editKeyword.post(() -> {
                    Log.d("SearchFragment", "=== POST 1: Inside post() callback ===");
                    Log.d("SearchFragment", "editKeyword is null: " + (editKeyword == null));
                    Log.d("SearchFragment", "Fragment isAdded: " + isAdded());
                    if (editKeyword != null && isAdded()) {
                        boolean focused = editKeyword.requestFocus();
                        Log.d("SearchFragment", "requestFocus() called, result: " + focused);
                        Log.d("SearchFragment", "hasFocus() after requestFocus: " + editKeyword.hasFocus());
                        Log.d("SearchFragment", "getWindowToken() after requestFocus: " + (editKeyword.getWindowToken() != null ? "NOT NULL" : "NULL"));
                        
                        // Try showing keyboard with multiple methods (emulator-friendly)
                        editKeyword.postDelayed(() -> {
                            Log.d("SearchFragment", "=== POST 2 (500ms delay): Attempting to show keyboard ===");
                            Log.d("SearchFragment", "editKeyword is null: " + (editKeyword == null));
                            Log.d("SearchFragment", "hasFocus: " + (editKeyword != null ? editKeyword.hasFocus() : "N/A"));
                            Log.d("SearchFragment", "isAdded: " + isAdded());
                            
                            if (editKeyword != null && editKeyword.hasFocus() && isAdded()) {
                                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                Log.d("SearchFragment", "InputMethodManager is null: " + (imm == null));
                                
                                if (imm != null) {
                                    Log.d("SearchFragment", "getWindowToken before showSoftInput: " + (editKeyword.getWindowToken() != null ? "NOT NULL" : "NULL"));
                                    
                                    // Method 1: showSoftInput
                                    boolean shown1 = imm.showSoftInput(editKeyword, InputMethodManager.SHOW_IMPLICIT);
                                    Log.d("SearchFragment", "*** showSoftInput(SHOW_IMPLICIT) called, result: " + shown1 + " ***");
                                    
                                    // Method 2: toggleSoftInput only as fallback if showSoftInput failed
                                    if (!shown1) {
                                        Log.d("SearchFragment", "showSoftInput failed, trying toggleSoftInput as fallback");
                                        editKeyword.postDelayed(() -> {
                                            Log.d("SearchFragment", "=== POST 3 (700ms total delay): Attempting toggleSoftInput ===");
                                            Log.d("SearchFragment", "editKeyword is null: " + (editKeyword == null));
                                            Log.d("SearchFragment", "hasFocus: " + (editKeyword != null ? editKeyword.hasFocus() : "N/A"));
                                            Log.d("SearchFragment", "isAdded: " + isAdded());
                                            
                                            if (editKeyword != null && editKeyword.hasFocus() && isAdded()) {
                                                InputMethodManager imm2 = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                                Log.d("SearchFragment", "InputMethodManager (retry) is null: " + (imm2 == null));
                                                
                                                if (imm2 != null) {
                                                    Log.d("SearchFragment", "*** toggleSoftInput(SHOW_FORCED) called as fallback ***");
                                                    imm2.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                                                    Log.d("SearchFragment", "toggleSoftInput completed");
                                                } else {
                                                    Log.e("SearchFragment", "InputMethodManager is NULL in retry!");
                                                }
                                            } else {
                                                Log.w("SearchFragment", "Cannot call toggleSoftInput - conditions not met");
                                            }
                                        }, 200);
                                    } else {
                                        Log.d("SearchFragment", "Keyboard shown successfully with showSoftInput, skipping toggleSoftInput");
                                    }
                                } else {
                                    Log.e("SearchFragment", "InputMethodManager is NULL!");
                                }
                            } else {
                                Log.w("SearchFragment", "Cannot show keyboard - field not focused or fragment not added");
                                Log.w("SearchFragment", "  - editKeyword null: " + (editKeyword == null));
                                Log.w("SearchFragment", "  - hasFocus: " + (editKeyword != null ? editKeyword.hasFocus() : "N/A"));
                                Log.w("SearchFragment", "  - isAdded: " + isAdded());
                            }
                        }, 500);
                    } else {
                        Log.w("SearchFragment", "Cannot proceed - editKeyword is null or fragment not added");
                        Log.w("SearchFragment", "  - editKeyword null: " + (editKeyword == null));
                        Log.w("SearchFragment", "  - isAdded: " + isAdded());
                    }
                });
            } else {
                Log.e("SearchFragment", "editKeyword is NULL! Cannot show keyboard");
            }

            Log.d("SearchFragment", "onCreateView completed successfully");
            return view;
        } catch (Exception e) {
            Log.e("SearchFragment", "Error in onCreateView", e);
            throw e;
        }
    }

    private void initializeViews(View view) {
        Log.d("SearchFragment", "Initializing views");
        try {
            editKeyword = view.findViewById(R.id.editKeyword);
            textKeywordError = view.findViewById(R.id.textKeywordError);
            autoCompleteLocation = view.findViewById(R.id.autoCompleteLocation);
            editDistance = view.findViewById(R.id.editDistance);
            textDistanceError = view.findViewById(R.id.textDistanceError);
            tabCategories = view.findViewById(R.id.tabCategories);
            buttonBack = view.findViewById(R.id.buttonBack);
            buttonIncrease = view.findViewById(R.id.buttonIncrease);
            buttonDecrease = view.findViewById(R.id.buttonDecrease);
            imageLocationDropdown = view.findViewById(R.id.imageLocationDropdown);
            imageSearchIcon = view.findViewById(R.id.imageSearchIcon);
            progressLoading = view.findViewById(R.id.progressLoading);
            textNoEvents = view.findViewById(R.id.textNoEvents);
            cardNoEvents = view.findViewById(R.id.cardNoEvents);
            recyclerResults = view.findViewById(R.id.recyclerResults);
            headerRow = view.findViewById(R.id.layoutSearchHeader);

            recyclerResults.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerResults.setHasFixedSize(true);
            
            // Initialize search results adapter (use displayResults which contains either favorites or search results based on keyword)
            resultsAdapter = new SearchResultsAdapter(displayResults, new SearchResultsAdapter.OnFavoriteClickListener() {
                @Override
                public void onFavoriteClick(Event event, boolean isFavorite) {
                    // Handle favorite toggle
                    handleFavoriteToggle(event, isFavorite);
                }
            });
            
            // Add item click listener for navigation to details
            resultsAdapter.setOnItemClickListener(event -> {
                // Navigate to event details
                EventDetailsFragment detailsFragment = EventDetailsFragment.newInstance(event.id, event.name);
                if (getActivity() instanceof MainActivity) {
                    MainActivity activity = (MainActivity) getActivity();
                    activity.getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer, detailsFragment)
                            .addToBackStack(null)
                            .commit();
                }
            });
            // Set image request queue (ensure it's initialized)
            if (requestQueue != null) {
                resultsAdapter.setImageRequestQueue(requestQueue);
                Log.d("SearchFragment", "Image request queue set on adapter");
            } else {
                Log.e("SearchFragment", "ERROR: requestQueue is NULL when setting on adapter!");
            }
            recyclerResults.setAdapter(resultsAdapter);
            
            Log.d("SearchFragment", "Views initialized successfully");
        } catch (Exception e) {
            Log.e("SearchFragment", "Error initializing views", e);
            throw e;
        }
    }

    private void setupCategoryTabs() {
        for (String category : CATEGORIES) {
            TabLayout.Tab tab = tabCategories.newTab();
            tab.setText(category);
            tabCategories.addTab(tab);
        }

        // Select "All" by default and reset selectedCategory
        selectedCategory = "All";
        tabCategories.selectTab(tabCategories.getTabAt(0));

        tabCategories.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String newCategory = tab.getText().toString();
                
                // Only process if category actually changed
                if (newCategory.equals(selectedCategory)) {
                    Log.d("SearchFragment", "Category already selected: " + selectedCategory + ", ignoring");
                    return;
                }
                
                selectedCategory = newCategory;
                Log.d("SearchFragment", "Category selected: " + selectedCategory);
                
                // Set flag to prevent keyboard/dropdown from showing during category switch
                isCategorySwitchInProgress = true;
                
                // Hide keyboard and dropdown when category is selected
                if (editKeyword != null) {
                    editKeyword.dismissDropDown();
                    if (editKeyword.getWindowToken() != null) {
                        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(editKeyword.getWindowToken(), 0);
                            editKeyword.clearFocus();
                            Log.d("SearchFragment", "Keyboard and dropdown hidden after category selection");
                        }
                    }
                }
                
                // Hide location dropdown as well
                if (autoCompleteLocation != null) {
                    autoCompleteLocation.dismissDropDown();
                    if (autoCompleteLocation.getWindowToken() != null) {
                        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(autoCompleteLocation.getWindowToken(), 0);
                            autoCompleteLocation.clearFocus();
                        }
                    }
                }
                
                // Make a new backend call for the selected category instead of filtering
                // Only if we have search parameters (keyword or location set)
                if (hasPerformedSearch && (!keyword.isEmpty() || !locationText.equals("Current Location"))) {
                    Log.d("SearchFragment", "Making new backend call for category: " + selectedCategory);
                    // Show loading state
                    isLoading = true;
                    if (progressLoading != null) {
                        progressLoading.setVisibility(View.VISIBLE);
                    }
                    if (cardNoEvents != null) {
                        cardNoEvents.setVisibility(View.GONE);
                    }
                    if (recyclerResults != null) {
                        recyclerResults.setVisibility(View.GONE);
                    }
                    
                    // Make API call with the new category
                    performSearchWithLocation();
                } else {
                    Log.d("SearchFragment", "No search performed yet, cannot make category-specific call");
                    // Clear flag after a delay
                    editKeyword.postDelayed(() -> {
                        isCategorySwitchInProgress = false;
                        Log.d("SearchFragment", "Category switch flag cleared");
                    }, 500);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // When clicking on already selected tab, ensure keyboard and dropdown stay hidden
                if (editKeyword != null) {
                    editKeyword.dismissDropDown();
                    if (editKeyword.getWindowToken() != null) {
                        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(editKeyword.getWindowToken(), 0);
                            editKeyword.clearFocus();
                            Log.d("SearchFragment", "Keyboard and dropdown hidden after category reselection");
                        }
                    }
                }
            }
        });
    }

    private void setupLocationDropdown() {
        Log.d("SearchFragment", "=== Setting up location dropdown ===");
        try {
            if (autoCompleteLocation == null) {
                Log.e("SearchFragment", "autoCompleteLocation is NULL!");
                return;
            }
            Log.d("SearchFragment", "autoCompleteLocation view found");

            // Initialize with "Current Location" as first option
            locationSuggestions.clear();
            locationSuggestions.add("Current Location");
            Log.d("SearchFragment", "Added 'Current Location' to suggestions. Total: " + locationSuggestions.size());

            // Create custom adapter for location suggestions
            locationAdapter = new ArrayAdapter<String>(
                requireContext(),
                R.layout.dropdown_item_location,
                R.id.textLocation,
                locationSuggestions
            ) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View view;
                    if (convertView == null) {
                        view = LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item_location, parent, false);
                    } else {
                        view = convertView;
                    }
                    
                TextView textView = view.findViewById(R.id.textLocation);
                ProgressBar progressBar = view.findViewById(R.id.progressLocation);
                ImageView iconView = view.findViewById(R.id.imageLocationIcon);
                
                String item = getItem(position);
                if (item != null) {
                    if (item.equals("Searching...")) {
                        // Show loading state - spinner before text
                        textView.setText("Searching...");
                        progressBar.setVisibility(View.VISIBLE);
                        iconView.setVisibility(View.GONE);
                    } else if (item.equals("Current Location")) {
                        // Show "Current Location" with icon
                        textView.setText(item);
                        iconView.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                    } else {
                        // Show normal location item without icon
                        textView.setText(item);
                        iconView.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                    }
                }
                return view;
                }
            };
            Log.d("SearchFragment", "Adapter created with " + locationAdapter.getCount() + " items");
            Log.d("SearchFragment", "Adapter items: " + locationSuggestions.toString());
            
            autoCompleteLocation.setAdapter(locationAdapter);
            Log.d("SearchFragment", "Adapter set on AutoCompleteTextView");
            Log.d("SearchFragment", "Adapter is null? " + (autoCompleteLocation.getAdapter() == null));
            if (autoCompleteLocation.getAdapter() != null) {
                Log.d("SearchFragment", "Adapter after set has " + autoCompleteLocation.getAdapter().getCount() + " items");
            }
            
            // Force adapter to notify data set changed
            locationAdapter.notifyDataSetChanged();
            Log.d("SearchFragment", "Adapter notifyDataSetChanged() called");
            
            // Set threshold to 0 so dropdown shows even with empty text
            autoCompleteLocation.setThreshold(0);
            Log.d("SearchFragment", "Threshold set to 0");
            Log.d("SearchFragment", "Current threshold: " + autoCompleteLocation.getThreshold());
            
            // Ensure dropdown can be shown
            autoCompleteLocation.setDropDownHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            Log.d("SearchFragment", "Dropdown height set to WRAP_CONTENT");
            
            // Set dropdown width to be narrower (80% of screen width or 300dp, whichever is smaller)
            autoCompleteLocation.post(() -> {
                if (autoCompleteLocation != null) {
                    int screenWidth = getResources().getDisplayMetrics().widthPixels;
                    int dropdownWidth = Math.min((int)(screenWidth * 0.8), (int)(300 * getResources().getDisplayMetrics().density));
                    autoCompleteLocation.setDropDownWidth(dropdownWidth);
                    Log.d("SearchFragment", "Dropdown width set to: " + dropdownWidth + "px");
                }
            });
            
            autoCompleteLocation.setText("Current Location", false);
            Log.d("SearchFragment", "Initial text set to 'Current Location'");
            Log.d("SearchFragment", "Current text after set: '" + autoCompleteLocation.getText().toString() + "'");

            // Make location field clickable to show dropdown
            // Use OnTouchListener to intercept BEFORE focus is gained
            autoCompleteLocation.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    Log.d("SearchFragment", "*** Location field TOUCHED (ACTION_DOWN) ***");
                    Log.d("SearchFragment", "Current text: '" + autoCompleteLocation.getText().toString() + "'");
                    Log.d("SearchFragment", "Has focus: " + autoCompleteLocation.hasFocus());
                    Log.d("SearchFragment", "Adapter count: " + (autoCompleteLocation.getAdapter() != null ? autoCompleteLocation.getAdapter().getCount() : "NULL"));
                    
                    // Set flag to prevent focus listener from interfering
                    isLocationFieldClickInProgress = true;
                    Log.d("SearchFragment", "Set isLocationFieldClickInProgress = true");
                    
                    // Clear text and show placeholder when dropdown opens
                    String currentText = autoCompleteLocation.getText().toString();
                    if (currentText.equals("Current Location")) {
                        autoCompleteLocation.setText("");
                        Log.d("SearchFragment", "Cleared 'Current Location' text");
                    }
                    
                    // Temporarily hide keyboard to show dropdown first
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(autoCompleteLocation.getWindowToken(), 0);
                        Log.d("SearchFragment", "Temporarily hid keyboard");
                    }
                    
                    // Show dropdown IMMEDIATELY (before focus is gained) - this is critical
                    Log.d("SearchFragment", "*** Showing dropdown IMMEDIATELY (BEFORE focus) ***");
                    showDropdownWithRetry(0);
                    
                    // Request focus after showing dropdown
                    autoCompleteLocation.requestFocus();
                    Log.d("SearchFragment", "Requested focus");
                    
                    // Ensure dropdown shows after view is laid out (multiple attempts)
                    autoCompleteLocation.post(() -> {
                        Log.d("SearchFragment", "*** POST 1: Attempting to show dropdown after layout ***");
                        showDropdownWithRetry(1);
                    });
                    
                    // Additional retry after a short delay
                    autoCompleteLocation.postDelayed(() -> {
                        Log.d("SearchFragment", "*** POST 2 (30ms): Attempting to show dropdown ***");
                        showDropdownWithRetry(2);
                    }, 30);
                    
                    // Final retry after longer delay
                    autoCompleteLocation.postDelayed(() -> {
                        Log.d("SearchFragment", "*** POST 3 (100ms): Attempting to show dropdown ***");
                        showDropdownWithRetry(3);
                        // Clear flag after dropdown should be shown
                        isLocationFieldClickInProgress = false;
                        Log.d("SearchFragment", "Cleared isLocationFieldClickInProgress flag");
                    }, 100);
                }
                // Return false to allow default click behavior (focus) to also happen
                return false;
            });
            
            // Also keep onClickListener as backup
            autoCompleteLocation.setOnClickListener(v -> {
                Log.d("SearchFragment", "*** Location field onClick (backup) ***");
                // Same logic as touch handler but only if flag not set
                if (!isLocationFieldClickInProgress) {
                    String currentText = autoCompleteLocation.getText().toString();
                    if (currentText.equals("Current Location")) {
                        autoCompleteLocation.setText("");
                    }
                    showDropdownWithRetry(0);
                    autoCompleteLocation.requestFocus();
                    autoCompleteLocation.post(() -> showDropdownWithRetry(1));
                    autoCompleteLocation.postDelayed(() -> showDropdownWithRetry(2), 30);
                    autoCompleteLocation.postDelayed(() -> showDropdownWithRetry(3), 100);
                }
            });

            // Make dropdown icon clickable
            if (imageLocationDropdown != null) {
                Log.d("SearchFragment", "Dropdown icon view found, setting click listener");
                imageLocationDropdown.setOnClickListener(v -> {
                    Log.d("SearchFragment", "*** Dropdown icon CLICKED ***");
                    String currentText = autoCompleteLocation.getText().toString();
                    Log.d("SearchFragment", "Current text: '" + currentText + "'");
                    if (currentText.equals("Current Location")) {
                        autoCompleteLocation.setText("");
                        Log.d("SearchFragment", "Cleared 'Current Location' text");
                    }
                    
                    // Request focus first
                    autoCompleteLocation.requestFocus();
                    Log.d("SearchFragment", "Requested focus");
                    
                    // Show dropdown immediately (synchronous)
                    showDropdownWithRetry(0);
                    
                    // Ensure dropdown shows after view is laid out (multiple attempts)
                    autoCompleteLocation.post(() -> {
                        Log.d("SearchFragment", "*** POST from icon: Attempting to show dropdown ***");
                        showDropdownWithRetry(1);
                    });
                    
                    autoCompleteLocation.postDelayed(() -> {
                        Log.d("SearchFragment", "*** POST 2 from icon (50ms): Attempting to show dropdown ***");
                        showDropdownWithRetry(2);
                    }, 50);
                    
                    autoCompleteLocation.postDelayed(() -> {
                        Log.d("SearchFragment", "*** POST 3 from icon (150ms): Attempting to show dropdown ***");
                        showDropdownWithRetry(3);
                    }, 150);
                });
            } else {
                Log.w("SearchFragment", "Dropdown icon view is NULL!");
            }

            // When dropdown is shown, focus the input
            autoCompleteLocation.setOnFocusChangeListener((v, hasFocus) -> {
                Log.d("SearchFragment", "*** Focus changed: " + hasFocus + " ***");
                Log.d("SearchFragment", "isLocationFieldClickInProgress: " + isLocationFieldClickInProgress);
                Log.d("SearchFragment", "isLocationSelectionInProgress: " + isLocationSelectionInProgress);
                
                if (hasFocus) {
                    // Skip focus handler if click or selection is in progress
                    if (isLocationFieldClickInProgress || isLocationSelectionInProgress) {
                        Log.d("SearchFragment", "Click/selection in progress, skipping focus handler dropdown logic");
                        return;
                    }
                    
                    String currentText = autoCompleteLocation.getText().toString();
                    Log.d("SearchFragment", "Focused. Current text: '" + currentText + "'");
                    if (currentText.equals("Current Location")) {
                        autoCompleteLocation.setText("");
                        Log.d("SearchFragment", "Cleared 'Current Location' on focus");
                    }
                    
                    // Show dropdown when focused (only if not already showing and not from click/selection)
                    if (!autoCompleteLocation.isPopupShowing() && !isLocationSelectionInProgress) {
                        autoCompleteLocation.post(() -> {
                            if (autoCompleteLocation.hasFocus() && !autoCompleteLocation.isPopupShowing() && !isLocationFieldClickInProgress && !isLocationSelectionInProgress) {
                                Log.d("SearchFragment", "*** POST on focus: Attempting to show dropdown (not from click) ***");
                                showDropdownWithRetry(1);
                            }
                        });
                    } else {
                        Log.d("SearchFragment", "Dropdown already showing or selection in progress, skipping focus handler");
                    }
                }
            });

            autoCompleteLocation.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    // Set flag IMMEDIATELY at the very start to prevent any other handlers from interfering
                    isLocationSelectionInProgress = true;
                    Log.d("SearchFragment", "*** Location item clicked at position " + position + " - setting selection flag IMMEDIATELY ***");
                    
                    // Dismiss dropdown FIRST, before anything else
                    if (autoCompleteLocation.isPopupShowing()) {
                        autoCompleteLocation.dismissDropDown();
                        Log.d("SearchFragment", "Dropdown dismissed immediately");
                    }
                    
                    // Remove ALL pending callbacks that might show dropdown or update suggestions
                    locationDebounceHandler.removeCallbacksAndMessages(null);
                    if (locationDebounceRunnable != null) {
                        locationDebounceHandler.removeCallbacks(locationDebounceRunnable);
                        locationDebounceRunnable = null;
                    }
                    if (locationSearchingRemoveRunnable != null) {
                        locationDebounceHandler.removeCallbacks(locationSearchingRemoveRunnable);
                        locationSearchingRemoveRunnable = null;
                    }
                    
                    // Get the selected text directly from the clicked view to avoid position issues
                    String selectedText = null;
                    if (view != null) {
                        TextView textView = view.findViewById(R.id.textLocation);
                        if (textView != null) {
                            selectedText = textView.getText().toString();
                            Log.d("SearchFragment", "Got text from clicked view: " + selectedText);
                        }
                    }
                    
                    // Fallback: Get from adapter if view text is not available
                    if (selectedText == null || selectedText.isEmpty()) {
                        if (locationAdapter != null && position >= 0 && position < locationAdapter.getCount()) {
                            selectedText = locationAdapter.getItem(position);
                            Log.d("SearchFragment", "Got text from adapter at position " + position + ": " + selectedText);
                        } else {
                            Log.e("SearchFragment", "Invalid position: " + position + ", adapter count: " + (locationAdapter != null ? locationAdapter.getCount() : "null"));
                            // Try to get from suggestions list directly
                            if (locationSuggestions != null && position >= 0 && position < locationSuggestions.size()) {
                                selectedText = locationSuggestions.get(position);
                                Log.d("SearchFragment", "Got text from suggestions list at position " + position + ": " + selectedText);
                            } else {
                                Log.e("SearchFragment", "Cannot get location text - invalid position or empty suggestions");
                                isLocationSelectionInProgress = false;
                                return;
                            }
                        }
                    }
                    
                    locationText = selectedText;
                    Log.d("SearchFragment", "*** Location item selected: " + locationText + " at position " + position + " ***");
                    Log.d("SearchFragment", "Adapter count: " + (locationAdapter != null ? locationAdapter.getCount() : "null") + ", Suggestions list: " + locationSuggestions.toString());
                        
                        // Double-check: Ensure "Searching..." is not in the suggestions list before selection
                        if (locationSuggestions.contains("Searching...")) {
                            Log.w("SearchFragment", "WARNING: 'Searching...' still in suggestions list! Removing it now.");
                            locationSuggestions.remove("Searching...");
                            isLocationSearching = false;
                            // Recreate adapter without "Searching..."
                            locationAdapter = new ArrayAdapter<String>(
                                requireContext(),
                                R.layout.dropdown_item_location,
                                R.id.textLocation,
                                locationSuggestions
                            ) {
                                @NonNull
                                @Override
                                public View getView(int pos, @Nullable View convertView, @NonNull ViewGroup parent) {
                                    View view = (convertView != null) ? convertView : 
                                        LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item_location, parent, false);
                                    TextView textView = view.findViewById(R.id.textLocation);
                                    ProgressBar progressBar = view.findViewById(R.id.progressLocation);
                                    ImageView iconView = view.findViewById(R.id.imageLocationIcon);
                                    String item = getItem(pos);
                                    if (item != null) {
                                        if (item.equals("Current Location")) {
                                            textView.setText(item);
                                            iconView.setVisibility(View.VISIBLE);
                                            progressBar.setVisibility(View.GONE);
                                        } else {
                                            textView.setText(item);
                                            iconView.setVisibility(View.GONE);
                                            progressBar.setVisibility(View.GONE);
                                        }
                                    }
                                    return view;
                                }
                            };
                            autoCompleteLocation.setAdapter(locationAdapter);
                            // Re-get the item after adapter update
                            if (position >= 0 && position < locationAdapter.getCount()) {
                                locationText = locationAdapter.getItem(position);
                                Log.d("SearchFragment", "Re-fetched item after removing 'Searching...': " + locationText);
                            }
                        }
                        
                        // Prevent selecting "Searching..." - if user clicked on it, ignore the selection
                        if (locationText != null && locationText.equals("Searching...")) {
                            Log.d("SearchFragment", "User clicked on 'Searching...' - ignoring selection");
                            // Just dismiss dropdown and return without changing text
                            if (autoCompleteLocation.isPopupShowing()) {
                                autoCompleteLocation.dismissDropDown();
                            }
                            return;
                        }
                        
                        // Stop searching state immediately when selection is made
                        isLocationSearching = false;
                        if (locationSearchingRemoveRunnable != null) {
                            locationDebounceHandler.removeCallbacks(locationSearchingRemoveRunnable);
                            locationSearchingRemoveRunnable = null;
                        }
                        Log.d("SearchFragment", "Stopped searching state and cancelled removal runnable");
                        
                        // Hide dropdown first
                        if (autoCompleteLocation.isPopupShowing()) {
                            autoCompleteLocation.dismissDropDown();
                        }
                        
                        // Hide keyboard when selection is made (same approach as keyword dropdown)
                        autoCompleteLocation.post(() -> {
                            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null && autoCompleteLocation.getWindowToken() != null) {
                                // Try multiple methods to ensure keyboard closes
                                imm.hideSoftInputFromWindow(autoCompleteLocation.getWindowToken(), 0);
                                imm.hideSoftInputFromWindow(autoCompleteLocation.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
                                Log.d("SearchFragment", "Keyboard hide requested (multiple methods)");
                            }
                            autoCompleteLocation.clearFocus();
                            Log.d("SearchFragment", "Dropdown dismissed and keyboard hidden after selection");
                        });
                        
                        // Dismiss dropdown again before setting text
                        if (autoCompleteLocation.isPopupShowing()) {
                            autoCompleteLocation.dismissDropDown();
                            Log.d("SearchFragment", "Dropdown dismissed before setText (2)");
                        }
                        
                        // Remove "Searching..." from suggestions if present
                        if (locationSuggestions.contains("Searching...")) {
                            locationSuggestions.remove("Searching...");
                            Log.d("SearchFragment", "Removed 'Searching...' from suggestions list");
                        }
                        
                        // Temporarily remove TextWatcher to prevent interference
                        if (locationTextWatcher != null) {
                            autoCompleteLocation.removeTextChangedListener(locationTextWatcher);
                            Log.d("SearchFragment", "Temporarily removed TextWatcher");
                        }
                        
                        // Set the text in the field (TextWatcher is removed so it won't trigger)
                        autoCompleteLocation.setText(locationText, false);
                        Log.d("SearchFragment", "Text set to: " + locationText);
                        
                        // Keep TextWatcher removed for longer to prevent it from re-adding "Searching..."
                        // Re-add TextWatcher after a delay to ensure selection is complete
                        autoCompleteLocation.postDelayed(() -> {
                            if (locationTextWatcher != null && !isLocationSelectionInProgress) {
                                autoCompleteLocation.addTextChangedListener(locationTextWatcher);
                                Log.d("SearchFragment", "Re-added TextWatcher after selection delay");
                            }
                        }, 500); // 500ms delay to ensure selection is fully processed
                        
                        // Ensure the text stays as selected location by setting it again after delays
                        autoCompleteLocation.post(() -> {
                            String currentText = autoCompleteLocation.getText().toString();
                            if (!currentText.equals(locationText) && !currentText.equals("Searching...")) {
                                if (locationTextWatcher != null) {
                                    autoCompleteLocation.removeTextChangedListener(locationTextWatcher);
                                }
                                autoCompleteLocation.setText(locationText, false);
                                if (locationTextWatcher != null) {
                                    autoCompleteLocation.addTextChangedListener(locationTextWatcher);
                                }
                                Log.d("SearchFragment", "Re-set text to selected location: " + locationText);
                            }
                        });
                        
                        autoCompleteLocation.postDelayed(() -> {
                            String currentText = autoCompleteLocation.getText().toString();
                            if (!currentText.equals(locationText) && !currentText.equals("Searching...")) {
                                if (locationTextWatcher != null) {
                                    autoCompleteLocation.removeTextChangedListener(locationTextWatcher);
                                }
                                autoCompleteLocation.setText(locationText, false);
                                if (locationTextWatcher != null) {
                                    autoCompleteLocation.addTextChangedListener(locationTextWatcher);
                                }
                                Log.d("SearchFragment", "Re-set text to selected location (delayed): " + locationText);
                            }
                        }, 100);
                        
                        // Dismiss dropdown immediately after setText
                        if (autoCompleteLocation.isPopupShowing()) {
                            autoCompleteLocation.dismissDropDown();
                            Log.d("SearchFragment", "Dropdown dismissed after setText (3)");
                        }
                        
                        // Ensure dropdown stays closed with multiple checks
                        autoCompleteLocation.post(() -> {
                            if (autoCompleteLocation.isPopupShowing()) {
                                autoCompleteLocation.dismissDropDown();
                                Log.d("SearchFragment", "Force dismissed dropdown in post (1)");
                            }
                        });
                        autoCompleteLocation.postDelayed(() -> {
                            if (autoCompleteLocation.isPopupShowing()) {
                                autoCompleteLocation.dismissDropDown();
                                Log.d("SearchFragment", "Force dismissed dropdown in post (2)");
                            }
                        }, 50);
                        autoCompleteLocation.postDelayed(() -> {
                            if (autoCompleteLocation.isPopupShowing()) {
                                autoCompleteLocation.dismissDropDown();
                                Log.d("SearchFragment", "Force dismissed dropdown in post (3)");
                            }
                        }, 100);
                        autoCompleteLocation.postDelayed(() -> {
                            if (autoCompleteLocation.isPopupShowing()) {
                                autoCompleteLocation.dismissDropDown();
                                Log.d("SearchFragment", "Force dismissed dropdown in post (4)");
                            }
                        }, 200);
                        
                        // Reset flag after a longer delay to ensure all handlers and post() callbacks have completed
                        autoCompleteLocation.postDelayed(() -> {
                            isLocationSelectionInProgress = false;
                            Log.d("SearchFragment", "Reset isLocationSelectionInProgress flag");
                        }, 1500);
                        
                        // TODO: If not "Current Location", geocode to get lat/lng
                } catch (Exception e) {
                    Log.e("SearchFragment", "Error handling location item click", e);
                    // Keep flag set for a delay even on error to prevent dropdown from showing
                    autoCompleteLocation.postDelayed(() -> {
                        isLocationSelectionInProgress = false;
                        Log.d("SearchFragment", "Reset isLocationSelectionInProgress flag after exception");
                    }, 1000);
                }
            });

            // Handle text changes for autocomplete
            locationTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    Log.d("SearchFragment", "Before text change: '" + s + "'");
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    // Skip if text is being set programmatically from selection
                    if (isLocationSelectionInProgress) {
                        Log.d("SearchFragment", "Skipping TextWatcher - selection in progress, text: '" + s + "'");
                        return;
                    }
                    // Don't process if selection is in progress (prevents dropdown from reopening after selection)
                    if (isLocationSelectionInProgress) {
                        Log.d("SearchFragment", "Skipping text change handler - selection in progress");
                        return;
                    }
                    
                    String query = s.toString().trim();
                    Log.d("SearchFragment", "*** Location text changed: '" + query + "' (length: " + query.length() + ") ***");
                    
                    // Cancel previous debounce
                    if (locationDebounceRunnable != null) {
                        locationDebounceHandler.removeCallbacks(locationDebounceRunnable);
                    }
                    
                    // Cancel any pending removal of "Searching..." from previous request
                    if (locationSearchingRemoveRunnable != null) {
                        locationDebounceHandler.removeCallbacks(locationSearchingRemoveRunnable);
                        locationSearchingRemoveRunnable = null;
                        Log.d("SearchFragment", "Cancelled previous 'remove Searching...' delay on new keystroke");
                    }
                    
                    // Set searching state immediately on every keystroke to show "Searching..." row
                    // BUT: Don't set searching state if this is a valid location selection (not user typing)
                    // Check if the text matches a location from suggestions (user selected it, not typing)
                    boolean isSelectedLocation = false;
                    if (locationSuggestions != null && !locationSuggestions.isEmpty()) {
                        for (String suggestion : locationSuggestions) {
                            if (suggestion.equals(query) && !suggestion.equals("Current Location") && !suggestion.equals("Searching...")) {
                                isSelectedLocation = true;
                                Log.d("SearchFragment", "Text matches a suggestion - this is a selection, not typing");
                                break;
                            }
                        }
                    }
                    
                    if (query.length() > 0 && !query.equals("Current Location") && !isSelectedLocation) {
                        isLocationSearching = true;
                        locationSearchStartTime = System.currentTimeMillis();
                        Log.d("SearchFragment", "Set isLocationSearching=true on keystroke for query: '" + query + "'");
                    } else {
                        isLocationSearching = false;
                        Log.d("SearchFragment", "Not setting searching state - query: '" + query + "', isSelectedLocation: " + isSelectedLocation);
                    }
                    
                    // Update suggestions immediately with user's typed text
                    updateLocationSuggestions(query);
                    
                    // Show dropdown when typing (but not if selection just happened)
                    // Check flag BEFORE posting to avoid queuing unnecessary callbacks
                    if (query.length() > 0 && !isLocationSelectionInProgress) {
                        autoCompleteLocation.post(() -> {
                            // Triple-check flag in post() callback - it might have changed
                            if (!isLocationSelectionInProgress && autoCompleteLocation.hasFocus() && !autoCompleteLocation.isPopupShowing()) {
                                Log.d("SearchFragment", "*** POST on text change: Attempting to show dropdown ***");
                                try {
                                    autoCompleteLocation.showDropDown();
                                    Log.d("SearchFragment", "*** showDropDown() called on text change successfully ***");
                                } catch (Exception e) {
                                    Log.e("SearchFragment", "ERROR calling showDropDown() on text change", e);
                                }
                            } else {
                                Log.d("SearchFragment", "Skipping showDropDown - selectionInProgress: " + isLocationSelectionInProgress + 
                                    ", hasFocus: " + autoCompleteLocation.hasFocus() + 
                                    ", isPopupShowing: " + autoCompleteLocation.isPopupShowing());
                            }
                        });
                        
                        // Debounce geocoding API call
                        locationDebounceRunnable = () -> {
                            if (query.length() > 0 && !query.equals("Current Location")) {
                                fetchLocationSuggestions(query);
                            }
                        };
                        locationDebounceHandler.postDelayed(locationDebounceRunnable, DEBOUNCE_DELAY_MS);
                    } else {
                        // Reset to just "Current Location" when empty
                        locationSuggestions.clear();
                        locationSuggestions.add("Current Location");
                        locationAdapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                    // Skip if text is being set programmatically from selection
                    if (isLocationSelectionInProgress) {
                        Log.d("SearchFragment", "Skipping afterTextChanged - selection in progress");
                        return;
                    }
                    Log.d("SearchFragment", "After text change: '" + s + "'");
                }
            };
            autoCompleteLocation.addTextChangedListener(locationTextWatcher);

            Log.d("SearchFragment", "=== Location dropdown setup complete ===");
        } catch (Exception e) {
            Log.e("SearchFragment", "ERROR setting up location dropdown", e);
            e.printStackTrace();
            throw e;
        }
    }

    private void showDropdownWithRetry(int attempt) {
        try {
            if (autoCompleteLocation == null) {
                Log.e("SearchFragment", "showDropdownWithRetry: autoCompleteLocation is NULL");
                return;
            }
            
            if (autoCompleteLocation.getAdapter() == null) {
                Log.d("SearchFragment", "showDropdownWithRetry (attempt " + attempt + "): Adapter is NULL");
                return;
            }
            
            int adapterCount = autoCompleteLocation.getAdapter().getCount();
            if (adapterCount == 0) {
                Log.d("SearchFragment", "showDropdownWithRetry (attempt " + attempt + "): Adapter count is 0");
                return;
            }
            
            if (autoCompleteLocation.isPopupShowing()) {
                Log.d("SearchFragment", "showDropdownWithRetry (attempt " + attempt + "): Dropdown already showing");
                return;
            }
            
            Log.d("SearchFragment", "showDropdownWithRetry (attempt " + attempt + "): Calling showDropDown(). Adapter count: " + adapterCount);
            autoCompleteLocation.showDropDown();
            Log.d("SearchFragment", "showDropdownWithRetry (attempt " + attempt + "): showDropDown() called successfully");
            
        } catch (Exception e) {
            Log.e("SearchFragment", "ERROR in showDropdownWithRetry (attempt " + attempt + ")", e);
            e.printStackTrace();
        }
    }

    private void setupDistanceSelector() {
        editDistance.setText(String.valueOf(distance));

        // Increment button
        buttonIncrease.setOnClickListener(v -> {
            int currentValue = getDistanceValue();
            currentValue++;
            distance = currentValue;
            editDistance.setText(String.valueOf(currentValue));
            validateDistance();
        });

        // Decrement button
        buttonDecrease.setOnClickListener(v -> {
            int currentValue = getDistanceValue();
            currentValue--;
            distance = currentValue;
            editDistance.setText(String.valueOf(currentValue));
            validateDistance();
        });

        editDistance.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Clear error when user starts typing
                if (textDistanceError != null && textDistanceError.getVisibility() == View.VISIBLE) {
                    textDistanceError.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                validateDistance();
            }
        });
        
        // Ensure distance field only shows number keyboard and closes properly
        editDistance.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // Set flag when distance field gains focus
                isDistanceFieldInUse = true;
                Log.d("SearchFragment", "Distance field gained focus - set flag to prevent letter keyboard");
            } else {
                // Hide keyboard when distance field loses focus
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    if (editDistance.getWindowToken() != null) {
                        imm.hideSoftInputFromWindow(editDistance.getWindowToken(), 0);
                    }
                    // Also hide any keyboard that might be showing (in case focus moved to another field)
                    View currentFocus = requireActivity().getCurrentFocus();
                    if (currentFocus != null && currentFocus.getWindowToken() != null) {
                        imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                    }
                    Log.d("SearchFragment", "Hid keyboard when distance field lost focus");
                }
                // Clear focus to prevent any keyboard from appearing
                editDistance.clearFocus();
                // Prevent keyword field from getting focus automatically
                if (editKeyword != null && editKeyword.hasFocus()) {
                    editKeyword.clearFocus();
                    Log.d("SearchFragment", "Cleared keyword field focus to prevent letter keyboard");
                }
                // Reset flag after a delay to allow normal keyword field behavior
                editDistance.postDelayed(() -> {
                    isDistanceFieldInUse = false;
                    Log.d("SearchFragment", "Reset distance field flag");
                }, 300);
            }
        });
        
        // Ensure inputType is set to number only (in case it gets changed)
        editDistance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
    }

    private void setupKeywordInput() {
        Log.d("SearchFragment", "=== setupKeywordInput START ===");
        
        // Initialize keyword suggestions adapter
        keywordSuggestions = new ArrayList<>();
        keywordAdapter = new ArrayAdapter<String>(
            requireContext(),
            R.layout.dropdown_item_suggestion,
            R.id.textSuggestion,
            keywordSuggestions
        ) {
            @Override
            public int getCount() {
                int count = super.getCount();
                Log.d("SearchFragment", "Adapter.getCount() called, returning: " + count);
                return count;
            }

            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view;
                if (convertView == null) {
                    view = LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item_suggestion, parent, false);
                } else {
                    view = convertView;
                }
                TextView textView = view.findViewById(R.id.textSuggestion);
                if (textView != null && getItem(position) != null) {
                    textView.setText(getItem(position));
                }
                return view;
            }
        };
        Log.d("SearchFragment", "Keyword adapter created. Initial size: " + keywordSuggestions.size());
        Log.d("SearchFragment", "Adapter initial count: " + keywordAdapter.getCount());
        
        editKeyword.setAdapter(keywordAdapter);
        editKeyword.setThreshold(1); // Show suggestions after 1 character
        
        // Set dropdown width to match input field width (padding will be visible within items)
        editKeyword.post(() -> {
            if (editKeyword != null) {
                int width = editKeyword.getWidth();
                if (width > 0) {
                    editKeyword.setDropDownWidth(width);
                    Log.d("SearchFragment", "Dropdown width set to: " + width);
                }
            }
        });
        
        Log.d("SearchFragment", "Adapter set on editKeyword. Threshold: " + editKeyword.getThreshold());
        Log.d("SearchFragment", "editKeyword.getAdapter() == keywordAdapter: " + (editKeyword.getAdapter() == keywordAdapter));
        
        editKeyword.setOnItemClickListener((parent, view, position, id) -> {
            String selectedSuggestion = (String) parent.getItemAtPosition(position);
            Log.d("SearchFragment", "*** Suggestion selected: " + selectedSuggestion + " at position " + position + " ***");
            editKeyword.setText(selectedSuggestion);
            editKeyword.setSelection(selectedSuggestion.length());
            keyword = selectedSuggestion;
            
            // Hide dropdown first
            editKeyword.dismissDropDown();
            
            // Hide keyboard when selection is made
            editKeyword.post(() -> {
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null && editKeyword.getWindowToken() != null) {
                    // Try multiple methods to ensure keyboard closes
                    imm.hideSoftInputFromWindow(editKeyword.getWindowToken(), 0);
                    imm.hideSoftInputFromWindow(editKeyword.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
                    Log.d("SearchFragment", "Keyboard hide requested (multiple methods)");
                }
                editKeyword.clearFocus();
                Log.d("SearchFragment", "Dropdown dismissed and keyboard hidden after selection");
            });
        });

        editKeyword.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d("SearchFragment", "=== Keyword field FOCUS CHANGED: " + hasFocus + " ===");
            Log.d("SearchFragment", "Fragment isAdded: " + isAdded());
            Log.d("SearchFragment", "Fragment isResumed: " + isResumed());
            Log.d("SearchFragment", "editKeyword.getWindowToken: " + (editKeyword.getWindowToken() != null ? "NOT NULL" : "NULL"));
            Log.d("SearchFragment", "isFavoriteClickInProgress: " + isFavoriteClickInProgress);
            Log.d("SearchFragment", "isCategorySwitchInProgress: " + isCategorySwitchInProgress);
            
            if (hasFocus) {
                // Skip showing keyboard/dropdown if favorite click or category switch is in progress
                if (isFavoriteClickInProgress || isCategorySwitchInProgress) {
                    Log.d("SearchFragment", "Favorite click or category switch in progress, skipping keyboard/dropdown show");
                    return;
                }
                // Skip showing keyboard if distance field was just used (prevents letter keyboard from appearing)
                if (isDistanceFieldInUse) {
                    Log.d("SearchFragment", "Distance field was just used, skipping keyword keyboard show");
                    editKeyword.clearFocus();
                    return;
                }
                // Show keyboard when field is focused (use toggleSoftInput for emulator compatibility)
                editKeyword.postDelayed(() -> {
                    Log.d("SearchFragment", "=== POST (focus change, 150ms delay): Attempting to show keyboard ===");
                    Log.d("SearchFragment", "editKeyword is null: " + (editKeyword == null));
                    Log.d("SearchFragment", "hasFocus: " + (editKeyword != null ? editKeyword.hasFocus() : "N/A"));
                    Log.d("SearchFragment", "isAdded: " + isAdded());
                    Log.d("SearchFragment", "getWindowToken: " + (editKeyword != null && editKeyword.getWindowToken() != null ? "NOT NULL" : "NULL"));
                    Log.d("SearchFragment", "isCategorySwitchInProgress: " + isCategorySwitchInProgress);
                    
                    // Check flag again in postDelayed callback
                    if (isCategorySwitchInProgress || isFavoriteClickInProgress) {
                        Log.d("SearchFragment", "Skipping keyboard show - category switch or favorite click in progress");
                        return;
                    }
                    
                    if (editKeyword != null && editKeyword.hasFocus() && isAdded()) {
                        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        Log.d("SearchFragment", "InputMethodManager is null: " + (imm == null));
                        
                        if (imm != null) {
                            // Try showSoftInput first
                            boolean shown1 = imm.showSoftInput(editKeyword, InputMethodManager.SHOW_IMPLICIT);
                            Log.d("SearchFragment", "*** showSoftInput(SHOW_IMPLICIT) on focus change, result: " + shown1 + " ***");
                            
                            // Only use toggleSoftInput as fallback if showSoftInput failed
                            if (!shown1) {
                                Log.d("SearchFragment", "showSoftInput failed on focus change, trying toggleSoftInput as fallback");
                                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                                Log.d("SearchFragment", "*** toggleSoftInput(SHOW_FORCED) on focus change called as fallback ***");
                            } else {
                                Log.d("SearchFragment", "Keyboard shown successfully with showSoftInput on focus change, skipping toggleSoftInput");
                            }
                            Log.d("SearchFragment", "Keyboard methods called on focus change");
                        } else {
                            Log.e("SearchFragment", "InputMethodManager is NULL on focus change!");
                        }
                    } else {
                        Log.w("SearchFragment", "Cannot show keyboard on focus change - conditions not met");
                        Log.w("SearchFragment", "  - editKeyword null: " + (editKeyword == null));
                        Log.w("SearchFragment", "  - hasFocus: " + (editKeyword != null ? editKeyword.hasFocus() : "N/A"));
                        Log.w("SearchFragment", "  - isAdded: " + isAdded());
                    }
                }, 150);
                
                // Show dropdown if there are suggestions
                if (!keywordSuggestions.isEmpty() && !isCategorySwitchInProgress && !isFavoriteClickInProgress) {
                    Log.d("SearchFragment", "Field focused with " + keywordSuggestions.size() + " suggestions. Attempting to show dropdown...");
                    editKeyword.post(() -> {
                        if (editKeyword.hasFocus() && !keywordSuggestions.isEmpty() && !isCategorySwitchInProgress && !isFavoriteClickInProgress) {
                            Log.d("SearchFragment", "POST: Showing dropdown on focus. Suggestions count: " + keywordSuggestions.size());
                            editKeyword.showDropDown();
                            Log.d("SearchFragment", "POST: showDropDown() called. Is showing: " + editKeyword.isPopupShowing());
                        }
                    });
                }
            }
        });
        
        // Also handle click to ensure keyboard shows (more reliable on emulators)
        editKeyword.setOnClickListener(v -> {
            Log.d("SearchFragment", "=== Keyword field CLICKED ===");
            Log.d("SearchFragment", "Fragment isAdded: " + isAdded());
            Log.d("SearchFragment", "Fragment isResumed: " + isResumed());
            Log.d("SearchFragment", "editKeyword.hasFocus before requestFocus: " + editKeyword.hasFocus());
            Log.d("SearchFragment", "editKeyword.getWindowToken: " + (editKeyword.getWindowToken() != null ? "NOT NULL" : "NULL"));
            
            boolean focused = editKeyword.requestFocus();
            Log.d("SearchFragment", "requestFocus() on click, result: " + focused);
            Log.d("SearchFragment", "hasFocus() after requestFocus: " + editKeyword.hasFocus());
            
            editKeyword.postDelayed(() -> {
                Log.d("SearchFragment", "=== POST (click, 100ms delay): Attempting to show keyboard ===");
                Log.d("SearchFragment", "hasFocus: " + editKeyword.hasFocus());
                Log.d("SearchFragment", "getWindowToken: " + (editKeyword.getWindowToken() != null ? "NOT NULL" : "NULL"));
                
                if (editKeyword.hasFocus()) {
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    Log.d("SearchFragment", "InputMethodManager is null: " + (imm == null));
                    
                    if (imm != null) {
                        // Try showSoftInput first
                        boolean shown1 = imm.showSoftInput(editKeyword, InputMethodManager.SHOW_IMPLICIT);
                        Log.d("SearchFragment", "*** showSoftInput(SHOW_IMPLICIT) on click, result: " + shown1 + " ***");
                        
                        // Only use toggleSoftInput as fallback if showSoftInput failed
                        if (!shown1) {
                            Log.d("SearchFragment", "showSoftInput failed on click, trying toggleSoftInput as fallback");
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                            Log.d("SearchFragment", "*** toggleSoftInput(SHOW_FORCED) on click called as fallback ***");
                        } else {
                            Log.d("SearchFragment", "Keyboard shown successfully with showSoftInput on click, skipping toggleSoftInput");
                        }
                        Log.d("SearchFragment", "Keyboard methods called on click");
                    } else {
                        Log.e("SearchFragment", "InputMethodManager is NULL on click!");
                    }
                } else {
                    Log.w("SearchFragment", "Field does not have focus, cannot show keyboard");
                }
            }, 100);
        });

        editKeyword.setOnEditorActionListener((v, actionId, event) -> {
            Log.d("SearchFragment", "Editor action: " + actionId);
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                editKeyword.dismissDropDown();
                performSearch();
                return true;
            }
            return false;
        });

            editKeyword.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    Log.d("SearchFragment", "Keyword beforeTextChanged: '" + s + "' (start=" + start + ", count=" + count + ", after=" + after + ")");
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    Log.d("SearchFragment", "*** Keyword onTextChanged: '" + s + "' (length=" + s.length() + ") ***");

                    // Ensure keyboard is shown when user starts typing
                    if (s.length() > 0 && editKeyword.hasFocus()) {
                        editKeyword.post(() -> {
                            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                Log.d("SearchFragment", "User typing detected, ensuring keyboard is shown");
                                boolean shown = imm.showSoftInput(editKeyword, InputMethodManager.SHOW_IMPLICIT);
                                if (!shown) {
                                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                                    Log.d("SearchFragment", "Forced keyboard show on typing");
                                }
                            }
                        });
                    }

                    // Clear error when user starts typing
                    if (textKeywordError != null && textKeywordError.getVisibility() == View.VISIBLE) {
                        textKeywordError.setVisibility(View.GONE);
                        Log.d("SearchFragment", "Cleared keyword error message");
                    }
                    // Reset search icon color when user starts typing
                    if (imageSearchIcon != null && s.length() > 0) {
                        imageSearchIcon.setColorFilter(MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, 0));
                    }
                    keyword = s.toString().trim();
                    Log.d("SearchFragment", "Updated keyword variable: '" + keyword + "'");

                    // Cancel previous debounce runnable
                    if (debounceRunnable != null) {
                        debounceHandler.removeCallbacks(debounceRunnable);
                        Log.d("SearchFragment", "Cancelled previous debounce runnable");
                    }

                    // If keyword is empty, hide dropdown and show favorites
                    if (keyword.isEmpty()) {
                        Log.d("SearchFragment", "Keyword is empty. Dismissing dropdown, clearing suggestions, and showing favorites");
                        editKeyword.dismissDropDown();
                        keywordSuggestions.clear();
                        keywordAdapter.notifyDataSetChanged();
                        // Clear search results when keyword is empty
                        searchResults.clear();
                        allSearchResults.clear();
                        hasPerformedSearch = false;
                        // Update display to show only favorites
                        updateDisplayResults();
                        // Show favorites if available, otherwise show no events
                        if (!displayResults.isEmpty()) {
                            if (resultsAdapter != null) {
                                resultsAdapter.notifyDataSetChanged();
                            }
                            showResultsState();
                        } else {
                            showNoEventsState();
                        }
                        Log.d("SearchFragment", "Dropdown dismissed, suggestions cleared, showing favorites");
                        return;
                    }

                    // Debounce API call
                    Log.d("SearchFragment", "Scheduling debounced API call for keyword: '" + keyword + "' (delay: " + DEBOUNCE_DELAY_MS + "ms)");
                    debounceRunnable = () -> {
                        Log.d("SearchFragment", "*** Debounce runnable executed for keyword: '" + keyword + "' ***");
                        fetchKeywordSuggestions(keyword);
                    };
                    debounceHandler.postDelayed(debounceRunnable, DEBOUNCE_DELAY_MS);
                    Log.d("SearchFragment", "Debounce runnable scheduled");
                }

                @Override
                public void afterTextChanged(Editable s) {
                    Log.d("SearchFragment", "Keyword afterTextChanged: '" + s + "'");
                }
            });
        
        Log.d("SearchFragment", "=== setupKeywordInput END ===");
    }

    private void fetchKeywordSuggestions(String keywordText) {
        Log.d("SearchFragment", "=== fetchKeywordSuggestions START ===");
        Log.d("SearchFragment", "Input keywordText: '" + keywordText + "'");
        
        if (keywordText == null || keywordText.trim().isEmpty()) {
            Log.d("SearchFragment", "Keyword is null or empty. Clearing suggestions");
            keywordSuggestions.clear();
            keywordAdapter.notifyDataSetChanged();
            return;
        }

        String url = BASE_URL + "/api/suggest?keyword=" + java.net.URLEncoder.encode(keywordText.trim(), java.nio.charset.StandardCharsets.UTF_8);
        Log.d("SearchFragment", "*** API Request URL: " + url + " ***");
        Log.d("SearchFragment", "RequestQueue is null: " + (requestQueue == null));
        Log.d("SearchFragment", "editKeyword is null: " + (editKeyword == null));
        Log.d("SearchFragment", "editKeyword has focus: " + (editKeyword != null && editKeyword.hasFocus()));
        Log.d("SearchFragment", "editKeyword is showing popup: " + (editKeyword != null && editKeyword.isPopupShowing()));

        // First, use StringRequest to see the raw response
        StringRequest stringRequest = new StringRequest(
            Request.Method.GET,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    Log.d("SearchFragment", "*** Raw API Response received ***");
                    Log.d("SearchFragment", "Response length: " + response.length());
                    Log.d("SearchFragment", "Response first 500 chars: " + (response.length() > 500 ? response.substring(0, 500) : response));
                    
                    // Check if response is HTML
                    if (response.trim().startsWith("<!--") || response.trim().startsWith("<")) {
                        Log.e("SearchFragment", "*** ERROR: Server returned HTML instead of JSON ***");
                        Log.e("SearchFragment", "This usually means the endpoint doesn't exist or returned an error page");
                        keywordSuggestions.clear();
                        keywordAdapter.notifyDataSetChanged();
                        return;
                    }
                    
                    // Try to parse as JSON
                    try {
                        JSONObject jsonResponse = new JSONObject(response);
                        Log.d("SearchFragment", "Successfully parsed as JSON: " + jsonResponse.toString());
                        
                        keywordSuggestions.clear();
                        JSONArray suggestionsArray = jsonResponse.getJSONArray("suggestions");
                        Log.d("SearchFragment", "Suggestions array length: " + suggestionsArray.length());
                        
                        for (int i = 0; i < suggestionsArray.length(); i++) {
                            String suggestion = suggestionsArray.getString(i);
                            keywordSuggestions.add(suggestion);
                            Log.d("SearchFragment", "Added suggestion [" + i + "]: " + suggestion);
                        }
                        
                        Log.d("SearchFragment", "Total suggestions in list: " + keywordSuggestions.size());
                        Log.d("SearchFragment", "Adapter count before notify: " + keywordAdapter.getCount());
                        
                        // Recreate adapter with updated list to ensure it's properly linked
                        keywordAdapter = new ArrayAdapter<>(
                            requireContext(),
                            R.layout.dropdown_item_suggestion,
                            R.id.textSuggestion,
                            keywordSuggestions
                        ) {
                            @NonNull
                            @Override
                            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                View view;
                                if (convertView == null) {
                                    view = LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item_suggestion, parent, false);
                                } else {
                                    view = convertView;
                                }
                                TextView textView = view.findViewById(R.id.textSuggestion);
                                if (textView != null && getItem(position) != null) {
                                    textView.setText(getItem(position));
                                }
                                return view;
                            }
                        };
                        editKeyword.setAdapter(keywordAdapter);
                        
                        // Ensure dropdown width is set correctly
                        editKeyword.post(() -> {
                            if (editKeyword != null) {
                                int width = editKeyword.getWidth();
                                if (width > 0) {
                                    editKeyword.setDropDownWidth(width);
                                    Log.d("SearchFragment", "Dropdown width set to: " + width);
                                }
                            }
                        });
                        
                        Log.d("SearchFragment", "Adapter recreated with " + keywordSuggestions.size() + " items. New adapter count: " + keywordAdapter.getCount());
                        
                        keywordAdapter.notifyDataSetChanged();
                        
                        Log.d("SearchFragment", "Adapter count after notify: " + keywordAdapter.getCount());
                        Log.d("SearchFragment", "editKeyword is null: " + (editKeyword == null));
                        Log.d("SearchFragment", "editKeyword has focus: " + (editKeyword != null && editKeyword.hasFocus()));
                        Log.d("SearchFragment", "editKeyword is showing popup: " + (editKeyword != null && editKeyword.isPopupShowing()));
                        Log.d("SearchFragment", "editKeyword threshold: " + (editKeyword != null ? editKeyword.getThreshold() : "N/A"));
                        Log.d("SearchFragment", "editKeyword adapter is null: " + (editKeyword != null && editKeyword.getAdapter() == null));

                        // Show dropdown if there are suggestions and field has focus
                        if (!keywordSuggestions.isEmpty()) {
                            Log.d("SearchFragment", "*** Attempting to show dropdown ***");
                            Log.d("SearchFragment", "Suggestions not empty: " + !keywordSuggestions.isEmpty());
                            Log.d("SearchFragment", "Field has focus: " + (editKeyword != null && editKeyword.hasFocus()));
                            
                            if (editKeyword != null) {
                                // Try multiple approaches to show dropdown
                                editKeyword.post(() -> {
                                    Log.d("SearchFragment", "*** POST 1: Attempting to show dropdown ***");
                                    Log.d("SearchFragment", "POST 1 - Has focus: " + editKeyword.hasFocus());
                                    Log.d("SearchFragment", "POST 1 - Is popup showing: " + editKeyword.isPopupShowing());
                                    Log.d("SearchFragment", "POST 1 - Adapter count: " + (editKeyword.getAdapter() != null ? editKeyword.getAdapter().getCount() : "NULL"));
                                    
                                    if (editKeyword.hasFocus() && !keywordSuggestions.isEmpty()) {
                                        Log.d("SearchFragment", "POST 1 - Calling showDropDown()");
                                        editKeyword.showDropDown();
                                        Log.d("SearchFragment", "POST 1 - showDropDown() called. Is showing: " + editKeyword.isPopupShowing());
                                    } else {
                                        Log.d("SearchFragment", "POST 1 - Conditions not met. Has focus: " + editKeyword.hasFocus() + ", Suggestions empty: " + keywordSuggestions.isEmpty());
                                    }
                                });
                                
                                editKeyword.postDelayed(() -> {
                                    Log.d("SearchFragment", "*** POST 2 (100ms delay): Attempting to show dropdown ***");
                                    Log.d("SearchFragment", "POST 2 - Has focus: " + editKeyword.hasFocus());
                                    Log.d("SearchFragment", "POST 2 - Is popup showing: " + editKeyword.isPopupShowing());
                                    
                                    if (editKeyword.hasFocus() && !keywordSuggestions.isEmpty() && !editKeyword.isPopupShowing()) {
                                        Log.d("SearchFragment", "POST 2 - Calling showDropDown()");
                                        editKeyword.showDropDown();
                                        Log.d("SearchFragment", "POST 2 - showDropDown() called. Is showing: " + editKeyword.isPopupShowing());
                                    }
                                }, 100);
                                
                                editKeyword.postDelayed(() -> {
                                    Log.d("SearchFragment", "*** POST 3 (200ms delay): Attempting to show dropdown ***");
                                    Log.d("SearchFragment", "POST 3 - Has focus: " + editKeyword.hasFocus());
                                    Log.d("SearchFragment", "POST 3 - Is popup showing: " + editKeyword.isPopupShowing());
                                    
                                    if (editKeyword.hasFocus() && !keywordSuggestions.isEmpty() && !editKeyword.isPopupShowing()) {
                                        Log.d("SearchFragment", "POST 3 - Calling showDropDown()");
                                        editKeyword.showDropDown();
                                        Log.d("SearchFragment", "POST 3 - showDropDown() called. Is showing: " + editKeyword.isPopupShowing());
                                    }
                                }, 200);
                            } else {
                                Log.e("SearchFragment", "editKeyword is NULL! Cannot show dropdown");
                            }
                        } else {
                            Log.d("SearchFragment", "No suggestions to show. Hiding dropdown");
                            if (editKeyword != null) {
                                editKeyword.dismissDropDown();
                            }
                        }
                    } catch (JSONException e) {
                        Log.e("SearchFragment", "*** ERROR parsing suggestions JSON ***", e);
                        Log.e("SearchFragment", "Exception message: " + e.getMessage());
                        Log.e("SearchFragment", "Response that failed to parse: " + response);
                        e.printStackTrace();
                        keywordSuggestions.clear();
                        keywordAdapter.notifyDataSetChanged();
                    }
                    Log.d("SearchFragment", "=== fetchKeywordSuggestions onResponse END ===");
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("SearchFragment", "*** ERROR fetching suggestions ***");
                    Log.e("SearchFragment", "Error message: " + (error.getMessage() != null ? error.getMessage() : "null"));
                    Log.e("SearchFragment", "Error network response: " + (error.networkResponse != null ? error.networkResponse.statusCode : "null"));
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        String errorBody = new String(error.networkResponse.data);
                        Log.e("SearchFragment", "Error response data length: " + errorBody.length());
                        Log.e("SearchFragment", "Error response data (first 500 chars): " + (errorBody.length() > 500 ? errorBody.substring(0, 500) : errorBody));
                    }
                    if (error.getCause() != null) {
                        Log.e("SearchFragment", "Error cause: " + error.getCause().getMessage());
                    }
                    error.printStackTrace();
                    keywordSuggestions.clear();
                    keywordAdapter.notifyDataSetChanged();
                    Log.d("SearchFragment", "=== fetchKeywordSuggestions onErrorResponse END ===");
                }
            }
        );

        Log.d("SearchFragment", "Adding request to queue");
        requestQueue.add(stringRequest);
        Log.d("SearchFragment", "Request added to queue. Request count: " + requestQueue.getSequenceNumber());
        Log.d("SearchFragment", "=== fetchKeywordSuggestions END (request queued) ===");
    }

    private void setupBackButton() {
        Log.d("SearchFragment", "Setting up back button");
        buttonBack.setOnClickListener(v -> {
            Log.d("SearchFragment", "Back button clicked");
            try {
                if (getActivity() != null && getActivity().getSupportFragmentManager() != null) {
                    int backStackCount = getActivity().getSupportFragmentManager().getBackStackEntryCount();
                    Log.d("SearchFragment", "Back stack count: " + backStackCount);
                    // Check if there are fragments in the back stack
                    if (backStackCount > 0) {
                        // Pop the back stack to return to previous fragment (HomeFragment)
                        Log.d("SearchFragment", "Popping back stack");
                        getActivity().getSupportFragmentManager().popBackStack();
                    } else {
                        // If no back stack, navigate to HomeFragment
                        Log.d("SearchFragment", "No back stack, replacing with HomeFragment");
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).replaceFragment(new HomeFragment());
                        }
                    }
                } else {
                    Log.w("SearchFragment", "Activity or FragmentManager is null");
                }
            } catch (Exception e) {
                Log.e("SearchFragment", "Error handling back button", e);
            }
        });
    }

    private void setupSearchIcon(View view) {
        imageSearchIcon.setOnClickListener(v -> performSearch());
    }

    private void performSearch() {
        // Hide keyboard when search is clicked
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // Hide keyboard from keyword field
            if (editKeyword != null && editKeyword.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(editKeyword.getWindowToken(), 0);
                editKeyword.clearFocus();
            }
            // Hide keyboard from location field
            if (autoCompleteLocation != null && autoCompleteLocation.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(autoCompleteLocation.getWindowToken(), 0);
                autoCompleteLocation.clearFocus();
            }
            // Hide keyboard from distance field
            if (editDistance != null && editDistance.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(editDistance.getWindowToken(), 0);
                editDistance.clearFocus();
            }
            // Also hide from current focus as fallback
            View currentFocus = requireActivity().getCurrentFocus();
            if (currentFocus != null && currentFocus.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
            Log.d("SearchFragment", "Keyboard hidden after search button click");
        }
        
        // Validate inputs
        if (!validateKeyword()) {
            return;
        }
        if (!validateDistance()) {
            return;
        }

        // Get form values
        keyword = editKeyword.getText().toString().trim();
        locationText = autoCompleteLocation.getText().toString();
        distance = getDistanceValue();

        // Get latitude/longitude based on location selection
        if ("Current Location".equals(locationText)) {
            // Use ipinfo API to get current location
            fetchCurrentLocation(() -> {
                // After getting location, perform search
                performSearchWithLocation();
            });
        } else {
            // Geocode selected location to get lat/lng
            geocodeLocationForSearch(locationText, () -> {
                // After geocoding, perform search
                performSearchWithLocation();
            });
        }

        // Show loading state
        showLoadingState();
    }
    
    private void performSearchWithLocation() {
        Log.d("SearchFragment", "=== performSearchWithLocation START ===");
        hasPerformedSearch = true; // Mark that a search has been performed
        Log.d("SearchFragment", String.format("Search params: keyword='%s', lat=%.4f, lng=%.4f, distance=%d, category='%s'", 
            keyword, latitude, longitude, distance, selectedCategory));
        
        // Build API URL
        String segmentId = getSegmentId(selectedCategory);
        String url = BASE_URL + "/api/events" +
            "?keyword=" + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8) +
            "&lat=" + latitude +
            "&lon=" + longitude +
            "&radius=" + distance +
            "&unit=miles";
        
        if (segmentId != null && !segmentId.isEmpty() && !selectedCategory.equals("All")) {
            url += "&segmentId=" + segmentId;
        }
        
        Log.d("SearchFragment", "Search API URL: " + url);
        
        JsonObjectRequest jsonRequest = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d("SearchFragment", "=== Search API Response received ===");
                    try {
                        parseSearchResults(response);
                    } catch (JSONException e) {
                        Log.e("SearchFragment", "Error parsing search results", e);
                        showNoEventsState();
                        // Clear category switch flag on parsing error
                        isCategorySwitchInProgress = false;
                        Log.d("SearchFragment", "Category switch flag cleared after parsing error");
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("SearchFragment", "=== Search API Error ===", error);
                    showNoEventsState();
                    // Clear category switch flag on error
                    isCategorySwitchInProgress = false;
                    Log.d("SearchFragment", "Category switch flag cleared after API error");
                }
            }
        );
        
        requestQueue.add(jsonRequest);
        Log.d("SearchFragment", "=== performSearchWithLocation END (request queued) ===");
    }
    
    private String getSegmentId(String category) {
        // Map category to Ticketmaster segment ID
        switch (category) {
            case "Music":
                return "KZFzniwnSyZfZ7v7nJ"; // Music
            case "Sports":
                return "KZFzniwnSyZfZ7v7nE"; // Sports
            case "Arts & Theatre":
                return "KZFzniwnSyZfZ7v7na"; // Arts & Theatre
            case "Film":
                return "KZFzniwnSyZfZ7v7nn"; // Film
            case "Miscellaneous":
                return "KZFzniwnSyZfZ7v7n1"; // Miscellaneous
            default:
                return null; // All categories
        }
    }
    
    private void parseSearchResults(JSONObject response) throws JSONException {
        Log.d("SearchFragment", "Parsing search results...");
        
        // Don't clear likedEvents - they persist across searches
        searchResults.clear();
        
        JSONObject embedded = response.optJSONObject("_embedded");
        if (embedded == null) {
            Log.d("SearchFragment", "No _embedded object in response");
            showNoEventsState();
            return;
        }
        
        JSONArray eventsArray = embedded.optJSONArray("events");
        if (eventsArray == null || eventsArray.length() == 0) {
            Log.d("SearchFragment", "No events in response");
            showNoEventsState();
            return;
        }
        
        Log.d("SearchFragment", "Found " + eventsArray.length() + " events");
        
        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject eventObj = eventsArray.getJSONObject(i);
            
            String id = eventObj.getString("id");
            String name = eventObj.getString("name");
            
            // Get venue name
            String venue = "Unknown Venue";
            JSONObject embeddedObj = eventObj.optJSONObject("_embedded");
            if (embeddedObj != null) {
                JSONArray venues = embeddedObj.optJSONArray("venues");
                if (venues != null && venues.length() > 0) {
                    venue = venues.getJSONObject(0).getString("name");
                }
            }
            
            // Get date and time
            JSONObject dates = eventObj.optJSONObject("dates");
            String date = "";
            String dateFormatted = ""; // Formatted date for display
            String time = "";
            String time24 = ""; // Store original 24-hour format for sorting
            if (dates != null) {
                JSONObject start = dates.optJSONObject("start");
                if (start != null) {
                    date = start.optString("localDate", "");
                    dateFormatted = formatDate(date);
                    time24 = start.optString("localTime", "");
                    time = formatTime(time24);
                }
            }
            
            // Get image URL
            String imageUrl = "";
            JSONArray images = eventObj.optJSONArray("images");
            if (images != null && images.length() > 0) {
                // Try to get the largest image (usually the last one or one with width/height)
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
                    // Fallback to first image if no width info
                    imageUrl = images.getJSONObject(0).optString("url", "");
                }
                Log.d("SearchFragment", "Image URL for " + name + ": " + imageUrl);
            } else {
                Log.w("SearchFragment", "No images found for event: " + name);
            }
            
            // Get segment/category
            String segment = "Unknown";
            JSONArray classifications = eventObj.optJSONArray("classifications");
            if (classifications != null && classifications.length() > 0) {
                JSONObject classification = classifications.getJSONObject(0);
                JSONObject segmentObj = classification.optJSONObject("segment");
                if (segmentObj != null) {
                    segment = segmentObj.optString("name", "Unknown");
                }
            }
            
            Event event = new Event(id, name, venue, date, dateFormatted, time, time24, imageUrl, segment);
            searchResults.add(event);
            
            Log.d("SearchFragment", "Added event: " + name);
        }
        
        // Sort results by date and time (using 24-hour format for accurate time sorting)
        Collections.sort(searchResults, new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                // Compare by date first (format: YYYY-MM-DD, so string comparison works)
                int dateCompare = e1.date.compareTo(e2.date);
                if (dateCompare != 0) {
                    return dateCompare;
                }
                // If dates are equal, compare by time (using 24-hour format: HH:mm:ss)
                String time1 = e1.time24 != null ? e1.time24 : "";
                String time2 = e2.time24 != null ? e2.time24 : "";
                return time1.compareTo(time2);
            }
        });
        
        Log.d("SearchFragment", "Sorted " + searchResults.size() + " events by date and time");
        
        // Store all results (unfiltered) for category tab filtering
        allSearchResults.clear();
        allSearchResults.addAll(searchResults);
        
        // Check if any events match the selected category filter
        if (selectedCategory != null && !selectedCategory.equals("All")) {
            List<Event> filtered = new ArrayList<>();
            for (Event event : allSearchResults) {
                if (event.segment != null && event.segment.equals(selectedCategory)) {
                    filtered.add(event);
                }
            }
            searchResults.clear();
            searchResults.addAll(filtered);
        }
        
        // DO NOT filter out favorites - search results must show ALL API events
        // Favorite status is only used for the star icon, not for filtering
        
        // Update display results (will show only search results since keyword has text)
        updateDisplayResults();
        
        // Show results if we have search results
        if (displayResults.isEmpty()) {
            // No search results - show "No events found" (don't show favorites when searching)
            showNoEventsState();
            // Clear category switch flag
            isCategorySwitchInProgress = false;
            Log.d("SearchFragment", "Category switch flag cleared after no results");
        } else {
            // Ensure image request queue is set on adapter (in case it wasn't set during initialization)
            if (resultsAdapter != null && requestQueue != null) {
                resultsAdapter.setImageRequestQueue(requestQueue);
                Log.d("SearchFragment", "Image request queue set on adapter after parsing results");
            }
            // Load favorites and sync favorite state for all events
            loadFavoritesAndSync();
            // Hide keyboard and dropdown after search results are displayed
            showResultsState(true);
            // Clear category switch flag after search completes
            editKeyword.postDelayed(() -> {
                isCategorySwitchInProgress = false;
                Log.d("SearchFragment", "Category switch flag cleared after search results");
            }, 500);
        }
    }
    
    private void updateDisplayResults() {
        displayResults.clear();
        
        // Get current keyword from the input field
        String currentKeyword = editKeyword != null ? editKeyword.getText().toString().trim() : keyword;
        
        // If keyword has text, show ONLY search results (no favorites)
        if (currentKeyword != null && !currentKeyword.isEmpty()) {
            displayResults.addAll(searchResults);
            Log.d("SearchFragment", "Keyword has text - showing ONLY search results: " + searchResults.size() + " events (no favorites)");
        } else {
            // If keyword is empty, show ONLY favorites (no search results)
            displayResults.addAll(likedEvents);
            Log.d("SearchFragment", "Keyword is empty - showing ONLY favorites: " + likedEvents.size() + " events (no search results)");
        }
    }
    
    private void loadFavoritesAndSync() {
        String url = BASE_URL + "/api/favorites";
        Log.d("SearchFragment", "Loading favorites to sync state");
        
        StringRequest request = new StringRequest(
            Request.Method.GET,
            url,
            response -> {
                try {
                    JSONArray favorites = new JSONArray(response);
                    Set<String> favoriteIds = new HashSet<>();
                    for (int i = 0; i < favorites.length(); i++) {
                        JSONObject favorite = favorites.getJSONObject(i);
                        String favoriteId = favorite.optString("id", "");
                        if (!favoriteId.isEmpty()) {
                            favoriteIds.add(favoriteId);
                        }
                    }
                    
                    // Update isFavorite state for all events in searchResults and allSearchResults
                    for (Event event : searchResults) {
                        event.isFavorite = favoriteIds.contains(event.id);
                    }
                    for (Event event : allSearchResults) {
                        event.isFavorite = favoriteIds.contains(event.id);
                    }
                    
                    // Notify adapter to update UI
                    if (resultsAdapter != null) {
                        resultsAdapter.notifyDataSetChanged();
                    }
                    Log.d("SearchFragment", "Synced favorite state for " + favoriteIds.size() + " favorites");
                } catch (JSONException e) {
                    Log.e("SearchFragment", "Error parsing favorites response", e);
                }
            },
            error -> {
                Log.e("SearchFragment", "Error loading favorites", error);
            }
        );
        
        if (requestQueue != null) {
            requestQueue.add(request);
        }
    }
    
    private void checkAndAddNewFavorites() {
        String url = BASE_URL + "/api/favorites";
        Log.d("SearchFragment", "=== checkAndAddNewFavorites START ===");
        Log.d("SearchFragment", "Current likedEvents size: " + likedEvents.size());
        Log.d("SearchFragment", "Current searchResults size: " + searchResults.size());
        
        StringRequest request = new StringRequest(
            Request.Method.GET,
            url,
            response -> {
                try {
                    JSONArray favorites = new JSONArray(response);
                    Log.d("SearchFragment", "Received " + favorites.length() + " favorites from backend");
                    
                    // Collect backend favorite IDs
                    Set<String> backendFavoriteIds = new HashSet<>();
                    for (int i = 0; i < favorites.length(); i++) {
                        JSONObject favorite = favorites.getJSONObject(i);
                        String favoriteId = favorite.optString("id", "");
                        if (!favoriteId.isEmpty()) {
                            backendFavoriteIds.add(favoriteId);
                        }
                    }
                    
                    Log.d("SearchFragment", "Backend favorite IDs: " + backendFavoriteIds.size());
                    
                    // Collect all existing event IDs from likedEvents (most important)
                    Set<String> existingEventIds = new HashSet<>();
                    for (Event event : likedEvents) {
                        existingEventIds.add(event.id);
                        Log.d("SearchFragment", "Existing liked event: " + event.id + " - " + event.name);
                    }
                    
                    // Also collect from search results
                    for (Event event : searchResults) {
                        existingEventIds.add(event.id);
                    }
                    
                    Log.d("SearchFragment", "Total existing event IDs: " + existingEventIds.size());
                    
                    // REMOVE events from likedEvents that are no longer in backend
                    List<Event> eventsToRemove = new ArrayList<>();
                    for (Event event : likedEvents) {
                        if (!backendFavoriteIds.contains(event.id)) {
                            Log.d("SearchFragment", "Removing unliked event from likedEvents: " + event.id + " - " + event.name);
                            eventsToRemove.add(event);
                        }
                    }
                    for (Event event : eventsToRemove) {
                        likedEvents.remove(event);
                        Log.d("SearchFragment", "Removed from likedEvents: " + event.id);
                    }
                    
                    // Check for favorites that aren't in likedEvents or searchResults
                    int newFavoritesCount = 0;
                    for (String favoriteId : backendFavoriteIds) {
                        if (!existingEventIds.contains(favoriteId)) {
                            Log.d("SearchFragment", "Found new favorite not in likedEvents or searchResults: " + favoriteId);
                            fetchAndAddFavoriteEvent(favoriteId);
                            newFavoritesCount++;
                        } else {
                            Log.d("SearchFragment", "Favorite already exists: " + favoriteId);
                        }
                    }
                    
                    Log.d("SearchFragment", "Removed " + eventsToRemove.size() + " unliked events, Added " + newFavoritesCount + " new favorites");
                    
                    // Update display after checking (in case favorites were added or removed)
                    // Only update if keyword is empty (don't show favorites when searching)
                    String currentKeyword = editKeyword != null ? editKeyword.getText().toString().trim() : keyword;
                    if ((newFavoritesCount > 0 || !eventsToRemove.isEmpty()) && (currentKeyword == null || currentKeyword.isEmpty())) {
                        updateDisplayResults(); // Will show only favorites since keyword is empty
                        if (!displayResults.isEmpty()) {
                            showResultsState();
                        } else {
                            showNoEventsState();
                        }
                    }
                } catch (JSONException e) {
                    Log.e("SearchFragment", "Error parsing favorites response", e);
                }
                Log.d("SearchFragment", "=== checkAndAddNewFavorites END ===");
            },
            error -> {
                Log.e("SearchFragment", "Error checking for new favorites", error);
            }
        );
        
        if (requestQueue != null) {
            requestQueue.add(request);
        }
    }
    
    private void loadFavoritesAsSearchResults() {
        // Only load favorites if there are no current search results AND no search has been performed
        if (!searchResults.isEmpty() || hasPerformedSearch) {
            Log.d("SearchFragment", "Skipping loading favorites - search results exist or search was performed");
            return;
        }
        
        String url = BASE_URL + "/api/favorites";
        Log.d("SearchFragment", "Loading favorites as search results from: " + url);
        
        StringRequest request = new StringRequest(
            Request.Method.GET,
            url,
            response -> {
                try {
                    JSONArray favorites = new JSONArray(response);
                    if (favorites.length() == 0) {
                        Log.d("SearchFragment", "No favorites found");
                        return;
                    }
                    
                    Log.d("SearchFragment", "Found " + favorites.length() + " favorites to display");
                    List<Event> favoriteEvents = new ArrayList<>();
                    final int totalFavorites = favorites.length();
                    final int[] loadedCount = {0};
                    
                    if (totalFavorites == 0) {
                        return;
                    }
                    
                    for (int i = 0; i < favorites.length(); i++) {
                        JSONObject favorite = favorites.getJSONObject(i);
                        
                        String id = favorite.optString("id", "");
                        String name = favorite.optString("name", "");
                        String venue = favorite.optString("venue", "Unknown Venue");
                        String dateRaw = favorite.optString("date", "");
                        String timeRaw = favorite.optString("time", "");
                        String imageUrl = favorite.optString("image", "");
                        String segment = favorite.optString("segment", "Unknown");
                        
                        if (id.isEmpty() || name.isEmpty()) {
                            continue; // Skip invalid favorites
                        }
                        
                        // Format date and time
                        String dateFormatted = formatDate(dateRaw);
                        String timeFormatted = formatTime(timeRaw);
                        
                        // Always fetch event details to ensure we have complete date, time, and segment information
                        // This ensures favorites display category and date just like search results
                        if (!id.isEmpty()) {
                            fetchEventImageForFavorite(id, name, venue, dateRaw, dateFormatted, timeFormatted, timeRaw, segment, favoriteEvents, loadedCount, totalFavorites);
                        } else {
                            // Skip if no ID
                            Log.w("SearchFragment", "Skipping favorite with empty ID: " + name);
                        }
                    }
                    
                    // If all favorites already had images, update UI immediately
                    if (loadedCount[0] >= totalFavorites && !favoriteEvents.isEmpty()) {
                        updateFavoritesInSearchResults(favoriteEvents);
                    }
                } catch (JSONException e) {
                    Log.e("SearchFragment", "Error parsing favorites response", e);
                }
            },
            error -> {
                Log.e("SearchFragment", "Error loading favorites as search results", error);
            }
        );
        
        if (requestQueue != null) {
            requestQueue.add(request);
        }
    }
    
    private void fetchEventImageForFavorite(String eventId, String name, String venue, String dateRaw, 
                                           String dateFormatted, String timeFormatted, String timeRaw, 
                                           String segment, List<Event> favoriteEvents, int[] loadedCount, 
                                           int totalFavorites) {
        String url = BASE_URL + "/api/eventdetails?id=" + eventId;
        Log.d("SearchFragment", "Fetching event details for favorite: " + name);
        
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            response -> {
                try {
                    // Get venue (use from response if available, otherwise use passed parameter)
                    String eventVenue = venue;
                    JSONObject embedded = response.optJSONObject("_embedded");
                    if (embedded != null) {
                        JSONArray venues = embedded.optJSONArray("venues");
                        if (venues != null && venues.length() > 0) {
                            JSONObject venueObj = venues.getJSONObject(0);
                            eventVenue = venueObj.optString("name", venue);
                        }
                    }
                    
                    // Get date and time from event details (use response data, fallback to passed parameters)
                    String eventDate = dateRaw;
                    String eventTime24 = timeRaw;
                    JSONObject dates = response.optJSONObject("dates");
                    if (dates != null) {
                        JSONObject start = dates.optJSONObject("start");
                        if (start != null) {
                            String localDate = start.optString("localDate", "");
                            String localTime = start.optString("localTime", "");
                            if (!localDate.isEmpty()) {
                                eventDate = localDate;
                            }
                            if (!localTime.isEmpty()) {
                                eventTime24 = localTime;
                            }
                        }
                    }
                    
                    // Format date and time
                    String eventDateFormatted = !eventDate.isEmpty() ? formatDate(eventDate) : dateFormatted;
                    String eventTimeFormatted = !eventTime24.isEmpty() ? formatTime(eventTime24) : timeFormatted;
                    
                    // Get image URL from event details
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
                    
                    // Get segment/category from event details (use response data, fallback to passed parameter)
                    String eventSegment = segment;
                    JSONArray classifications = response.optJSONArray("classifications");
                    if (classifications != null && classifications.length() > 0) {
                        JSONObject classification = classifications.getJSONObject(0);
                        JSONObject segmentObj = classification.optJSONObject("segment");
                        if (segmentObj != null) {
                            String segmentName = segmentObj.optString("name", "");
                            if (!segmentName.isEmpty()) {
                                eventSegment = segmentName;
                            }
                        }
                    }
                    
                    // Create Event object with complete information from event details
                    Event event = new Event(eventId, name, eventVenue, eventDate, eventDateFormatted, eventTimeFormatted, eventTime24, imageUrl, eventSegment);
                    event.isFavorite = true;
                    favoriteEvents.add(event);
                    loadedCount[0]++;
                    
                    Log.d("SearchFragment", "Loaded favorite event with complete details: " + name + ", segment: " + eventSegment + ", date: " + eventDateFormatted);
                    
                    // Update UI when all favorites are loaded
                    if (loadedCount[0] >= totalFavorites) {
                        updateFavoritesInSearchResults(favoriteEvents);
                    }
                } catch (JSONException e) {
                    Log.e("SearchFragment", "Error parsing event details for favorite", e);
                    // Create event with passed parameters as fallback
                    Event event = new Event(eventId, name, venue, dateRaw, dateFormatted, timeFormatted, timeRaw, "", segment);
                    event.isFavorite = true;
                    favoriteEvents.add(event);
                    loadedCount[0]++;
                    
                    if (loadedCount[0] >= totalFavorites) {
                        updateFavoritesInSearchResults(favoriteEvents);
                    }
                }
            },
            error -> {
                Log.e("SearchFragment", "Error fetching event details for favorite image", error);
                // Create event with passed parameters as fallback
                Event event = new Event(eventId, name, venue, dateRaw, dateFormatted, timeFormatted, timeRaw, "", segment);
                event.isFavorite = true;
                favoriteEvents.add(event);
                loadedCount[0]++;
                
                if (loadedCount[0] >= totalFavorites) {
                    updateFavoritesInSearchResults(favoriteEvents);
                }
            }
        );
        
        if (requestQueue != null) {
            requestQueue.add(request);
        }
    }
    
    private void updateFavoritesInSearchResults(List<Event> favoriteEvents) {
        if (favoriteEvents.isEmpty()) {
            Log.d("SearchFragment", "updateFavoritesInSearchResults: favoriteEvents is empty, returning");
            return;
        }
        
        Log.d("SearchFragment", "=== updateFavoritesInSearchResults START ===");
        Log.d("SearchFragment", "Current likedEvents size: " + likedEvents.size());
        Log.d("SearchFragment", "New favoriteEvents size: " + favoriteEvents.size());
        
        // Merge favorites instead of clearing - preserve any events that were just added
        Set<String> existingIds = new HashSet<>();
        for (Event event : likedEvents) {
            existingIds.add(event.id);
        }
        
        // Add new favorites that don't already exist
        int addedCount = 0;
        for (Event event : favoriteEvents) {
            if (!existingIds.contains(event.id)) {
                likedEvents.add(event);
                addedCount++;
                Log.d("SearchFragment", "Added new favorite to likedEvents: " + event.name);
            } else {
                Log.d("SearchFragment", "Favorite already in likedEvents: " + event.name);
            }
        }
        
        Log.d("SearchFragment", "Added " + addedCount + " new favorites. Total likedEvents: " + likedEvents.size());
        
        // Sort likedEvents by date and time
        Collections.sort(likedEvents, new Comparator<Event>() {
            @Override
            public int compare(Event e1, Event e2) {
                int dateCompare = e1.date.compareTo(e2.date);
                if (dateCompare != 0) {
                    return dateCompare;
                }
                return e1.time24.compareTo(e2.time24);
            }
        });
        
        // Get current keyword to determine what to show
        String currentKeyword = editKeyword != null ? editKeyword.getText().toString().trim() : keyword;
        
        // Only update display if keyword is empty (showing favorites)
        // If keyword has text, we're showing searchResults, so don't update display here
        if (currentKeyword == null || currentKeyword.isEmpty()) {
            // Update display results (will show favorites since keyword is empty)
            updateDisplayResults();
            
            // Update UI
            if (resultsAdapter != null && requestQueue != null) {
                resultsAdapter.setImageRequestQueue(requestQueue);
            }
            // Only show results if we have something to display
            if (!displayResults.isEmpty()) {
                showResultsState();
            } else {
                showNoEventsState();
            }
        } else {
            // Keyword has text - we're showing searchResults, so just ensure adapter is set up
            // Don't update displayResults (it should already show searchResults)
            if (resultsAdapter != null && requestQueue != null) {
                resultsAdapter.setImageRequestQueue(requestQueue);
            }
        }
        
        // Ensure keyboard stays visible after updating favorites
        if (editKeyword != null) {
            editKeyword.postDelayed(() -> {
                if (editKeyword != null && isAdded() && isResumed()) {
                    if (!editKeyword.hasFocus()) {
                        editKeyword.requestFocus();
                    }
                    if (editKeyword.hasFocus() && editKeyword.getWindowToken() != null) {
                        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(editKeyword, InputMethodManager.SHOW_IMPLICIT);
                            Log.d("SearchFragment", "Keyboard shown after updating favorites");
                        }
                    }
                }
            }, 100);
        }
        
        Log.d("SearchFragment", "Displayed " + likedEvents.size() + " favorite events");
        Log.d("SearchFragment", "=== updateFavoritesInSearchResults END ===");
    }
    
    private void filterResultsByCategory() {
        // This method is called when category tab changes after results are already loaded
        Log.d("SearchFragment", "filterResultsByCategory: selectedCategory=" + selectedCategory + ", allSearchResults.size()=" + allSearchResults.size());
        
        // DO NOT filter out favorites - search results must show ALL API events
        // Favorite status is only used for the star icon, not for filtering
        
        if (selectedCategory == null || selectedCategory.equals("All")) {
            // Show all results - restore from allSearchResults (including favorites)
            searchResults.clear();
            searchResults.addAll(allSearchResults);
            Log.d("SearchFragment", "Restored all results: " + searchResults.size() + " events");
        } else {
            // Filter by selected category only (do not exclude favorites)
            List<Event> filtered = new ArrayList<>();
            for (Event event : allSearchResults) {
                if (event.segment != null && event.segment.equals(selectedCategory)) {
                    filtered.add(event);
                }
            }
            searchResults.clear();
            searchResults.addAll(filtered);
            Log.d("SearchFragment", "Filtered to category '" + selectedCategory + "': " + searchResults.size() + " events");
        }
        
        // Update display results (will show only search results since keyword should have text when filtering)
        updateDisplayResults();
        
        // Show results if we have search results (don't show favorites when filtering)
        if (displayResults.isEmpty()) {
            showNoEventsState();
        } else {
            showResultsState();
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
            Log.e("SearchFragment", "Error formatting date: " + dateStr, e);
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
            Log.e("SearchFragment", "Error formatting time: " + time24, e);
            return time24; // Return original if parsing fails
        }
    }
    
    private void fetchCurrentLocation(Runnable callback) {
        Log.d("SearchFragment", "Fetching current location from ipinfo...");
        String url = BASE_URL + "/api/autodetect";
        
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            response -> {
                try {
                    latitude = Double.parseDouble(response.getString("lat"));
                    longitude = Double.parseDouble(response.getString("lon"));
                    Log.d("SearchFragment", "Current location: lat=" + latitude + ", lng=" + longitude);
                    if (callback != null) callback.run();
                } catch (JSONException e) {
                    Log.e("SearchFragment", "Error parsing location response", e);
                    // Use default location
                    latitude = 34.0522;
                    longitude = -118.2437;
                    if (callback != null) callback.run();
                }
            },
            error -> {
                Log.e("SearchFragment", "Error fetching current location", error);
                // Use default location
                latitude = 34.0522;
                longitude = -118.2437;
                if (callback != null) callback.run();
            }
        );
        
        requestQueue.add(request);
    }
    
    private void geocodeLocationForSearch(String address, Runnable callback) {
        Log.d("SearchFragment", "Geocoding location: " + address);
        String googleApiKey = com.example.eventfinderjava.BuildConfig.GOOGLE_GEOCODING_API_KEY;
        String encodedAddress = java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + encodedAddress + "&key=" + googleApiKey;
        
        StringRequest request = new StringRequest(
            Request.Method.GET,
            url,
            response -> {
                try {
                    JSONObject jsonResponse = new JSONObject(response);
                    if (jsonResponse.getString("status").equals("OK")) {
                        JSONArray results = jsonResponse.getJSONArray("results");
                        if (results.length() > 0) {
                            JSONObject result = results.getJSONObject(0);
                            JSONObject geometry = result.getJSONObject("geometry");
                            JSONObject location = geometry.getJSONObject("location");
                            latitude = location.getDouble("lat");
                            longitude = location.getDouble("lng");
                            Log.d("SearchFragment", "Geocoded location: lat=" + latitude + ", lng=" + longitude);
                            if (callback != null) callback.run();
                            return;
                        }
                    }
                } catch (JSONException e) {
                    Log.e("SearchFragment", "Error parsing geocoding response", e);
                }
                // Use default on error
                latitude = 34.0522;
                longitude = -118.2437;
                if (callback != null) callback.run();
            },
            error -> {
                Log.e("SearchFragment", "Error geocoding location", error);
                // Use default on error
                latitude = 34.0522;
                longitude = -118.2437;
                if (callback != null) callback.run();
            }
        );
        
        requestQueue.add(request);
    }
    
    private void handleFavoriteToggle(Event event, boolean isFavorite) {
        Log.d("SearchFragment", "Favorite toggle: event=" + event.name + ", isFavorite=" + isFavorite);
        
        // Set flag to prevent keyboard/dropdown from showing during favorite handling
        isFavoriteClickInProgress = true;
        
        // Hide keyboard and dismiss dropdowns when favorite button is clicked
        hideKeyboardAndDropdowns();
        
        if (isFavorite) {
            // Add to favorites
            addToFavorites(event);
        } else {
            // Remove from favorites
            removeFromFavorites(event);
        }
        
        // Clear the flag after a delay to allow RecyclerView updates to complete
        // Use a longer delay to ensure all RecyclerView updates and post() callbacks are done
        editKeyword.postDelayed(() -> {
            isFavoriteClickInProgress = false;
            Log.d("SearchFragment", "Favorite click flag cleared");
        }, 1000);
    }
    
    private void hideKeyboardAndDropdowns() {
        // Hide keyword suggestions dropdown
        if (editKeyword != null) {
            editKeyword.dismissDropDown();
            editKeyword.clearFocus();
            if (editKeyword.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(editKeyword.getWindowToken(), 0);
                }
            }
        }
        
        // Hide location suggestions dropdown
        if (autoCompleteLocation != null) {
            autoCompleteLocation.dismissDropDown();
            autoCompleteLocation.clearFocus();
            if (autoCompleteLocation.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(autoCompleteLocation.getWindowToken(), 0);
                }
            }
        }
    }
    
    private void addToFavorites(Event event) {
        Log.d("SearchFragment", "Adding to favorites: " + event.name);
        String url = BASE_URL + "/api/favorites";
        
        try {
            JSONObject eventJson = new JSONObject();
            eventJson.put("id", event.id);
            eventJson.put("name", event.name);
            eventJson.put("venue", event.venue);
            eventJson.put("image", event.imageUrl != null ? event.imageUrl : "");
            eventJson.put("date", event.date != null ? event.date : "");
            // Backend expects 24-hour format (HH:mm:ss), not formatted AM/PM
            eventJson.put("time", event.time24 != null ? event.time24 : "");
            eventJson.put("segment", event.segment != null ? event.segment : "");
            
            Log.d("SearchFragment", "Sending favorite data: name=" + event.name + ", venue=" + event.venue + 
                ", date=" + event.date + ", time=" + event.time24);
            
            JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                url,
                eventJson,
                response -> {
                    Log.d("SearchFragment", "Successfully added to favorites");
                    // Update UI state immediately
                    updateEventFavoriteState(event.id, true);
                },
                error -> {
                    Log.e("SearchFragment", "Error adding to favorites", error);
                    // Revert favorite state on error
                    event.isFavorite = false;
                    if (resultsAdapter != null) {
                        resultsAdapter.notifyDataSetChanged();
                    }
                }
            );
            
            requestQueue.add(request);
        } catch (JSONException e) {
            Log.e("SearchFragment", "Error creating favorite JSON", e);
        }
    }
    
    private void removeFromFavorites(Event event) {
        Log.d("SearchFragment", "Removing from favorites: " + event.name);
        String url = BASE_URL + "/api/favorites/" + event.id;
        
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.DELETE,
            url,
            null,
            response -> {
                Log.d("SearchFragment", "Successfully removed from favorites");
                // Update UI state immediately - remove from display if not in searchResults
                updateEventFavoriteState(event.id, false);
            },
            error -> {
                Log.e("SearchFragment", "Error removing from favorites", error);
                // Revert favorite state on error
                event.isFavorite = true;
                if (resultsAdapter != null) {
                    resultsAdapter.notifyDataSetChanged();
                }
            }
        );
        
        requestQueue.add(request);
    }
    
    /**
     * Public method to update favorite state for an event in search results.
     * Called from EventDetailsFragment when favorite is toggled.
     */
    public void updateEventFavoriteState(String eventId, boolean isFavorite) {
        Log.d("SearchFragment", "=== updateEventFavoriteState START ===");
        Log.d("SearchFragment", "Event ID: " + eventId + ", isFavorite: " + isFavorite);
        Log.d("SearchFragment", "Current likedEvents size: " + likedEvents.size());
        
        boolean foundInLikedEvents = false;
        boolean foundInSearchResults = false;
        boolean foundInAllSearchResults = false;
        
        // First, check if event is in searchResults (to determine if it should stay visible when unliked)
        for (Event event : searchResults) {
            if (event.id.equals(eventId)) {
                foundInSearchResults = true;
                event.isFavorite = isFavorite;
                Log.d("SearchFragment", "Updated event in searchResults, isFavorite=" + isFavorite);
                break;
            }
        }
        
        // Also check in allSearchResults
        for (Event event : allSearchResults) {
            if (event.id.equals(eventId)) {
                foundInAllSearchResults = true;
                event.isFavorite = isFavorite;
                Log.d("SearchFragment", "Updated event in allSearchResults, isFavorite=" + isFavorite);
                break;
            }
        }
        
        // Check if event is already in likedEvents
        for (Event event : likedEvents) {
            if (event.id.equals(eventId)) {
                if (!isFavorite) {
                    // Remove from likedEvents if unliked
                    likedEvents.remove(event);
                    Log.d("SearchFragment", "Removed event from likedEvents: " + eventId);
                    Log.d("SearchFragment", "Event is in searchResults: " + foundInSearchResults + ", will " + (foundInSearchResults ? "STAY visible" : "be REMOVED"));
                } else {
                    // Update favorite state
                    event.isFavorite = isFavorite;
                    foundInLikedEvents = true;
                    Log.d("SearchFragment", "Event already in likedEvents, updated state");
                }
                break;
            }
        }
        
        // If event is now a favorite but not in likedEvents, fetch it and add it
        if (isFavorite && !foundInLikedEvents) {
            Log.d("SearchFragment", "Event " + eventId + " is now favorite but not in likedEvents. Fetching details...");
            fetchAndAddFavoriteEvent(eventId);
        } else {
            // Update display results and notify adapter (whether added or removed)
            // Get current keyword to determine what to show
            String currentKeyword = editKeyword != null ? editKeyword.getText().toString().trim() : keyword;
            Log.d("SearchFragment", "Updating display results immediately. likedEvents size: " + likedEvents.size());
            Log.d("SearchFragment", "Event in searchResults: " + foundInSearchResults + ", searchResults size: " + searchResults.size());
            updateDisplayResults();
            Log.d("SearchFragment", "displayResults size after update: " + displayResults.size());
            
            if (resultsAdapter != null) {
                resultsAdapter.notifyDataSetChanged();
                Log.d("SearchFragment", "Adapter notified. displayResults size: " + displayResults.size());
            }
            
            // Update UI state based on whether we have any results
            // When keyword has text, displayResults contains searchResults (all events, including favorited ones)
            // When keyword is empty, displayResults contains likedEvents
            if (!displayResults.isEmpty()) {
                showResultsState();
                Log.d("SearchFragment", "Showing results state");
            } else {
                showNoEventsState();
                Log.d("SearchFragment", "Showing no events state");
            }
        }
        Log.d("SearchFragment", "=== updateEventFavoriteState END ===");
    }
    
    private void fetchAndAddFavoriteEvent(String eventId) {
        String url = BASE_URL + "/api/eventdetails?id=" + eventId;
        Log.d("SearchFragment", "Fetching event details to add to search results: " + url);
        
        JsonObjectRequest request = new JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            response -> {
                try {
                    // Parse event details similar to parseSearchResults
                    String id = response.optString("id", "");
                    String name = response.optString("name", "");
                    
                    // Get venue
                    String venue = "Unknown Venue";
                    JSONObject embedded = response.optJSONObject("_embedded");
                    if (embedded != null) {
                        JSONArray venues = embedded.optJSONArray("venues");
                        if (venues != null && venues.length() > 0) {
                            JSONObject venueObj = venues.getJSONObject(0);
                            venue = venueObj.optString("name", "Unknown Venue");
                        }
                    }
                    
                    // Get date and time
                    String date = "";
                    String time24 = "";
                    JSONObject dates = response.optJSONObject("dates");
                    if (dates != null) {
                        JSONObject start = dates.optJSONObject("start");
                        if (start != null) {
                            date = start.optString("localDate", "");
                            time24 = start.optString("localTime", "");
                        }
                    }
                    
                    // Format date and time
                    String dateFormatted = formatDate(date);
                    String timeFormatted = formatTime(time24);
                    
                    // Get image URL
                    String imageUrl = "";
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
                            imageUrl = bestImage.optString("url", "");
                        } else {
                            imageUrl = images.getJSONObject(0).optString("url", "");
                        }
                    }
                    
                    // Get segment
                    String segment = "Unknown";
                    JSONArray classifications = response.optJSONArray("classifications");
                    if (classifications != null && classifications.length() > 0) {
                        JSONObject classification = classifications.getJSONObject(0);
                        JSONObject segmentObj = classification.optJSONObject("segment");
                        if (segmentObj != null) {
                            segment = segmentObj.optString("name", "Unknown");
                        }
                    }
                    
                    // Create Event object
                    Event event = new Event(id, name, venue, date, dateFormatted, timeFormatted, time24, imageUrl, segment);
                    event.isFavorite = true;
                    
                    // Check if event is already in likedEvents (avoid duplicates)
                    boolean alreadyExists = false;
                    for (Event existingEvent : likedEvents) {
                        if (existingEvent.id.equals(event.id)) {
                            alreadyExists = true;
                            break;
                        }
                    }
                    
                    if (!alreadyExists) {
                        // Add to likedEvents (these persist across searches)
                        likedEvents.add(event);
                        Log.d("SearchFragment", "=== Added favorite event to likedEvents: " + name);
                        Log.d("SearchFragment", "likedEvents size after add: " + likedEvents.size());
                        
                        // Sort likedEvents by date and time
                        Collections.sort(likedEvents, new Comparator<Event>() {
                            @Override
                            public int compare(Event e1, Event e2) {
                                int dateCompare = e1.date.compareTo(e2.date);
                                if (dateCompare != 0) {
                                    return dateCompare;
                                }
                                return e1.time24.compareTo(e2.time24);
                            }
                        });
                    } else {
                        Log.d("SearchFragment", "Event already exists in likedEvents, skipping add");
                    }
                    
                    // Update display results and UI immediately
                    Log.d("SearchFragment", "Updating display results after adding favorite. likedEvents: " + likedEvents.size());
                    updateDisplayResults();
                    Log.d("SearchFragment", "displayResults size: " + displayResults.size());
                    showResultsState();
                    Log.d("SearchFragment", "Added favorite event to display: " + name);
                } catch (JSONException e) {
                    Log.e("SearchFragment", "Error parsing event details for favorite", e);
                }
            },
            error -> {
                Log.e("SearchFragment", "Error fetching event details for favorite", error);
            }
        );
        
        if (requestQueue != null) {
            requestQueue.add(request);
        }
    }

    private boolean validateKeyword() {
        String keywordText = editKeyword.getText().toString().trim();
        if (keywordText.isEmpty()) {
            textKeywordError.setVisibility(View.VISIBLE);
            // Change search icon to red when validation fails
            if (imageSearchIcon != null) {
                int errorColor = ContextCompat.getColor(requireContext(), R.color.error);
                imageSearchIcon.setColorFilter(errorColor);
            }
            return false;
        }
        textKeywordError.setVisibility(View.GONE);
        // Reset search icon to normal color when validation passes
        if (imageSearchIcon != null) {
            imageSearchIcon.setColorFilter(MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOnSurface, 0));
        }
        return true;
    }

    private boolean validateDistance() {
        int distanceValue = getDistanceValue();
        if (distanceValue < 1) {
            textDistanceError.setText(getString(R.string.search_distance_invalid));
            textDistanceError.setVisibility(View.VISIBLE);
            return false;
        }
        if (distanceValue > 100) {
            textDistanceError.setText(getString(R.string.search_distance_max));
            textDistanceError.setVisibility(View.VISIBLE);
            return false;
        }
        textDistanceError.setVisibility(View.GONE);
        return true;
    }

    private int getDistanceValue() {
        try {
            String distanceText = editDistance.getText().toString().trim();
            if (distanceText.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(distanceText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showLoadingState() {
        isLoading = true;
        if (progressLoading != null) {
            progressLoading.setVisibility(View.VISIBLE);
        }
        if (cardNoEvents != null) {
            cardNoEvents.setVisibility(View.GONE);
        }
        if (recyclerResults != null) {
            recyclerResults.setVisibility(View.GONE);
        }
    }

    private void showNoEventsState() {
        isLoading = false;
        if (progressLoading != null) {
            progressLoading.setVisibility(View.GONE);
        }
        
        // Get current keyword to determine what to show
        String currentKeyword = editKeyword != null ? editKeyword.getText().toString().trim() : keyword;
        
        // If keyword is empty, show favorites instead of "No events found"
        if ((currentKeyword == null || currentKeyword.isEmpty()) && !likedEvents.isEmpty()) {
            updateDisplayResults(); // Will show only favorites
            showResultsState();
            return;
        }
        
        // If keyword is empty and no favorites, try loading favorites
        if ((currentKeyword == null || currentKeyword.isEmpty()) && !hasPerformedSearch) {
            loadFavoritesAsSearchResults();
            // After loading favorites, check again
            if (!likedEvents.isEmpty()) {
                updateDisplayResults(); // Will show only favorites
                showResultsState();
                return;
            }
        }
        
        // If keyword has text OR search was performed, show "No events found" (don't show favorites)
        if (textNoEvents != null) {
            if (searchResults.isEmpty()) {
                // Show "No events found" when searching (even if favorites exist)
                if (cardNoEvents != null) cardNoEvents.setVisibility(View.VISIBLE);
                textNoEvents.setVisibility(View.VISIBLE);
            } else {
                textNoEvents.setVisibility(View.GONE);
            }
        }
        
        if (recyclerResults != null) {
            recyclerResults.setVisibility(View.GONE);
        }
        
        // Always keep keyboard visible on search page - don't hide it
        // The keyboard should always be shown when on the search page, regardless of events or keyword
        Log.d("SearchFragment", "Keeping keyboard visible on search page (always show)");
    }
    
    private void showResultsState() {
        showResultsState(false);
    }
    
    private void showResultsStateWithoutKeyboard() {
        isLoading = false;
        if (progressLoading != null) {
            progressLoading.setVisibility(View.GONE);
        }
        if (cardNoEvents != null) {
            cardNoEvents.setVisibility(View.GONE);
        }
        if (recyclerResults != null) {
            recyclerResults.setVisibility(View.VISIBLE);
            if (resultsAdapter != null) {
                resultsAdapter.notifyDataSetChanged();
                Log.d("SearchFragment", "Updated results adapter with " + displayResults.size() + " events");
            }
        }
        // Don't show or hide keyboard - keep current state
    }
    
    private void showResultsState(boolean hideKeyboardAfterSearch) {
        isLoading = false;
        if (progressLoading != null) {
            progressLoading.setVisibility(View.GONE);
        }
        if (cardNoEvents != null) {
            cardNoEvents.setVisibility(View.GONE);
        }
        if (recyclerResults != null) {
            recyclerResults.setVisibility(View.VISIBLE);
            if (resultsAdapter != null) {
                resultsAdapter.notifyDataSetChanged();
                Log.d("SearchFragment", "Updated results adapter with " + displayResults.size() + " events");
            }
        }
        
        if (hideKeyboardAfterSearch) {
            // Hide keyboard and dropdown after search results are displayed
            if (editKeyword != null) {
                // Dismiss keyword suggestion dropdown
                editKeyword.dismissDropDown();
                
                // Hide keyboard
                if (editKeyword.getWindowToken() != null) {
                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(editKeyword.getWindowToken(), 0);
                        editKeyword.clearFocus();
                        Log.d("SearchFragment", "Keyboard and dropdown hidden after search results");
                    }
                }
            }
        } else {
            // Keep keyboard visible on search page (when not from search) - ensure focus and show keyboard
            // But skip if favorite click or category switch is in progress
            if (editKeyword != null && !isFavoriteClickInProgress && !isCategorySwitchInProgress) {
                editKeyword.post(() -> {
                    if (editKeyword != null && isAdded() && isResumed() && !isFavoriteClickInProgress && !isCategorySwitchInProgress) {
                        if (!editKeyword.hasFocus()) {
                            editKeyword.requestFocus();
                        }
                        if (editKeyword.hasFocus() && editKeyword.getWindowToken() != null && !isFavoriteClickInProgress && !isCategorySwitchInProgress) {
                            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(editKeyword, InputMethodManager.SHOW_IMPLICIT);
                                Log.d("SearchFragment", "Keyboard shown after showing results");
                            }
                        }
                    }
                });
            } else if (isFavoriteClickInProgress || isCategorySwitchInProgress) {
                Log.d("SearchFragment", "Skipping keyboard show in showResultsState - favorite click or category switch in progress");
            }
        }
    }


    private void updateLocationSuggestions(String query) {
        Log.d("SearchFragment", "=== updateLocationSuggestions START ===");
        Log.d("SearchFragment", "Query: '" + query + "'");
        Log.d("SearchFragment", "isLocationSearching: " + isLocationSearching);
        
        locationSuggestions.clear();
        locationSuggestions.add("Current Location");
        Log.d("SearchFragment", "Added 'Current Location'");
        
        // Show "Searching..." right after "Current Location" if we're fetching results
        if (isLocationSearching) {
            locationSuggestions.add("Searching...");
            Log.d("SearchFragment", "Added 'Searching...' (isLocationSearching=true) - positioned after Current Location");
        }
        
        // Note: User's typed text is not added to suggestions to avoid duplicate entries
        
        Log.d("SearchFragment", "Total suggestions before adapter: " + locationSuggestions.size());
        Log.d("SearchFragment", "Suggestions list: " + locationSuggestions.toString());
        
        // Recreate adapter to ensure updates
        locationAdapter = new ArrayAdapter<String>(
            requireContext(),
            R.layout.dropdown_item_location,
            R.id.textLocation,
            locationSuggestions
        ) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view;
                if (convertView == null) {
                    view = LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item_location, parent, false);
                } else {
                    view = convertView;
                }
                
                TextView textView = view.findViewById(R.id.textLocation);
                ProgressBar progressBar = view.findViewById(R.id.progressLocation);
                ImageView iconView = view.findViewById(R.id.imageLocationIcon);
                
                String item = getItem(position);
                if (item != null) {
                    if (item.equals("Searching...")) {
                        // Show loading state - spinner before text
                        textView.setText("Searching...");
                        progressBar.setVisibility(View.VISIBLE);
                        iconView.setVisibility(View.GONE);
                    } else if (item.equals("Current Location")) {
                        // Show "Current Location" with icon
                        textView.setText(item);
                        iconView.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                    } else {
                        // Show normal location item without icon
                        textView.setText(item);
                        iconView.setVisibility(View.GONE);
                        progressBar.setVisibility(View.GONE);
                    }
                }
                return view;
            }
        };
        autoCompleteLocation.setAdapter(locationAdapter);
        locationAdapter.notifyDataSetChanged();
        Log.d("SearchFragment", "Adapter updated. Adapter count: " + locationAdapter.getCount());
        Log.d("SearchFragment", "autoCompleteLocation adapter is null: " + (autoCompleteLocation.getAdapter() == null));
        Log.d("SearchFragment", "=== updateLocationSuggestions END ===");
    }

    private void fetchLocationSuggestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        
        Log.d("SearchFragment", "=== fetchLocationSuggestions START ===");
        Log.d("SearchFragment", "Query: '" + query + "'");
        
        // Cancel any pending removal of "Searching..." from previous request
        if (locationSearchingRemoveRunnable != null) {
            locationDebounceHandler.removeCallbacks(locationSearchingRemoveRunnable);
            locationSearchingRemoveRunnable = null;
            Log.d("SearchFragment", "Cancelled previous 'remove Searching...' delay");
        }
        
        // Cancel any pending location API request from previous keystroke
        if (currentLocationRequest != null) {
            currentLocationRequest.cancel();
            Log.d("SearchFragment", "Cancelled previous location API request");
        }
        
        // Update current query to ignore stale responses
        currentLocationQuery = query;
        
        // Always show "Searching..." when starting a new search
        // Reset start time to ensure full minimum display time on every keystroke
        isLocationSearching = true;
        locationSearchStartTime = System.currentTimeMillis();
        updateLocationSuggestions(query);
        Log.d("SearchFragment", "Set isLocationSearching=true, reset start time to " + locationSearchStartTime + ", and showed 'Searching...'");
        
        // Use Google Geocoding API directly - API key from BuildConfig
        String googleApiKey = com.example.eventfinderjava.BuildConfig.GOOGLE_GEOCODING_API_KEY;
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + encodedQuery + "&key=" + googleApiKey;
        
        Log.d("SearchFragment", "=== Geocoding API Request ===");
        Log.d("SearchFragment", "Query: '" + query + "'");
        Log.d("SearchFragment", "Encoded Query: '" + encodedQuery + "'");
        Log.d("SearchFragment", "API Key present: " + (googleApiKey != null && !googleApiKey.isEmpty()));
        Log.d("SearchFragment", "Full URL: " + url.replace(googleApiKey, "***HIDDEN***"));
        Log.d("SearchFragment", "RequestQueue is null: " + (requestQueue == null));
        
        // Store query in a final variable for comparison
        final String requestQuery = query;
        
        // Create a request tag to identify this specific request
        final Object requestTag = new Object();
        
        StringRequest stringRequest = new StringRequest(
            Request.Method.GET,
            url,
            new Response.Listener<String>() {
                @Override
                public void onResponse(String response) {
                    // Check if this request is still the current one (user might have typed again)
                    if (currentLocationRequest == null || currentLocationRequest.getTag() != requestTag) {
                        Log.d("SearchFragment", "Response received but request was superseded - ignoring");
                        return;
                    }
                    
                    Log.d("SearchFragment", "=== Geocoding API Response received ===");
                    Log.d("SearchFragment", "Response length: " + response.length());
                    Log.d("SearchFragment", "Response preview (first 500 chars): " + 
                        (response.length() > 500 ? response.substring(0, 500) : response));
                    
                    // Clear the current request since it's completed
                    currentLocationRequest = null;
                    
                    try {
                        // Check if response is HTML (error page)
                        if (response.trim().startsWith("<!--") || response.trim().startsWith("<")) {
                            Log.e("SearchFragment", "*** ERROR: Server returned HTML instead of JSON ***");
                            Log.e("SearchFragment", "This usually means the API endpoint doesn't exist or returned an error page");
                            
                            // Ensure minimum display time for "Searching..."
                            long elapsedTime = System.currentTimeMillis() - locationSearchStartTime;
                            long remainingTime = MIN_SEARCHING_DISPLAY_TIME_MS - elapsedTime;
                            
                            if (remainingTime > 0) {
                                locationSearchingRemoveRunnable = () -> {
                                    isLocationSearching = false;
                                    updateLocationSuggestions(query);
                                    locationSearchingRemoveRunnable = null;
                                };
                                locationDebounceHandler.postDelayed(locationSearchingRemoveRunnable, remainingTime);
                            } else {
                                isLocationSearching = false;
                                updateLocationSuggestions(query);
                            }
                            return;
                        }
                        
                        JSONObject jsonResponse = new JSONObject(response);
                        Log.d("SearchFragment", "Successfully parsed JSON response");
                        
                        // Check for API errors
                        String status = jsonResponse.optString("status", "UNKNOWN");
                        Log.d("SearchFragment", "API Status: " + status);
                        
                        if (!status.equals("OK")) {
                            String errorMessage = jsonResponse.optString("error_message", "Unknown error");
                            Log.e("SearchFragment", "*** Geocoding API Error: " + status + " - " + errorMessage + " ***");
                            
                            // Ensure minimum display time for "Searching..."
                            long elapsedTime = System.currentTimeMillis() - locationSearchStartTime;
                            long remainingTime = MIN_SEARCHING_DISPLAY_TIME_MS - elapsedTime;
                            
                            if (remainingTime > 0) {
                                locationSearchingRemoveRunnable = () -> {
                                    isLocationSearching = false;
                                    if (status.equals("ZERO_RESULTS")) {
                                        Log.d("SearchFragment", "ZERO_RESULTS - showing only Current Location and user's text");
                                        updateLocationSuggestions(query);
                                    } else {
                                        updateLocationSuggestions(query);
                                    }
                                    locationSearchingRemoveRunnable = null;
                                };
                                locationDebounceHandler.postDelayed(locationSearchingRemoveRunnable, remainingTime);
                            } else {
                                isLocationSearching = false;
                                if (status.equals("ZERO_RESULTS")) {
                                    Log.d("SearchFragment", "ZERO_RESULTS - showing only Current Location and user's text");
                                    updateLocationSuggestions(query);
                                } else {
                                    updateLocationSuggestions(query);
                                }
                            }
                            return;
                        }
                        
                        JSONArray results = jsonResponse.getJSONArray("results");
                        Log.d("SearchFragment", "Number of results: " + results.length());
                        
                        List<String> newSuggestions = new ArrayList<>();
                        newSuggestions.add("Current Location");
                        
                        // Keep "Searching..." in the list until minimum display time has passed
                        if (isLocationSearching) {
                            newSuggestions.add("Searching...");
                            Log.d("SearchFragment", "Keeping 'Searching...' in list until minimum display time");
                        }
                        
                        // Note: User's typed text is not added to avoid duplicate entries
                        
                        // Add geocoding results (limit to 5)
                        int maxResults = Math.min(results.length(), 5);
                        Log.d("SearchFragment", "Adding " + maxResults + " geocoding results");
                        for (int i = 0; i < maxResults; i++) {
                            JSONObject result = results.getJSONObject(i);
                            String formattedAddress = result.getString("formatted_address");
                            Log.d("SearchFragment", "  [" + i + "] " + formattedAddress);
                            // Avoid duplicates
                            if (!newSuggestions.contains(formattedAddress)) {
                                newSuggestions.add(formattedAddress);
                            } else {
                                Log.d("SearchFragment", "  Skipped duplicate: " + formattedAddress);
                            }
                        }
                        
                        Log.d("SearchFragment", "Total suggestions after processing: " + newSuggestions.size());
                        // Update suggestions with geocoding results (including "Searching..." if still needed)
                        locationSuggestions.clear();
                        locationSuggestions.addAll(newSuggestions);
                        
                        // Update adapter with new suggestions (don't call updateLocationSuggestions as it clears results)
                        locationAdapter = new ArrayAdapter<String>(
                            requireContext(),
                            R.layout.dropdown_item_location,
                            R.id.textLocation,
                            locationSuggestions
                        ) {
                            @NonNull
                            @Override
                            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                View view;
                                if (convertView == null) {
                                    view = LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item_location, parent, false);
                                } else {
                                    view = convertView;
                                }
                                
                                TextView textView = view.findViewById(R.id.textLocation);
                                ProgressBar progressBar = view.findViewById(R.id.progressLocation);
                                ImageView iconView = view.findViewById(R.id.imageLocationIcon);
                                
                                String item = getItem(position);
                                if (item != null) {
                                    if (item.equals("Searching...")) {
                                        // Show loading state - spinner before text
                                        textView.setText("Searching...");
                                        progressBar.setVisibility(View.VISIBLE);
                                        iconView.setVisibility(View.GONE);
                                    } else if (item.equals("Current Location")) {
                                        // Show "Current Location" with icon
                                        textView.setText(item);
                                        iconView.setVisibility(View.VISIBLE);
                                        progressBar.setVisibility(View.GONE);
                                    } else {
                                        // Show normal location item without icon
                                        textView.setText(item);
                                        iconView.setVisibility(View.GONE);
                                        progressBar.setVisibility(View.GONE);
                                    }
                                }
                                return view;
                            }
                        };
                        autoCompleteLocation.setAdapter(locationAdapter);
                        locationAdapter.notifyDataSetChanged();
                        
                        // Check if this response is still relevant (user might have typed again)
                        if (!requestQuery.equals(currentLocationQuery)) {
                            Log.d("SearchFragment", "Response received for old query '" + requestQuery + "', current is '" + currentLocationQuery + "' - ignoring");
                            return;
                        }
                        
                        // Calculate elapsed time and ensure minimum display time for "Searching..."
                        long elapsedTime = System.currentTimeMillis() - locationSearchStartTime;
                        long remainingTime = MIN_SEARCHING_DISPLAY_TIME_MS - elapsedTime;
                        Log.d("SearchFragment", "Elapsed time: " + elapsedTime + "ms, Remaining time: " + remainingTime + "ms");
                        
                        if (remainingTime > 0) {
                            Log.d("SearchFragment", "API responded quickly (" + elapsedTime + "ms). Waiting " + remainingTime + "ms to remove 'Searching...'");
                            // Delay hiding "Searching..." to ensure minimum display time
                            locationSearchingRemoveRunnable = () -> {
                                // Check if this removal is still relevant (user might have typed again)
                                if (!requestQuery.equals(currentLocationQuery)) {
                                    Log.d("SearchFragment", "Skipping removal - query changed from '" + requestQuery + "' to '" + currentLocationQuery + "'");
                                    locationSearchingRemoveRunnable = null;
                                    return;
                                }
                                isLocationSearching = false;
                                // Remove "Searching..." from suggestions and recreate adapter to ensure update
                                if (locationSuggestions.contains("Searching...")) {
                                    locationSuggestions.remove("Searching...");
                                    Log.d("SearchFragment", "Removed 'Searching...' from list. Remaining items: " + locationSuggestions.toString());
                                    // Recreate adapter to ensure proper update
                                    locationAdapter = new ArrayAdapter<String>(
                                        requireContext(),
                                        R.layout.dropdown_item_location,
                                        R.id.textLocation,
                                        locationSuggestions
                                    ) {
                                        @NonNull
                                        @Override
                                        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                                            View view;
                                            if (convertView == null) {
                                                view = LayoutInflater.from(getContext()).inflate(R.layout.dropdown_item_location, parent, false);
                                            } else {
                                                view = convertView;
                                            }
                                            
                                            TextView textView = view.findViewById(R.id.textLocation);
                                            ProgressBar progressBar = view.findViewById(R.id.progressLocation);
                                            ImageView iconView = view.findViewById(R.id.imageLocationIcon);
                                            
                                            String item = getItem(position);
                                            if (item != null) {
                                                if (item.equals("Current Location")) {
                                                    textView.setText(item);
                                                    iconView.setVisibility(View.VISIBLE);
                                                    progressBar.setVisibility(View.GONE);
                                                } else {
                                                    textView.setText(item);
                                                    iconView.setVisibility(View.GONE);
                                                    progressBar.setVisibility(View.GONE);
                                                }
                                            }
                                            return view;
                                        }
                                    };
                                    autoCompleteLocation.setAdapter(locationAdapter);
                                    locationAdapter.notifyDataSetChanged();
                                    Log.d("SearchFragment", "Removed 'Searching...' after minimum display time. Final count: " + locationSuggestions.size() + ", items: " + locationSuggestions.toString());
                                    
                                    // Verify "Searching..." is not in the list
                                    if (locationSuggestions.contains("Searching...")) {
                                        Log.e("SearchFragment", "ERROR: 'Searching...' still in list after removal!");
                                        locationSuggestions.remove("Searching...");
                                        locationAdapter.notifyDataSetChanged();
                                    }
                                }
                                locationSearchingRemoveRunnable = null;
                            };
                            locationDebounceHandler.postDelayed(locationSearchingRemoveRunnable, remainingTime);
                        } else {
                            // Already exceeded minimum time, hide immediately
                            isLocationSearching = false;
                            // Remove "Searching..." from suggestions and update adapter
                            if (locationSuggestions.contains("Searching...")) {
                                locationSuggestions.remove("Searching...");
                                locationAdapter.notifyDataSetChanged();
                                Log.d("SearchFragment", "Removed 'Searching...' immediately. Final count: " + locationSuggestions.size());
                            }
                        }
                        
                        Log.d("SearchFragment", "Adapter updated with " + locationSuggestions.size() + " items");
                        Log.d("SearchFragment", "Final suggestions: " + locationSuggestions.toString());
                        
                        // Show dropdown if field has focus
                        if (autoCompleteLocation != null && autoCompleteLocation.hasFocus()) {
                            Log.d("SearchFragment", "Field has focus, attempting to show dropdown");
                            autoCompleteLocation.post(() -> {
                                if (autoCompleteLocation.hasFocus() && !locationSuggestions.isEmpty()) {
                                    Log.d("SearchFragment", "Showing dropdown with " + locationSuggestions.size() + " items");
                                    autoCompleteLocation.showDropDown();
                                    Log.d("SearchFragment", "Dropdown showing: " + autoCompleteLocation.isPopupShowing());
                                } else {
                                    Log.d("SearchFragment", "Cannot show dropdown - hasFocus: " + 
                                        (autoCompleteLocation != null ? autoCompleteLocation.hasFocus() : "N/A") + 
                                        ", suggestions empty: " + locationSuggestions.isEmpty());
                                }
                            });
                        } else {
                            Log.d("SearchFragment", "Field does not have focus, not showing dropdown");
                        }
                    } catch (JSONException e) {
                        Log.e("SearchFragment", "*** ERROR parsing location response ***");
                        Log.e("SearchFragment", "Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        Log.e("SearchFragment", "Stack trace: ", e);
                        Log.e("SearchFragment", "Response was: " + (response.length() > 500 ? response.substring(0, 500) : response));
                        isLocationSearching = false;
                        updateLocationSuggestions(query);
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    // Check if this request is still the current one (user might have typed again)
                    if (currentLocationRequest == null || currentLocationRequest.getTag() != requestTag) {
                        Log.d("SearchFragment", "Error received but request was superseded - ignoring");
                        return;
                    }
                    
                    // Clear the current request since it's completed (with error)
                    currentLocationRequest = null;
                    
                    Log.e("SearchFragment", "=== Geocoding API Error Response ===");
                    Log.e("SearchFragment", "Error message: " + error.getMessage());
                    if (error.networkResponse != null) {
                        Log.e("SearchFragment", "Network response status: " + error.networkResponse.statusCode);
                        try {
                            String responseBody = new String(error.networkResponse.data, "UTF-8");
                            Log.e("SearchFragment", "Error response body: " + 
                                (responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody));
                        } catch (Exception e) {
                            Log.e("SearchFragment", "Error parsing error response: " + e.getMessage());
                        }
                    } else {
                        Log.e("SearchFragment", "Network response: null (likely network error or timeout)");
                    }
                    Log.e("SearchFragment", "Stack trace: ", error);
                    
                    // Ensure minimum display time for "Searching..."
                    long elapsedTime = System.currentTimeMillis() - locationSearchStartTime;
                    long remainingTime = MIN_SEARCHING_DISPLAY_TIME_MS - elapsedTime;
                    
                    if (remainingTime > 0) {
                        locationSearchingRemoveRunnable = () -> {
                            isLocationSearching = false;
                            updateLocationSuggestions(query);
                            locationSearchingRemoveRunnable = null;
                        };
                        locationDebounceHandler.postDelayed(locationSearchingRemoveRunnable, remainingTime);
                    } else {
                        isLocationSearching = false;
                        updateLocationSuggestions(query);
                    }
                }
            }
        );
        
        // Set tag and store the request so we can cancel it if user types again
        stringRequest.setTag(requestTag);
        currentLocationRequest = stringRequest;
        
        Log.d("SearchFragment", "Adding request to queue");
        requestQueue.add(stringRequest);
        Log.d("SearchFragment", "Request added to queue. Request count: " + requestQueue.getSequenceNumber());
        Log.d("SearchFragment", "=== fetchLocationSuggestions END (request queued) ===");
    }

    private void simulateSearch() {
        // Simulate API call delay
        if (recyclerResults != null) {
            recyclerResults.postDelayed(() -> {
                // For now, always show "No events found"
                // When backend is ready, replace this with:
                // updateSearchResults(apiResults);
                showNoEventsState();
            }, 1000);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("SearchFragment", "=== onResume() called ===");
        Log.d("SearchFragment", "likedEvents size: " + likedEvents.size());
        Log.d("SearchFragment", "searchResults size: " + searchResults.size());
        Log.d("SearchFragment", "allSearchResults size: " + allSearchResults.size());
        Log.d("SearchFragment", "hasPerformedSearch: " + hasPerformedSearch);
        
        // Reset category to "All" when returning from Event Details
        if (tabCategories != null && selectedCategory != null && !selectedCategory.equals("All")) {
            Log.d("SearchFragment", "Resetting category from '" + selectedCategory + "' to 'All'");
            selectedCategory = "All";
            TabLayout.Tab allTab = tabCategories.getTabAt(0);
            if (allTab != null) {
                tabCategories.selectTab(allTab);
            }
        }
        
        // Sync favorite state with backend (refresh favorites)
        loadFavoritesAndSync();
        
        // When returning from Event Details, clear previous search results
        // Keep likedEvents intact, but clear searchResults and allSearchResults
        // This ensures only favorite events are shown when returning
        if (!searchResults.isEmpty() || !allSearchResults.isEmpty()) {
            Log.d("SearchFragment", "Clearing previous search results when returning from Event Details");
            searchResults.clear();
            allSearchResults.clear();
        }
        
        // Always check backend for favorites when returning from Event Details
        // This ensures we get newly liked events even if the fragment was recreated
        if (!hasPerformedSearch) {
            if (likedEvents.isEmpty()) {
                Log.d("SearchFragment", "likedEvents is empty and no search performed - loading favorites from backend");
                loadFavoritesAsSearchResults();
            } else {
                // We already have liked events, but check backend for any new ones that might have been added
                Log.d("SearchFragment", "likedEvents has " + likedEvents.size() + " events - checking for new favorites from backend");
                checkAndAddNewFavorites();
            }
        } else {
            // Search was performed, but still check for new favorites that might have been added
            Log.d("SearchFragment", "Search was performed - checking for new favorites");
            checkAndAddNewFavorites();
        }
        
        // Update display results (will show favorites if keyword is empty, search results if keyword has text)
        Log.d("SearchFragment", "Before updateDisplayResults - likedEvents: " + likedEvents.size() + ", searchResults: " + searchResults.size());
        updateDisplayResults();
        Log.d("SearchFragment", "After updateDisplayResults - displayResults: " + displayResults.size());
        
        if (!displayResults.isEmpty()) {
            Log.d("SearchFragment", "Showing results state with " + displayResults.size() + " events");
            showResultsState();
        } else {
            Log.d("SearchFragment", "No events to display - showing no events state");
            showNoEventsState();
        }
        
        // Always show keyboard when navigating to search page (regardless of keyword or favorite events)
        if (editKeyword != null) {
            editKeyword.postDelayed(() -> {
                if (editKeyword != null && isAdded() && isResumed()) {
                    Log.d("SearchFragment", "=== onResume: Attempting to show keyboard (always show on search page) ===");
                    editKeyword.requestFocus();
                    
                    editKeyword.postDelayed(() -> {
                        if (editKeyword != null && isAdded() && isResumed()) {
                            // Check if field has focus or try to get it
                            if (!editKeyword.hasFocus()) {
                                editKeyword.requestFocus();
                            }
                            
                            if (editKeyword.hasFocus() && editKeyword.getWindowToken() != null) {
                                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) {
                                    boolean shown = imm.showSoftInput(editKeyword, InputMethodManager.SHOW_IMPLICIT);
                                    Log.d("SearchFragment", "*** onResume: showSoftInput result: " + shown + " ***");
                                    
                                    if (!shown) {
                                        // Force show on emulator
                                        editKeyword.postDelayed(() -> {
                                            if (editKeyword != null && editKeyword.hasFocus() && isAdded() && isResumed()) {
                                                InputMethodManager imm2 = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                                if (imm2 != null) {
                                                    imm2.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                                                    Log.d("SearchFragment", "*** onResume: toggleSoftInput called as fallback ***");
                                                }
                                            }
                                        }, 200);
                                    }
                                }
                            } else {
                                Log.d("SearchFragment", "Cannot show keyboard - field not focused or no window token");
                            }
                        }
                    }, 300);
                }
            }, 200);
        }
    }
}

