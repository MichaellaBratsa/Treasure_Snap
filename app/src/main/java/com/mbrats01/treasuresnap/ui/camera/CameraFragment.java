package com.mbrats01.treasuresnap.ui.camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.mbrats01.treasuresnap.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CameraFragment extends BottomSheetDialogFragment {
    private static final String TAG = "CameraFragment";

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private BarcodeScanner barcodeScanner;
    private FusedLocationProviderClient locationClient;
    private OnPhotoTakenListener photoListener;

    public interface OnPhotoTakenListener {
        void onPhotoTaken(String photoPath, double lat, double lng, long timestamp);
    }

    public void setOnPhotoTakenListener(OnPhotoTakenListener listener) {
        this.photoListener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Expand the BottomSheet to full screen
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );

            if (getDialog() instanceof BottomSheetDialog) {
                BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
                FrameLayout bottomSheet = dialog.findViewById(
                        com.google.android.material.R.id.design_bottom_sheet
                );

                if (bottomSheet != null) {
                    bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                    BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    behavior.setPeekHeight(getResources().getDisplayMetrics().heightPixels);
                }
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);

        previewView = view.findViewById(R.id.preview_view);
        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Configure barcode scanner for QR codes only
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        // Button listeners
        view.findViewById(R.id.button_take_photo).setOnClickListener(v -> capturePhoto());
        view.findViewById(R.id.button_scan_qr).setOnClickListener(v -> scanQR());
        view.findViewById(R.id.button_close).setOnClickListener(v -> dismiss());

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    1001
            );
        } else {
            startCamera();
        }

        return view;
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
    }


    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            ProcessCameraProvider provider = null;
            try {
                provider = cameraProviderFuture.get();
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            bindCamera(provider);

        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCamera(ProcessCameraProvider provider) {
        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder().build();

        provider.unbindAll();
        provider.bindToLifecycle((LifecycleOwner) this, selector, preview, imageCapture);
    }

    // Capture photo and save it locally with timestamp and location
    private void capturePhoto() {
        if (imageCapture == null) {
            Toast.makeText(requireContext(), "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        File photoFile = new File(requireContext().getFilesDir(), timestamp + ".jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        saveWithLocation(photoFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Toast.makeText(requireContext(), "Photo capture failed", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Capture error", e);
                    }
                });
    }

    private void saveWithLocation(File photo) {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Location permission needed", Toast.LENGTH_SHORT).show();
            return;
        }

        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null && photoListener != null) {
                photoListener.onPhotoTaken(
                        photo.getAbsolutePath(),
                        location.getLatitude(),
                        location.getLongitude(),
                        System.currentTimeMillis()
                );
                dismiss();
            } else {
                Toast.makeText(requireContext(), "Could not get location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scanQR() {
        if (imageCapture == null) {
            Toast.makeText(requireContext(), "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        File tempFile = new File(requireContext().getCacheDir(), "qr_scan.jpg");
        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(tempFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        processQRImage(tempFile.getAbsolutePath());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Toast.makeText(requireContext(), "QR scan failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Processes a saved image with ML Kit to detect QR codes
    private void processQRImage(String imagePath) {
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            InputImage image = InputImage.fromBitmap(bitmap, 0);

            barcodeScanner.process(image)
                    .addOnSuccessListener(barcodes -> {
                        if (barcodes.isEmpty()) {
                            Toast.makeText(requireContext(), "No QR code found", Toast.LENGTH_SHORT).show();
                        } else {
                            String qrValue = barcodes.get(0).getRawValue();
                            handleQRCode(qrValue);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(), "Failed to read QR code", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "QR processing error", e);
                    });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not load image", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Image decode error", e);
        }
    }

    private void handleQRCode(String value) {
        if (value != null && (value.startsWith("http://") || value.startsWith("https://"))) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(value));
            startActivity(intent);
            dismiss();
        } else {
            Toast.makeText(requireContext(), "QR: " + value, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraProviderFuture != null) {
            try {
                ProcessCameraProvider provider = cameraProviderFuture.get();
                provider.unbindAll();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera cleanup error", e);
            }
        }
    }
}