package com.example.dawaga;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.AtomicFile;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

final class ProfilePhoto {
    static final String REVISION = "profilePhotoRevision";
    static final Executor IO = Executors.newSingleThreadExecutor();
    private static final int MAX_DIMENSION = 512;

    static boolean show(ImageView view) {
        File image = file(view.getContext()).getBaseFile();
        Bitmap photo = image.isFile() ? BitmapFactory.decodeFile(image.getPath()) : null;
        if (photo == null) {
            view.setImageResource(R.drawable.ic_profile_avatar);
            return false;
        }
        view.setImageBitmap(photo);
        return true;
    }

    static void save(Context context, Uri uri) throws IOException {
        Bitmap bitmap = decode(context.getContentResolver(), uri);
        if (bitmap == null) throw new IOException("Could not decode profile photo");
        AtomicFile photo = file(context);
        FileOutputStream output = photo.startWrite();
        try {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                throw new IOException("Could not encode profile photo");
            }
            photo.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            photo.failWrite(output);
            throw error;
        } finally {
            bitmap.recycle();
        }
        context.getSharedPreferences("DawagaPrefs", Context.MODE_PRIVATE).edit()
                .putLong(REVISION, System.nanoTime()).apply();
    }

    private static Bitmap decode(ContentResolver resolver, Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri), (decoder, info, source) -> {
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                float scale = Math.min(1f, (float) MAX_DIMENSION / Math.max(width, height));
                decoder.setTargetSize(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(input, null, options);
        }
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while ((long) Math.max(options.outWidth, options.outHeight) > 2L * MAX_DIMENSION * options.inSampleSize) {
            options.inSampleSize *= 2;
        }
        try (InputStream input = resolver.openInputStream(uri)) {
            return BitmapFactory.decodeStream(input, null, options);
        }
    }

    private static AtomicFile file(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "profile-photo.jpg"));
    }
}
