package com.example.eventfinderjava;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;
import java.util.List;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ViewHolder> {

    private final List<Event> events;
    private final OnFavoriteClickListener favoriteClickListener;
    private OnItemClickListener itemClickListener;
    private RequestQueue imageRequestQueue;
    private static final String TAG = "SearchResultsAdapter";

    public interface OnFavoriteClickListener {
        void onFavoriteClick(Event event, boolean isFavorite);
    }

    public interface OnItemClickListener {
        void onItemClick(Event event);
    }

    public SearchResultsAdapter(List<Event> events, OnFavoriteClickListener listener) {
        this.events = events;
        this.favoriteClickListener = listener;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setImageRequestQueue(RequestQueue queue) {
        this.imageRequestQueue = queue;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.search_result_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);
        
        // Cancel any pending image request for this view holder
        if (holder.imageRequest != null) {
            holder.imageRequest.cancel();
            holder.imageRequest = null;
        }
        
        // Clear previous image and set placeholder
        holder.imageEvent.setImageBitmap(null);
        // Don't set a resource - let the FrameLayout background show through as placeholder
        // The ImageView will be transparent until image loads
        
        // Set event name with marquee
        holder.textEventName.setText(event.name);
        holder.textEventName.setSelected(true); // Enable marquee
        
        // Set venue with marquee
        holder.textVenue.setText(event.venue);
        holder.textVenue.setSelected(true); // Enable marquee
        
        // Show and set category (genre) label - display all genres including "Unknown" and "Miscellaneous"
        if (event.segment != null && !event.segment.isEmpty()) {
            holder.textCategory.setText(event.segment);
            holder.textCategory.setVisibility(View.VISIBLE);
        } else {
            holder.textCategory.setVisibility(View.GONE);
        }
        
        // Show and set date/time label
        if (event.dateFormatted != null && !event.dateFormatted.isEmpty() && event.time != null && !event.time.isEmpty()) {
            String dateTimeText = event.dateFormatted + ", " + event.time;
            holder.textDateTime.setText(dateTimeText);
            holder.textDateTime.setVisibility(View.VISIBLE);
        } else if (event.dateFormatted != null && !event.dateFormatted.isEmpty()) {
            holder.textDateTime.setText(event.dateFormatted);
            holder.textDateTime.setVisibility(View.VISIBLE);
        } else {
            holder.textDateTime.setVisibility(View.GONE);
        }
        
        // Set favorite star state
        if (event.isFavorite) {
            // Filled star with #50515B color
            holder.buttonFavorite.setImageResource(R.drawable.ic_star_filled);
            holder.buttonFavorite.setColorFilter(MaterialColors.getColor(holder.itemView.getContext(), com.google.android.material.R.attr.colorOnSurface, 0));
        } else {
            // Outline star with border only, no fill
            holder.buttonFavorite.setImageResource(R.drawable.ic_star_outline);
            holder.buttonFavorite.setColorFilter(MaterialColors.getColor(holder.itemView.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
        }
        
        // Category icon is not in the layout, so we skip it for now
        
        // Load event image
        if (event.imageUrl != null && !event.imageUrl.isEmpty() && imageRequestQueue != null) {
            Log.d(TAG, "Loading image for event: " + event.name + ", URL: " + event.imageUrl);
            
            // Store the event ID to verify it's still the same event when image loads
            final String eventId = event.id;
            
            // Tag the ImageView with the event ID to track which event it should show
            // Use a simple string tag since we don't have a resource ID for this
            holder.imageEvent.setTag(eventId);
            
            ImageRequest imageRequest = new ImageRequest(
                event.imageUrl,
                new com.android.volley.Response.Listener<Bitmap>() {
                    @Override
                    public void onResponse(Bitmap response) {
                        if (response == null) {
                            Log.w(TAG, "Image response is null for: " + event.name);
                            return;
                        }
                        
                        Log.d(TAG, "Image loaded successfully for: " + event.name + ", size: " + response.getWidth() + "x" + response.getHeight());
                        
                        // Check if this ImageView is still showing the same event
                        String currentEventId = (String) holder.imageEvent.getTag();
                        if (eventId != null && eventId.equals(currentEventId)) {
                            holder.imageEvent.setImageBitmap(response);
                            Log.d(TAG, "Image set successfully for: " + event.name);
                        } else {
                            Log.d(TAG, "Skipping image set - event changed. Current: " + currentEventId + ", expected: " + eventId);
                        }
                    }
                },
                0, 0,
                ImageView.ScaleType.CENTER_CROP,
                null,
                new com.android.volley.Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(com.android.volley.VolleyError error) {
                        Log.e(TAG, "Error loading image for: " + event.name + ", URL: " + event.imageUrl);
                        Log.e(TAG, "Error message: " + (error.getMessage() != null ? error.getMessage() : "null"));
                        if (error.networkResponse != null) {
                            Log.e(TAG, "Network response status: " + error.networkResponse.statusCode);
                            try {
                                String errorBody = new String(error.networkResponse.data, "UTF-8");
                                Log.e(TAG, "Error response body (first 200 chars): " + (errorBody.length() > 200 ? errorBody.substring(0, 200) : errorBody));
                            } catch (Exception e) {
                                Log.e(TAG, "Error reading error response body", e);
                            }
                        } else {
                            Log.e(TAG, "Network response is null (likely network error or timeout)");
                        }
                        // Keep the placeholder on error - it's already set
                    }
                }
            );
            
            // Tag the request with the event ID to track it
            imageRequest.setTag("image_" + eventId);
            holder.imageRequest = imageRequest;
            
            Log.d(TAG, "Adding image request to queue for: " + event.name);
            imageRequestQueue.add(imageRequest);
        } else {
            Log.w(TAG, "Cannot load image - URL: " + (event.imageUrl != null ? event.imageUrl : "null") + ", Queue: " + (imageRequestQueue != null ? "not null" : "null"));
            // Use placeholder if no image URL
            holder.imageEvent.setImageResource(android.R.color.darker_gray);
        }
        
        // Favorite button click listener
        holder.buttonFavorite.setOnClickListener(v -> {
            // Consume the click event to prevent it from propagating
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            
            boolean newFavoriteState = !event.isFavorite;
            event.isFavorite = newFavoriteState;
            notifyItemChanged(position);
            if (favoriteClickListener != null) {
                favoriteClickListener.onFavoriteClick(event, newFavoriteState);
            }
        });
        
        // Prevent the favorite button from causing focus changes
        holder.buttonFavorite.setFocusable(false);
        holder.buttonFavorite.setFocusableInTouchMode(false);

        // Item click listener to navigate to event details
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(event);
            }
        });
    }
    
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Cancel image request when view is recycled
        if (holder.imageRequest != null) {
            holder.imageRequest.cancel();
            holder.imageRequest = null;
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    private int getCategoryIcon(String segment) {
        if (segment == null) return 0;
        
        // Map segment to icon resource
        // TODO: Add actual icon resources based on segment mapping from section 6.1
        // For now, return 0 (no icon) - icons need to be added to drawable folder
        switch (segment.toLowerCase()) {
            case "music":
                return android.R.drawable.ic_media_play; // Placeholder
            case "sports":
                return android.R.drawable.ic_menu_compass; // Placeholder
            case "arts & theatre":
            case "arts":
                return android.R.drawable.ic_menu_gallery; // Placeholder
            case "film":
                return android.R.drawable.ic_menu_camera; // Placeholder
            default:
                return 0;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageEvent;
        TextView textCategory;
        TextView textDateTime;
        TextView textEventName;
        TextView textVenue;
        ImageButton buttonFavorite;
        ImageRequest imageRequest; // Track the image request for cancellation

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageEvent = itemView.findViewById(R.id.imageEvent);
            textCategory = itemView.findViewById(R.id.textCategory);
            textDateTime = itemView.findViewById(R.id.textDateTime);
            textEventName = itemView.findViewById(R.id.textEventName);
            textVenue = itemView.findViewById(R.id.textVenue);
            buttonFavorite = itemView.findViewById(R.id.buttonFavorite);
        }
    }
}

