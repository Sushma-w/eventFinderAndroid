package com.example.eventfinderjava;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import java.util.List;

public class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.ViewHolder> {

    private static final String TAG = "FavoritesAdapter";
    private final List<FavoriteEvent> favorites;
    private RequestQueue imageRequestQueue;
    private OnItemClickListener itemClickListener;

    public interface OnItemClickListener {
        void onItemClick(FavoriteEvent event);
    }

    public FavoritesAdapter(List<FavoriteEvent> favorites) {
        this.favorites = favorites;
    }

    public void setImageRequestQueue(RequestQueue queue) {
        this.imageRequestQueue = queue;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.favorite_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FavoriteEvent event = favorites.get(position);
        
        // Event name
        holder.textTitle.setText(event.name);
        holder.textTitle.setSelected(true); // Enable marquee
        
        // Date and time (already formatted in HomeFragment as "MMM d, yyyy, h:mm AM/PM")
        // Display date below the event name heading
        if (event.date != null && !event.date.isEmpty()) {
            holder.textDate.setText(event.date);
            holder.textDate.setVisibility(View.VISIBLE);
            Log.d(TAG, "Set date for " + event.name + ": " + event.date);
        } else {
            holder.textDate.setText("");
            holder.textDate.setVisibility(View.GONE);
            Log.w(TAG, "No date available for event: " + event.name);
        }
        
        // Time elapsed
        String timeElapsed = formatTimeElapsed(event.timestampAdded);
        holder.textTimeElapsed.setText(timeElapsed);
        
        // Load event image
        if (event.imageUrl != null && !event.imageUrl.isEmpty() && imageRequestQueue != null) {
            loadEventImage(holder.imageEvent, event.imageUrl, event.id);
        } else {
            holder.imageEvent.setImageBitmap(null);
        }
        
        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(event);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        // Cancel any pending image request
        if (holder.imageRequest != null) {
            holder.imageRequest.cancel();
            holder.imageRequest = null;
        }
        holder.imageEvent.setImageBitmap(null);
    }

    @Override
    public int getItemCount() {
        return favorites.size();
    }

    private void loadEventImage(ImageView imageView, String url, String eventId) {
        // Cancel any existing request for this view
        if (imageRequestQueue != null) {
            imageRequestQueue.cancelAll(eventId);
        }
        
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
                Log.e(TAG, "Error loading favorite event image", error);
            }
        );
        imageRequest.setTag(eventId);
        if (imageRequestQueue != null) {
            imageRequestQueue.add(imageRequest);
        }
    }

    private String formatTimeElapsed(String timestampAdded) {
        if (timestampAdded == null || timestampAdded.isEmpty()) {
            return "";
        }
        
        try {
            // Parse timestamp (could be ISO 8601 or epoch milliseconds)
            long addedTime;
            if (timestampAdded.contains("T") || timestampAdded.contains("-")) {
                // ISO 8601 format
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                addedTime = sdf.parse(timestampAdded).getTime();
            } else {
                // Try as epoch milliseconds
                addedTime = Long.parseLong(timestampAdded);
            }
            
            long currentTime = System.currentTimeMillis();
            long diff = currentTime - addedTime;
            
            long seconds = diff / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;
            
            if (days > 0) {
                return days + (days == 1 ? " day ago" : " days ago");
            } else if (hours > 0) {
                return hours + (hours == 1 ? " hour ago" : " hours ago");
            } else if (minutes > 0) {
                return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
            } else {
                return seconds + (seconds == 1 ? " second ago" : " seconds ago");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing timestamp: " + timestampAdded, e);
            return "";
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageEvent;
        TextView textTitle;
        TextView textDate;
        TextView textTimeElapsed;
        ImageRequest imageRequest;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageEvent = itemView.findViewById(R.id.imageEvent);
            textTitle = itemView.findViewById(R.id.textEventTitle);
            textDate = itemView.findViewById(R.id.textEventDate);
            textTimeElapsed = itemView.findViewById(R.id.textTimeElapsed);
        }
    }
}
