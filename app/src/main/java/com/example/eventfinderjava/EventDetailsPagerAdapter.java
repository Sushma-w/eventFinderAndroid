package com.example.eventfinderjava;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class EventDetailsPagerAdapter extends FragmentStateAdapter {

    private final String eventId;
    private EventDetailsFragment.EventDetails eventDetails;

    public EventDetailsPagerAdapter(@NonNull Fragment fragment, String eventId) {
        super(fragment);
        this.eventId = eventId;
    }

    public void setEventDetails(EventDetailsFragment.EventDetails details) {
        this.eventDetails = details;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return EventDetailsTabFragment.newInstance(EventDetailsTabFragment.TAB_DETAILS, eventId);
            case 1:
                return EventDetailsTabFragment.newInstance(EventDetailsTabFragment.TAB_ARTIST, eventId);
            case 2:
                return EventDetailsTabFragment.newInstance(EventDetailsTabFragment.TAB_VENUE, eventId);
            default:
                return EventDetailsTabFragment.newInstance(EventDetailsTabFragment.TAB_DETAILS, eventId);
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    public EventDetailsFragment.EventDetails getEventDetails() {
        return eventDetails;
    }
}

