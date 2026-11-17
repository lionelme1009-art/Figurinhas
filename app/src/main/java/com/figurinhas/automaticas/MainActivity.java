package com.figurinhas.automaticas;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 42;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ImageView preview;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(32, 60, 32, 24);
        root.setBackgroundColor(Color.rgb(20,20,22));
        TextView title = new TextView(this);
        title.setText("FIGURINHA AUTOMÁTICA"); title.setTextSize(23); title.setTextColor(Color.WHITE);
        root.addView(title);
        status = new TextView(this);
        status.setText("Selecione uma foto: sem cortes, com margens transparentes.");
        status.setTextColor(Color.LTGRAY); status.setPadding(0,24,0,24);
        root.addView(status);
        preview = new ImageView(this);
        preview.setBackgroundColor(Color.rgb(48,48,48));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Button choose = new Button(this); choose.setText("ESCOLHER IMAGEM");
        root.addView(choose);
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, PICK_IMAGE);
        });
        setContentView(root);
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            Uri shared = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (shared != null) process(shared);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) process(data.getData());
    }

    private void process(Uri uri) {
        status.setText("Convertendo...");
        worker.execute(() -> {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
                Bitmap original = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    android.util.Size size = info.getSize();
                    int max = Math.max(size.getWidth(), size.getHeight());
                    if (max > 2048) decoder.setTargetSampleSize(Math.max(1, (int)Math.ceil(max / 2048.0)));
                });
                Bitmap sticker = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(sticker);
                canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
                float scale = Math.min(512f / original.getWidth(), 512f / original.getHeight());
                float w = original.getWidth() * scale;
                float h = original.getHeight() * scale;
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                canvas.drawBitmap(original, null, new android.graphics.RectF((512-w)/2, (512-h)/2, (512+w)/2, (512+h)/2), paint);
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, "figurinha_" + System.currentTimeMillis() + ".webp");
                values.put(MediaStore.MediaColumns.MIME_TYPE, "image/webp");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/FigurinhasAutomaticas");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri output = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (output == null) throw new Exception("Não foi possível criar o arquivo.");
                try {
                    try (OutputStream stream = getContentResolver().openOutputStream(output)) {
                        if (stream == null || !sticker.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream)) throw new Exception("Falha ao salvar WebP.");
                    }
                    values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(output, values, null, null);
                } catch (Exception e) { getContentResolver().delete(output, null, null); throw e; }
                original.recycle();
                runOnUiThread(() -> {
                    preview.setImageBitmap(sticker);
                    status.setText("Pronta! Salva em Imagens/FigurinhasAutomaticas.");
                    Toast.makeText(this, "Figurinha salva em WebP!", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> status.setText("Erro: " + e.getMessage()));
            }
        });
    }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdown(); }
}
