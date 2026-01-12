package com.mbrats01.treasuresnap.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.mbrats01.treasuresnap.R;
import com.mbrats01.treasuresnap.databinding.FragmentHomeBinding;
import com.mbrats01.treasuresnap.ui.camera.CameraFragment;
import com.mbrats01.treasuresnap.ui.map.MapFragment;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Permission launcher for location
        requestPermissionLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main)
                                .navigate(R.id.nav_map);
                    } else {
                        Toast.makeText(requireContext(),
                                "You have to allow location permissions to access the map",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        homeViewModel.getText().observe(getViewLifecycleOwner(), binding.textHome::setText);

        // Button to start CameraFragment
        binding.buttonStart.setOnClickListener(v -> {
            CameraFragment cameraFragment = new CameraFragment();

            // Set listener to receive photo data
            cameraFragment.setOnPhotoTakenListener((photoPath, latitude, longitude, timestamp) -> {
                // Add treasure to MapFragment's static list
                MapFragment.addTreasure(photoPath, latitude, longitude, String.valueOf(timestamp));

                Toast.makeText(requireContext(), "Treasure saved!", Toast.LENGTH_SHORT).show();
            });

            cameraFragment.show(getParentFragmentManager(), "CameraFragment");
        });

        // Button to open MapFragment
        binding.buttonMap.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                try {
                    Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main)
                            .navigate(R.id.nav_map);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(requireContext(), "Navigation error", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}