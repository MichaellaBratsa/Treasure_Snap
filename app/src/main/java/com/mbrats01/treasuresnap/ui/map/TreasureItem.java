package com.mbrats01.treasuresnap.ui.map;

import androidx.annotation.Nullable;

import com.google.maps.android.clustering.ClusterItem;
import com.google.android.gms.maps.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TreasureItem implements ClusterItem {
    private final LatLng position;
    private final String title;
    private final String snippet;
    private final String photoPath;
    private final long timestamp;

    public TreasureItem(MapFragment.Treasure treasure) {
        this.position = new LatLng(treasure.latitude, treasure.longitude);
        this.title = "Treasure 📸";
        this.snippet = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date(treasure.timestamp));
        this.photoPath = treasure.photoPath;
        this.timestamp = treasure.timestamp;
    }

    @Override
    public LatLng getPosition() { return position; }

    @Override
    public String getTitle() { return title; }

    @Override
    public String getSnippet() { return snippet; }

    @Nullable
    @Override
    public Float getZIndex() {
        return 0f;
    }

    public String getPhotoPath() { return photoPath; }

    public long getTimestamp() {
        return timestamp;
    }
}