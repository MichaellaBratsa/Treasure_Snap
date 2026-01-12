package com.mbrats01.treasuresnap.ui.map;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.mbrats01.treasuresnap.R;
import com.mbrats01.treasuresnap.databinding.FragmentMapBinding;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapFragment extends Fragment implements OnMapReadyCallback {

    private FragmentMapBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap googleMap;
    private ClusterManager<TreasureItem> clusterManager;

    // Static list to persist treasures across fragment lifecycle
    private static ArrayList<Treasure> treasures = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMapBinding.inflate(inflater, container, false);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            moveToCurrentLocation();
        }

        // Initialize ClusterManager
        clusterManager = new ClusterManager<>(requireContext(), googleMap);

        // Use distance-based algorithm for better geographic clustering
        clusterManager.setAlgorithm(new NonHierarchicalDistanceBasedAlgorithm<>());

        // Apply custom renderer for visual customization
        clusterManager.setRenderer(new TreasureClusterRenderer(requireContext(), googleMap, clusterManager));

        // Set listeners
        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnMarkerClickListener(clusterManager);

        // Handle cluster clicks - show all treasures in cluster
        clusterManager.setOnClusterClickListener(cluster -> {
            List<Treasure> treasuresInCluster = new ArrayList<>();
            for (TreasureItem item : cluster.getItems()) {
                treasuresInCluster.add(new Treasure(
                        item.getPhotoPath(),
                        item.getPosition().latitude,
                        item.getPosition().longitude,
                        item.getTimestamp()
                ));
            }
            showMultipleTreasures(treasuresInCluster);
            return true;
        });

        // Handle individual marker clicks
        clusterManager.setOnClusterItemClickListener(item -> {
            List<Treasure> treasureList = new ArrayList<>();
            treasureList.add(new Treasure(
                    item.getPhotoPath(),
                    item.getPosition().latitude,
                    item.getPosition().longitude,
                    item.getTimestamp()
            ));
            showMultipleTreasures(treasureList);
            return true;
        });

        addTreasuresToCluster();
    }

    private void moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && googleMap != null) {
                LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(current, 15f));
            }
        });
    }

    private void addTreasuresToCluster() {
        if (clusterManager == null) return;

        // Clear existing items
        clusterManager.clearItems();

        // Add all treasures as cluster items
        for (Treasure treasure : treasures) {
            clusterManager.addItem(new TreasureItem(treasure));
        }

        // Trigger clustering algorithm
        clusterManager.cluster();
    }

    private void showMultipleTreasures(List<Treasure> treasureList) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(requireContext());
        View sheetView = getLayoutInflater().inflate(R.layout.treasure_details, null);

        TextView countText = sheetView.findViewById(R.id.treasure_count);
        LinearLayout container = sheetView.findViewById(R.id.treasures_container);

        // Check if we find only one treasure
        if (treasureList.size() == 1) {
            countText.setText("1 Treasure");
        } else {
            countText.setText(treasureList.size() + " Treasures");
            countText.setTextColor(0xFF9C27B0); // Purple color for clusters
        }

        // Display each treasure
        for (int i = 0; i < treasureList.size(); i++) {
            Treasure treasure = treasureList.get(i);
            View itemView = getLayoutInflater().inflate(R.layout.treasure_item, container, false);

            ImageView imageView = itemView.findViewById(R.id.treasure_image);
            TextView locationText = itemView.findViewById(R.id.treasure_location);
            TextView timestampText = itemView.findViewById(R.id.treasure_timestamp);

            // Load and display image with correct orientation
            Bitmap bitmap = getCorrectlyOrientedBitmap(treasure.photoPath);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }

            // Display location coordinates
            locationText.setText(String.format(Locale.getDefault(),
                    "📍 %.6f, %.6f", treasure.latitude, treasure.longitude));

            // Display capture timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
            timestampText.setText("📸 " + sdf.format(new Date(treasure.timestamp)));

            container.addView(itemView);
        }

        bottomSheet.setContentView(sheetView);
        bottomSheet.show();
    }

    // Change the orientation of image
    private Bitmap getCorrectlyOrientedBitmap(String photoPath) {
        Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
        if (bitmap == null) {
            return null;
        }

        try {
            ExifInterface exif = new ExifInterface(photoPath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return bitmap;
            }

            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    // Add the new treasure locally in list
    public static void addTreasure(String photoPath, double latitude, double longitude, String timestamp) {
        Treasure treasure = new Treasure(photoPath, latitude, longitude, timestamp);
        treasures.add(treasure);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh map when returning to this fragment
        if (clusterManager != null) {
            addTreasuresToCluster();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // Treasure object
    public static class Treasure {
        String photoPath;
        double latitude;
        double longitude;
        long timestamp;

        Treasure(String path, double lat, double lon, String time) {
            photoPath = path;
            latitude = lat;
            longitude = lon;
            timestamp = Long.parseLong(time);
        }

        Treasure(String path, double lat, double lon, long time) {
            photoPath = path;
            latitude = lat;
            longitude = lon;
            timestamp = time;
        }
    }

    private static class TreasureClusterRenderer extends DefaultClusterRenderer<TreasureItem> {

        private final Context context;

        public TreasureClusterRenderer(Context context, GoogleMap map, ClusterManager<TreasureItem> clusterManager) {
            super(context, map, clusterManager);
            this.context = context;
        }

        @Override
        protected void onBeforeClusterRendered(@NonNull Cluster<TreasureItem> cluster, @NonNull MarkerOptions markerOptions) {
            // Create custom icon showing the number of treasures
            BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(createClusterIcon(cluster.getSize()));
            markerOptions.icon(icon);
            markerOptions.title(cluster.getSize() + " Treasures");
        }

        @Override
        protected void onBeforeClusterItemRendered(@NonNull TreasureItem item, @NonNull MarkerOptions markerOptions) {
            // Customize individual treasure markers
            markerOptions.title(item.getTitle());
            markerOptions.snippet(item.getSnippet());

            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET));
        }

        @Override
        protected boolean shouldRenderAsCluster(@NonNull Cluster<TreasureItem> cluster) {
            // Cluster if 2 or more items are close together
            return cluster.getSize() >= 2;
        }

        // Creates a custom bitmap icon for clusters showing the count
        private Bitmap createClusterIcon(int clusterSize) {
            int size = 120;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // Draw circle background
            Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(0xFF9C27B0); // Purple color
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint);

            // Draw white border
            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(Color.WHITE);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(8);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4, borderPaint);

            // Draw count text
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(clusterSize < 100 ? 50 : 40); // Smaller text for large numbers
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);

            String text = String.valueOf(clusterSize);
            float textY = size / 2f - ((textPaint.descent() + textPaint.ascent()) / 2);
            canvas.drawText(text, size / 2f, textY, textPaint);

            return bitmap;
        }
    }
}